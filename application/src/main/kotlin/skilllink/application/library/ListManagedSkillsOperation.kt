package skilllink.application.library

import skilllink.application.installation.model.ListSkillsOutcome
import skilllink.application.installation.model.ManagedSkillSnapshot
import skilllink.application.ports.CatalogPort
import skilllink.application.ports.FilesystemPort
import skilllink.application.ports.LibraryLayoutPort
import skilllink.application.ports.WriterLockOutcome
import skilllink.application.ports.WriterLockPort
import skilllink.application.installation.RecoveryCoordinator
import skilllink.application.installation.RecoveryOutcome

class ListManagedSkillsOperation(
    private val layout: LibraryLayoutPort,
    private val catalog: CatalogPort,
    private val filesystem: FilesystemPort,
    private val writerLock: WriterLockPort,
    private val recovery: RecoveryCoordinator,
) {
    fun execute(): ListSkillsOutcome {
        val initialized =
            try {
                layout.isInitialized()
            } catch (_: Exception) {
                return ListSkillsOutcome.Failed.StorageInaccessible
            }
        if (!initialized) {
            return ListSkillsOutcome.EmptyLibrary
        }
        when (val lock = writerLock.tryAcquire()) {
            is WriterLockOutcome.Busy -> return ListSkillsOutcome.Failed.StorageInaccessible
            is WriterLockOutcome.IoFailure -> return ListSkillsOutcome.Failed.StorageInaccessible
            is WriterLockOutcome.Acquired ->
                lock.handle.use {
                    try {
                        if (recovery.recoverIncomplete() is RecoveryOutcome.Blocked) {
                            return ListSkillsOutcome.Failed.StorageInvalid
                        }
                        val rows = catalog.listActiveOrdered()
                        if (rows.isEmpty()) {
                            return ListSkillsOutcome.EmptyLibrary
                        }
                        val observed =
                            rows.map { skill ->
                                refreshObserved(skill)
                            }
                        return ListSkillsOutcome.Rows(observed)
                    } catch (_: Exception) {
                        return ListSkillsOutcome.Failed.StorageInvalid
                    }
                }
        }
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
