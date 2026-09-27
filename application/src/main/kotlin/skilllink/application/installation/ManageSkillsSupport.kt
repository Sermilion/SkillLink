package skilllink.application.installation

import skilllink.application.installation.model.AgentInstallationSnapshot
import skilllink.application.installation.model.DesiredInstallationState
import skilllink.application.installation.model.DisableSkillOutcome
import skilllink.application.installation.model.EnableSkillOutcome
import skilllink.application.installation.model.ManagedSkillSnapshot
import skilllink.application.installation.model.ObservedLinkCondition
import skilllink.application.installation.model.RemoveSkillOutcome
import skilllink.application.installation.model.SkillManagementRequest
import skilllink.application.ports.CanonicalValidationOutcome
import skilllink.application.ports.CatalogPort
import skilllink.application.ports.LinkOutcome
import skilllink.application.ports.ManagementOperationStart
import skilllink.application.ports.MoveToTrashOutcome
import skilllink.application.ports.OperationKind
import skilllink.application.ports.OperationPaths
import skilllink.application.ports.OperationPhase
import skilllink.domain.agent.AgentId
import skilllink.domain.library.SkillNamePolicy

internal fun findManagedSkill(
  catalog: CatalogPort,
  request: SkillManagementRequest,
): ManagedSkillSnapshot? =
  SkillNamePolicy
    .comparisonKeyForLookup(request.comparisonKey.value)
    ?.let(catalog::findActiveByNameKey)
    ?.let(catalog::findActiveSnapshot)

internal fun <T> rollbackAfterFailure(
  recovery: RecoveryCoordinator,
  failure: T,
  blocked: T,
): T =
  if (recovery.recoverIncomplete() is RecoveryOutcome.Blocked) {
    blocked
  } else {
    failure
  }

internal fun ManageSkillsOperation.applyEnable(
  skill: ManagedSkillSnapshot,
  before: List<AgentInstallationSnapshot>,
  planned: List<AgentInstallationSnapshot>,
  changedAgents: Set<AgentId>,
): EnableSkillOutcome {
  val operationId =
    java.util.UUID
      .randomUUID()
      .toString()
  journal.beginManagement(
    ManagementOperationStart(
      operationId,
      OperationKind.Enable,
      skill.id,
      skill.comparisonKey.value,
      skill.canonicalPath,
      planned.associate { it.agent to it.destination },
      ManagementSnapshot.encode(before),
    ),
  )
  val updated = planned.toMutableList()
  val linkFailure =
    changedAgents
      .asSequence()
      .map { agent -> linkEnableAgent(skill, planned, updated, agent) }
      .firstOrNull { it != null }
  return if (linkFailure != null) {
    rollbackAfterFailure(this.recovery, linkFailure, EnableSkillOutcome.Failed.BlockedRecovery)
  } else {
    commitEnable(skill, operationId, updated, changedAgents)
  }
}

internal fun ManageSkillsOperation.disableLocked(request: SkillManagementRequest): DisableSkillOutcome =
  when (val plan = planDisable(request)) {
    DisablePlan.NotFound -> DisableSkillOutcome.Failed.NotFound
    DisablePlan.NoOp -> DisableSkillOutcome.NoOp
    DisablePlan.DestinationConflict -> DisableSkillOutcome.Failed.DestinationConflict
    is DisablePlan.Ready -> commitDisable(plan)
  }

internal fun ManageSkillsOperation.removeLocked(request: SkillManagementRequest): RemoveSkillOutcome =
  when (val preparation = prepareRemoval(request)) {
    RemovePreparation.NotFound -> RemoveSkillOutcome.Failed.NotFound
    RemovePreparation.IntegrityFailure -> RemoveSkillOutcome.Failed.IntegrityFailure
    RemovePreparation.DestinationConflict -> RemoveSkillOutcome.Failed.DestinationConflict
    is RemovePreparation.Ready -> executeRemoval(preparation.value)
  }

private fun ManageSkillsOperation.planDisable(request: SkillManagementRequest): DisablePlan =
  findManagedSkill(catalog, request)?.let { planDisableSkill(it, request) } ?: DisablePlan.NotFound

