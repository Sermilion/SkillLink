package skilllink.application.installation

import skilllink.application.ports.CatalogPort
import skilllink.application.ports.DiagnosticsPort
import skilllink.application.ports.FilesystemPort
import skilllink.application.ports.OperationJournalPort
import skilllink.application.ports.OperationPaths
import skilllink.application.ports.OperationPhase
import skilllink.application.ports.OperationRecord
import skilllink.application.ports.SafeRemovalOutcome

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
    fun recoverIncomplete(): RecoveryOutcome {
        val incomplete = journal.findIncomplete()
        if (incomplete.isEmpty()) {
            return RecoveryOutcome.Clean
        }
        var blocked = false
        for (record in incomplete) {
            val recovered =
                when (record.phase) {
                OperationPhase.Committed,
                OperationPhase.CleanupPending,
                -> finishCleanup(record)
                OperationPhase.Staging,
                OperationPhase.Published,
                OperationPhase.Linked,
                -> rollback(record)
                OperationPhase.Completed,
                OperationPhase.RolledBack,
                -> true
            }
            blocked = blocked || !recovered
        }
        return if (blocked) RecoveryOutcome.Blocked else RecoveryOutcome.Recovered
    }

    private fun rollback(record: OperationRecord): Boolean {
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
        if (source == null) {
            return false
        }
        val fingerprint = record.sourceFingerprint ?: return false
        val removal = filesystem.safeRemoveOriginal(source, fingerprint)
        val sourceClean = removal !is SafeRemovalOutcome.Blocked
        if (!sourceClean || !stagingClean) {
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
            return false
        }
        journal.markCompleted(record.operationId)
        return true
    }
}
