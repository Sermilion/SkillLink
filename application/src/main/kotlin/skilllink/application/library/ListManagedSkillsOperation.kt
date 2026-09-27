package skilllink.application.library

import skilllink.application.installation.RecoveryCoordinator
import skilllink.application.installation.RecoveryOutcome
import skilllink.application.installation.model.ListSkillsOutcome
import skilllink.application.installation.model.ManagedSkillSnapshot
import skilllink.application.ports.CatalogPort
import skilllink.application.ports.FilesystemPort
import skilllink.application.ports.LibraryLayoutPort
import skilllink.application.ports.WriterLockOutcome
import skilllink.application.ports.WriterLockPort

class ListManagedSkillsOperation(
    private val layout: LibraryLayoutPort,
    private val catalog: CatalogPort,
    private val filesystem: FilesystemPort,
    private val writerLock: WriterLockPort,
    private val recovery: RecoveryCoordinator,
) {
    fun execute(): ListSkillsOutcome =
        try {
            if (layout.isInitialized()) executeInitialized() else ListSkillsOutcome.EmptyLibrary
        } catch (_: Exception) {
            ListSkillsOutcome.Failed.StorageInaccessible
        }

    private fun executeInitialized(): ListSkillsOutcome =
        when (val lock = writerLock.tryAcquire()) {
            is WriterLockOutcome.Busy,
            is WriterLockOutcome.IoFailure,
            -> ListSkillsOutcome.Failed.StorageInaccessible

            is WriterLockOutcome.Acquired -> lock.handle.use { readRows() }
        }

    private fun readRows(): ListSkillsOutcome =
        try {
            if (recovery.recoverIncomplete() is RecoveryOutcome.Blocked) {
                ListSkillsOutcome.Failed.StorageInvalid
            } else {
                catalog
                    .listActiveOrdered()
                    .takeIf(List<*>::isNotEmpty)
                    ?.let { rows -> ListSkillsOutcome.Rows(rows.map(::refreshObserved)) }
                    ?: ListSkillsOutcome.EmptyLibrary
            }
        } catch (_: Exception) {
            ListSkillsOutcome.Failed.StorageInvalid
        }

    private fun refreshObserved(skill: ManagedSkillSnapshot): ManagedSkillSnapshot {
        val installations =
            skill.installations.map { installation ->
                val observed =
                    filesystem.observeLink(installation.destination, skill.canonicalPath)
                installation.copy(observed = observed)
            }
        return skill.copy(installations = installations)
    }
}