private fun ManageSkillsOperation.planDisableSkill(
  skill: ManagedSkillSnapshot,
  request: SkillManagementRequest,
): DisablePlan {
  val rememberedAgents = skill.installations.map { it.agent }.toSet()
  val targetAgents =
    request.explicitAgents
      .takeUnless { it.isEmpty() }
      ?.filter { it in rememberedAgents }
      ?.toSet()
      ?: rememberedAgents
  if (targetAgents.isEmpty()) {
    return DisablePlan.NoOp
  }
  val before = skill.installations
  val changedAgents = mutableSetOf<AgentId>()
  var failure = false
  val planned =
    skill.installations.map { installation ->
      if (installation.agent !in targetAgents) {
        installation
      } else if (installation.desired == DesiredInstallationState.Disabled &&
        installation.observed != ObservedLinkCondition.Linked
      ) {
        installation
      } else {
        val observed = filesystem.observeLink(installation.destination, skill.canonicalPath)
        if (observed == ObservedLinkCondition.Foreign || observed == ObservedLinkCondition.Unreadable) {
          failure = true
        }
        if (observed == ObservedLinkCondition.Linked) {
          changedAgents.add(installation.agent)
        }
        installation.copy(
          desired = DesiredInstallationState.Disabled,
          observed =
            if (observed == ObservedLinkCondition.Linked) {
              ObservedLinkCondition.Missing
            } else {
              observed
            },
        )
      }
    }
  val noOp =
    changedAgents.isEmpty() &&
      targetAgents.all { agent ->
        skill.installations.first { it.agent == agent }.desired == DesiredInstallationState.Disabled
      }
  return when {
    failure -> DisablePlan.DestinationConflict
    noOp -> DisablePlan.NoOp
    else -> DisablePlan.Ready(skill, before, planned, changedAgents)
  }
}

private fun ManageSkillsOperation.commitDisable(plan: DisablePlan.Ready): DisableSkillOutcome {
  val skill = plan.skill
  val operationId =
    java.util.UUID
      .randomUUID()
      .toString()
  journal.beginManagement(
    ManagementOperationStart(
      operationId,
      OperationKind.Disable,
      skill.id,
      skill.comparisonKey.value,
      skill.canonicalPath,
      plan.before.associate { it.agent to it.destination },
      ManagementSnapshot.encode(plan.before),
    ),
  )
  val unlinkFailure =
    plan.changedAgents.firstOrNull { agent ->
      val installation = skill.installations.first { it.agent == agent }
      !filesystem.removePathIfOwned(installation.destination, skill.canonicalPath, null)
    }
  return if (unlinkFailure != null) {
    rollbackAfterFailure(
      recovery,
      DisableSkillOutcome.Failed.DestinationConflict,
      DisableSkillOutcome.Failed.BlockedRecovery,
    )
  } else {
    try {
      catalog.commitManagementState(operationId, skill.id, plan.planned)
      journal.markCompleted(operationId)
      recovery.diagnosticsPort.record("disable", skill.id.value, operationId)
      DisableSkillOutcome.Completed(
        skillName = skill.displayName,
        changedAgents = plan.changedAgents,
        cleanupPending = false,
      )
    } catch (_: Exception) {
      rollbackAfterFailure(
        recovery,
        DisableSkillOutcome.Failed.IoFailure,
        DisableSkillOutcome.Failed.BlockedRecovery,
      )
    }
  }
}

private fun ManageSkillsOperation.linkEnableAgent(
  skill: ManagedSkillSnapshot,
  planned: List<AgentInstallationSnapshot>,
  updated: MutableList<AgentInstallationSnapshot>,
  agent: AgentId,
): EnableSkillOutcome.Failed? {
  val installation = planned.first { it.agent == agent }
  val failure =
    if (filesystem.observeLink(installation.destination, skill.canonicalPath) ==
      ObservedLinkCondition.Linked
    ) {
      null
    } else {
      when (filesystem.createOwnedLink(skill.canonicalPath, installation.destination, agent)) {
        is LinkOutcome.Linked -> {
          null
        }

        is LinkOutcome.Failed.CapabilityUnavailable -> {
          EnableSkillOutcome.Failed.PlatformCapability
        }

        is LinkOutcome.Failed.Occupied,
        is LinkOutcome.Failed.IoFailure,
        -> {
          EnableSkillOutcome.Failed.DestinationConflict
        }
      }
    }
  if (failure == null) {
    val index = updated.indexOfFirst { it.agent == agent }
    updated[index] = installation.copy(observed = ObservedLinkCondition.Linked)
  }
  return failure
}

private fun ManageSkillsOperation.commitEnable(
  skill: ManagedSkillSnapshot,
  operationId: String,
  updated: List<AgentInstallationSnapshot>,
  changedAgents: Set<AgentId>,
): EnableSkillOutcome =
  try {
    val merged = skill.installations.associateBy { it.agent }.toMutableMap()
    updated.forEach { installation -> merged[installation.agent] = installation }
    catalog.commitManagementState(operationId, skill.id, merged.values.toList())
    journal.markCompleted(operationId)
    recovery.diagnosticsPort.record("enable", skill.id.value, operationId)
    EnableSkillOutcome.Completed(
      skillName = skill.displayName,
      canonicalPath = skill.canonicalPath,
      changedAgents = changedAgents,
      cleanupPending = false,
    )
  } catch (_: Exception) {
    rollbackAfterFailure(
      recovery,
      EnableSkillOutcome.Failed.IoFailure,
      EnableSkillOutcome.Failed.BlockedRecovery,
    )
  }
