package skilllink.application.installation

import skilllink.application.installation.model.AgentInstallationSnapshot
import skilllink.application.installation.model.ManagedSkillSnapshot
import skilllink.application.installation.model.ObservedLinkCondition
import skilllink.application.installation.model.RemoveSkillOutcome
import skilllink.application.installation.model.SkillManagementRequest
import skilllink.application.ports.CanonicalValidationOutcome
import skilllink.application.ports.ManagementOperationStart
import skilllink.application.ports.MoveToTrashOutcome
import skilllink.application.ports.OperationKind
import skilllink.application.ports.OperationPaths
import skilllink.application.ports.OperationPhase
import skilllink.domain.agent.AgentId
import java.nio.file.Path

internal fun ManageSkillsOperation.prepareRemoval(request: SkillManagementRequest): RemovePreparation {
    val skill = findManagedSkill(catalog, request)
    return when {
        skill == null -> {
            RemovePreparation.NotFound
        }

        filesystem.validateCanonicalBundle(skill.canonicalPath) !is CanonicalValidationOutcome.Valid -> {
            RemovePreparation.IntegrityFailure
        }

        else -> {
            prepareKnownSkill(skill)
        }
    }
}

private fun ManageSkillsOperation.prepareKnownSkill(skill: ManagedSkillSnapshot): RemovePreparation {
    val before = skill.installations
    val conflict =
        before.any { installation ->
            filesystem.observeLink(installation.destination, skill.canonicalPath).isForeignOrUnreadable()
        }
    return if (conflict) {
        RemovePreparation.DestinationConflict
    } else {
        RemovePreparation.Ready(
            RemovalState(
                skill = skill,
                before = before,
                formerAgents = before.map { it.agent }.toSet(),
                operationId =
                    java.util.UUID
                        .randomUUID()
                        .toString(),
                trashPath =
                    layout.resolve().trashRoot.resolve(
                        java.util.UUID
                            .randomUUID()
                            .toString(),
                    ),
            ),
        )
    }
}

internal fun ManageSkillsOperation.executeRemoval(state: RemovalState): RemoveSkillOutcome {
    journal.beginManagement(
        ManagementOperationStart(
            state.operationId,
            OperationKind.Remove,
            state.skill.id,
            state.skill.comparisonKey.value,
            state.skill.canonicalPath,
            state.before.associate { it.agent to it.destination },
            ManagementSnapshot.encode(state.before),
        ),
    )
    return if (removeOwnedLinks(state)) {
        moveAndCommitRemoval(state)
    } else {
        rollbackAfterFailure(
            recovery,
            RemoveSkillOutcome.Failed.DestinationConflict,
            RemoveSkillOutcome.Failed.BlockedRecovery,
        )
    }
}

private fun ManageSkillsOperation.removeOwnedLinks(state: RemovalState): Boolean =
    state.before.firstOrNull { installation ->
        filesystem.observeLink(installation.destination, state.skill.canonicalPath) ==
            ObservedLinkCondition.Linked &&
            !filesystem.removePathIfOwned(installation.destination, state.skill.canonicalPath, null)
    } == null

private fun ManageSkillsOperation.moveAndCommitRemoval(state: RemovalState): RemoveSkillOutcome {
    journal.updatePhase(
        state.operationId,
        OperationPhase.Published,
        OperationPaths(canonicalPath = state.skill.canonicalPath, trashPath = state.trashPath),
    )
    return when (val moved = filesystem.moveCanonicalToTrash(state.skill.canonicalPath, state.trashPath)) {
        is MoveToTrashOutcome.Moved -> {
            commitRemoval(state, moved.trashPath)
        }

        is MoveToTrashOutcome.Failed -> {
            rollbackAfterFailure(
                recovery,
                RemoveSkillOutcome.Failed.IoFailure,
                RemoveSkillOutcome.Failed.BlockedRecovery,
            )
        }
    }
}

private fun ManageSkillsOperation.commitRemoval(
    state: RemovalState,
    trashPath: java.nio.file.Path,
): RemoveSkillOutcome =
    try {
        catalog.commitRemoval(state.operationId, state.skill.id, trashPath, state.formerAgents)
        journal.markCompleted(state.operationId)
        recovery.diagnosticsPort.record("remove", state.skill.id.value, state.operationId)
        RemoveSkillOutcome.Completed(
            skillName = state.skill.displayName,
            trashPath = trashPath,
            formerAgents = state.formerAgents,
            cleanupPending = false,
        )
    } catch (_: Exception) {
        rollbackAfterFailure(
            recovery,
            RemoveSkillOutcome.Failed.IoFailure,
            RemoveSkillOutcome.Failed.BlockedRecovery,
        )
    }

private fun ObservedLinkCondition.isForeignOrUnreadable(): Boolean =
    this == ObservedLinkCondition.Foreign || this == ObservedLinkCondition.Unreadable

internal sealed interface RemovePreparation {
    data object NotFound : RemovePreparation

    data object IntegrityFailure : RemovePreparation

    data object DestinationConflict : RemovePreparation

    data class Ready(
        val value: RemovalState,
    ) : RemovePreparation
}

internal data class RemovalState(
    val skill: ManagedSkillSnapshot,
    val before: List<AgentInstallationSnapshot>,
    val formerAgents: Set<AgentId>,
    val operationId: String,
    val trashPath: Path,
)
