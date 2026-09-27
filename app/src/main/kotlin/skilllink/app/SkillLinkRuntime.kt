package skilllink.app

import skilllink.application.installation.InstallSkillOperation
import skilllink.application.installation.ManageSkillsOperation
import skilllink.application.installation.MutationGate
import skilllink.application.installation.RecoveryCoordinator
import skilllink.application.library.ListManagedSkillsOperation
import skilllink.application.library.OpenManagedSkillOperation
import skilllink.cli.SkillLinkCli
import skilllink.infrastructure.agent.DefaultAgentRegistry
import skilllink.infrastructure.diagnostics.FileDiagnostics
import skilllink.infrastructure.filesystem.DefaultFileOpenerAdapter
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
        val filesystem = NativeFilesystemAdapter()
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
        SkillLinkServices(
          install = install,
          list = list,
          manage = manage,
          open = OpenManagedSkillOperation(layout, store, filesystem, DefaultFileOpenerAdapter(), writerLock),
        )
      }
    val cli =
      SkillLinkCli(
        installOperationProvider = { services.value.install },
        listOperationProvider = { services.value.list },
        manageOperationProvider = { services.value.manage },
        openOperationProvider = { services.value.open },
      )
    return SkillLinkRuntime(cli)
  }
}

private data class SkillLinkServices(
  val install: InstallSkillOperation,
  val list: ListManagedSkillsOperation,
  val manage: ManageSkillsOperation,
  val open: OpenManagedSkillOperation,
)
