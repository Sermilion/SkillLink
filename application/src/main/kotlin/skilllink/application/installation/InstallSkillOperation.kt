package skilllink.application.installation

import skilllink.application.installation.model.InstallSkillOutcome
import skilllink.application.installation.model.InstallSkillRequest
import skilllink.application.ports.AgentRegistryPort
import skilllink.application.ports.CatalogPort
import skilllink.application.ports.FilesystemPort
import skilllink.application.ports.InstallOperationStart
import skilllink.application.ports.LibraryLayoutPort
import skilllink.application.ports.OperationJournalPort
import skilllink.application.ports.WriterLockOutcome

class InstallSkillOperation(
    internal val layout: LibraryLayoutPort,
    internal val catalog: CatalogPort,
    internal val filesystem: FilesystemPort,
    internal val agents: AgentRegistryPort,
    internal val journal: OperationJournalPort,
    internal val gate: MutationGate,
) {
    internal val recovery: RecoveryCoordinator
        get() = gate.recovery

    fun execute(request: InstallSkillRequest): InstallSkillOutcome =
        (request as? InstallSkillRequest.NewImport)
            ?.takeUnless { it.agents.isEmpty() }
            ?.let(::executeImport)
            ?: InstallSkillOutcome.Failed.InvalidArguments

    private fun executeImport(import: InstallSkillRequest.NewImport): InstallSkillOutcome =
        when (val lock = gate.tryAcquire()) {
            is WriterLockOutcome.Busy -> {
                InstallSkillOutcome.Failed.WriterBusy
            }

            is WriterLockOutcome.IoFailure -> {
                InstallSkillOutcome.Failed.IoFailure
            }

            is WriterLockOutcome.Acquired -> {
                lock.handle.use {
                    if (recovery.recoverIncomplete() is RecoveryOutcome.Blocked) {
                        InstallSkillOutcome.Failed.BlockedRecovery
                    } else {
                        runInstall(import)
                    }
                }
            }
        }

    private fun runInstall(import: InstallSkillRequest.NewImport): InstallSkillOutcome = runInstallAttempt(this, import)
}
