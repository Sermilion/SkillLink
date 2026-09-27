package skilllink.app

import skilllink.application.installation.InstallSkillOperation
import skilllink.application.installation.ManageSkillsOperation
import skilllink.application.installation.MutationGate
import skilllink.application.installation.RecoveryCoordinator
import skilllink.application.library.ListManagedSkillsOperation
import skilllink.cli.SkillLinkCli
import skilllink.infrastructure.agent.DefaultAgentRegistry
import skilllink.infrastructure.diagnostics.FileDiagnostics
import skilllink.infrastructure.filesystem.NativeFilesystemAdapter
import skilllink.infrastructure.layout.HomeLibraryLayout
import skilllink.infrastructure.lock.FileWriterLock
import skilllink.infrastructure.persistence.SqliteCatalogStore
import java.nio.file.Path

data class SkillLinkRuntime(
  val cli: SkillLinkCli,
)

object SkillLinkRuntimeFactory {
  fun create(homeOverride: Path? = null): SkillLinkRuntime {
    val home = homeOverride ?: Path.of(System.getProperty("user.home"))
    val layout = HomeLibraryLayout(homeOverride)
    val services =
      lazy {
        val paths = layout.resolve()
        val store = SqliteCatalogStore(paths.databasePath)
        val agents = DefaultAgentRegistry(home)
        val filesystem = NativeFilesystemAdapter(layout, agents)
        val diagnostics = FileDiagnostics(paths.diagnosticsRoot)
        val recovery = RecoveryCoordinator(store, filesystem, store, diagnostics)
        val writerLock = FileWriterLock(paths.lockPath)
        val gate = MutationGate(writerLock, recovery)
        val install =
          InstallSkillOperation(
            layout,
            store,
            filesystem,
            agents,
            store,
            gate,
          )
        val list =
          ListManagedSkillsOperation(
            layout,
            store,
            filesystem,
            writerLock,
            recovery,
          )
        val manage =
          ManageSkillsOperation(
            layout,
            store,
            filesystem,
            agents,
            store,
            gate,
          )
        Triple(install, list, manage)
      }
    val cli =
      SkillLinkCli(
        installOperationProvider = { services.value.first },
        listOperationProvider = { services.value.second },
        manageOperationProvider = { services.value.third },
      )
    return SkillLinkRuntime(cli)
  }
}
