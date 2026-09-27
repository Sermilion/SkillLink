package skilllink.application.installation

import skilllink.application.installation.model.AgentInstallationSnapshot
import skilllink.application.installation.model.DesiredInstallationState
import skilllink.application.installation.model.ObservedLinkCondition
import skilllink.application.ports.CatalogPort
import skilllink.application.ports.DiagnosticsPort
import skilllink.application.ports.FilesystemPort
import skilllink.application.ports.LinkOutcome
import skilllink.application.ports.MoveToTrashOutcome
import skilllink.application.ports.OperationJournalPort
import skilllink.application.ports.OperationKind
import skilllink.application.ports.OperationPaths
import skilllink.application.ports.OperationPhase
import skilllink.application.ports.OperationRecord
import skilllink.application.ports.SafeRemovalOutcome
import java.nio.file.Path

sealed interface RecoveryOutcome {
    data object Clean : RecoveryOutcome

    data object Recovered : RecoveryOutcome

    data object Blocked : RecoveryOutcome
}

class RecoveryCoordinator(
    private val journal: OperationJournalPort,
    private val filesystem: FilesystemPort,
    private val catalog: CatalogPort,
    private val diagnostics: DiagnosticsPort,
) {
    internal val diagnosticsPort: DiagnosticsPort
        get() = diagnostics

    fun recoverIncomplete(): RecoveryOutcome {
        val incomplete = journal.findIncomplete()
        if (incomplete.isEmpty()) {
            return RecoveryOutcome.Clean
        }
        return if (incomplete.any { !recoverRecord(it) }) {
            RecoveryOutcome.Blocked
        } else {
            RecoveryOutcome.Recovered
        }
    }

    private fun recoverRecord(record: OperationRecord): Boolean =
        when (record.kind) {
            OperationKind.Install -> {
                if (record.phase == OperationPhase.Committed ||
                    record.phase == OperationPhase.CleanupPending
                ) {
                    finishCleanup(record)
                } else {
                    rollbackInstall(record)
                }
            }

            OperationKind.Enable,
            OperationKind.Disable,
            -> {
                if (record.phase == OperationPhase.Committed ||
                    record.phase == OperationPhase.CleanupPending
                ) {
                    finishCommittedManagement(record)
                } else {
                    rollbackManagement(record)
                }
            }

            OperationKind.Remove -> {
                when (record.phase) {
                    OperationPhase.Committed,
                    OperationPhase.CleanupPending,
                    -> finishCommittedRemove(record)

                    OperationPhase.Published -> rollbackRemovePublished(record)

                    else -> rollbackManagement(record)
                }
            }
        }

    private fun rollbackInstall(record: OperationRecord): Boolean {
        var cleaned = true
        record.stagingPath?.let {
            cleaned =
                filesystem.removePathIfOwned(it, it, record.stagingIdentity) &&
                cleaned
        }
        record.canonicalPath?.let {
            cleaned =
                filesystem.removePathIfOwned(it, it, record.canonicalIdentity) &&
                cleaned
        }
        for (destination in record.agentDestinations.values) {
            cleaned =
                filesystem.removePathIfOwned(destination, record.canonicalPath ?: destination, null) &&
                cleaned
        }
        if (!cleaned) {
            diagnostics.reportSecondaryFailure("recovery_rollback", "rollback_blocked", record.operationId)
            return false
        }
        catalog.clearReservation(record.skillId)
        journal.markRolledBack(record.operationId)
        diagnostics.record("recovery_rollback", record.skillId.value, record.operationId)
        return true
    }

    private fun finishCleanup(record: OperationRecord): Boolean {
        val stagingClean =
            record.stagingPath?.let {
                filesystem.removePathIfOwned(it, it, record.stagingIdentity)
            } ?: true
        val source = record.sourceRoot
        val removal =
            record.sourceFingerprint?.let { fingerprint ->
                source?.let { filesystem.safeRemoveOriginal(it, fingerprint) }
            }
        val sourceClean = removal != null && removal !is SafeRemovalOutcome.Blocked
        return if (!sourceClean || !stagingClean) {
            if (!stagingClean) {
                diagnostics.reportSecondaryFailure(
                    "recovery_cleanup",
                    "staging_cleanup_blocked",
                    record.operationId,
                )
            }
            journal.updatePhase(
                record.operationId,
                OperationPhase.CleanupPending,
                OperationPaths(
                    stagingPath = record.stagingPath,
                    stagingIdentity = record.stagingIdentity,
                ),
            )
            false
        } else {
            journal.markCompleted(record.operationId)
            true
        }
    }

    private fun rollbackManagement(record: OperationRecord): Boolean {
        val previous = record.managementSnapshot?.let(ManagementSnapshot::decode)
        val canonical = record.canonicalPath
        if (previous == null || canonical == null) {
            return false
        }
        val cleaned =
            restorePreviousLinks(previous, canonical) &&
                removeNewLinks(record, previous, canonical)
        return if (!cleaned) {
            diagnostics.reportSecondaryFailure("recovery_rollback", "management_blocked", record.operationId)
            false
        } else {
            journal.markRolledBack(record.operationId)
            diagnostics.record("recovery_management_rollback", record.skillId.value, record.operationId)
            true
        }
    }

    private fun restorePreviousLinks(
        previous: List<AgentInstallationSnapshot>,
        canonical: Path,
    ): Boolean {
        var cleaned = true
        for (installation in previous) {
            val observed = filesystem.observeLink(installation.destination, canonical)
            when {
                installation.desired == DesiredInstallationState.Enabled &&
                    installation.observed == ObservedLinkCondition.Linked &&
                    observed != ObservedLinkCondition.Linked -> {
                    when (
                        filesystem.createOwnedLink(canonical, installation.destination, installation.agent)
                    ) {
                        is LinkOutcome.Linked -> Unit
                        else -> cleaned = false
                    }
                }

                installation.desired == DesiredInstallationState.Disabled ||
                    installation.observed == ObservedLinkCondition.Missing -> {
                    if (observed == ObservedLinkCondition.Linked) {
                        cleaned = filesystem.removePathIfOwned(installation.destination, canonical, null) && cleaned
                    }
                }
            }
        }
        return cleaned
    }

    private fun removeNewLinks(
        record: OperationRecord,
        previous: List<AgentInstallationSnapshot>,
        canonical: Path,
    ): Boolean {
        var cleaned = true
        val previousAgents = previous.map { it.agent }.toSet()
        for ((agent, destination) in record.agentDestinations) {
            if (agent !in previousAgents &&
                filesystem.observeLink(destination, canonical) == ObservedLinkCondition.Linked
            ) {
                cleaned = filesystem.removePathIfOwned(destination, canonical, null) && cleaned
            }
        }
        return cleaned
    }

    private fun finishCommittedManagement(record: OperationRecord): Boolean {
        journal.markCompleted(record.operationId)
        return true
    }

    private fun finishCommittedRemove(record: OperationRecord): Boolean =
        record.trashPath?.let(filesystem::pathIdentity)?.let {
            journal.markCompleted(record.operationId)
            true
        } ?: false

    private fun rollbackRemovePublished(record: OperationRecord): Boolean {
        val canonical = record.canonicalPath ?: return false
        val trash = record.trashPath
        val restored =
            trash == null ||
                filesystem.pathIdentity(trash) == null ||
                filesystem.moveCanonicalToTrash(trash, canonical) is MoveToTrashOutcome.Moved
        return if (restored) {
            rollbackManagement(record)
        } else {
            diagnostics.reportSecondaryFailure("recovery_rollback", "remove_trash_blocked", record.operationId)
            false
        }
    }
}
