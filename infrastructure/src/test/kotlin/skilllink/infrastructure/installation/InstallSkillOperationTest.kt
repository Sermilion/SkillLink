package skilllink.infrastructure.installation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import skilllink.application.installation.InstallSkillOperation
import skilllink.application.installation.MutationGate
import skilllink.application.installation.RecoveryCoordinator
import skilllink.application.installation.model.InstallSkillOutcome
import skilllink.application.installation.model.InstallSkillRequest
import skilllink.application.library.ListManagedSkillsOperation
import skilllink.domain.agent.AgentId
import skilllink.infrastructure.agent.DefaultAgentRegistry
import skilllink.infrastructure.agent.LinkCapabilityProbe
import skilllink.infrastructure.diagnostics.FileDiagnostics
import skilllink.infrastructure.filesystem.NativeFilesystemAdapter
import skilllink.infrastructure.layout.HomeLibraryLayout
import skilllink.infrastructure.lock.FileWriterLock
import skilllink.infrastructure.persistence.SqliteCatalogStore
import java.nio.file.Files
import java.nio.file.Path

class InstallSkillOperationTest {
  @TempDir
  lateinit var temp: Path

  @Test
  fun commitsVerifiedBundleAndOnlyCatalogsTheExplicitImport() {
    assumeTrue(LinkCapabilityProbe.current() is skilllink.application.ports.LinkCapability.DirectSymlink)
    val source = createBundle("demo-skill", "payload")
    val fixture = fixture()

    val outcome =
      fixture.operation.execute(
        InstallSkillRequest.NewImport(source.resolve("SKILL.md"), setOf(AgentId.Claude)),
      )

    assertTrue(outcome is InstallSkillOutcome.Completed)
    val completed = outcome as InstallSkillOutcome.Completed
    assertEquals("demo-skill", completed.skillName)
    assertFalse(Files.exists(source))
    assertEquals(
      "payload",
      Files.readString(
        fixture.layout
          .resolve()
          .skillsRoot
          .resolve("demo-skill/nested.txt"),
      ),
    )
    val destination =
      fixture.registry
        .destinationFor(AgentId.Claude)
        .root
        .resolve("demo-skill")
    assertTrue(Files.isSymbolicLink(destination))
    assertEquals(
      fixture.layout
        .resolve()
        .skillsRoot
        .resolve("demo-skill"),
      Files.readSymbolicLink(destination),
    )
    assertEquals(listOf("demo-skill"), fixture.store.listActiveOrdered().map { it.displayName })
    val listed = fixture.listOperation.execute()
    assertTrue(listed is skilllink.application.installation.model.ListSkillsOutcome.Rows)
    assertEquals(
      skilllink.application.installation.model.ObservedLinkCondition.Linked,
      (listed as skilllink.application.installation.model.ListSkillsOutcome.Rows)
        .skills
        .single()
        .installations
        .single()
        .observed,
    )
  }

  @Test
  fun rejectsNameConflictBeforeRemovingTheSecondOriginal() {
    assumeTrue(LinkCapabilityProbe.current() is skilllink.application.ports.LinkCapability.DirectSymlink)
    val first = createBundle("demo-skill", "first")
    val second = createBundle("demo-skill", "second")
    val fixture = fixture()
    val request = { root: Path -> InstallSkillRequest.NewImport(root.resolve("SKILL.md"), setOf(AgentId.Cursor)) }

    assertTrue(fixture.operation.execute(request(first)) is InstallSkillOutcome.Completed)
    val outcome = fixture.operation.execute(request(second))

    assertEquals(InstallSkillOutcome.Failed.NameConflict, outcome)
    assertTrue(Files.exists(second))
    assertEquals(1, fixture.store.listActiveOrdered().size)
  }

  @Test
  fun rejectsOccupiedDestinationBeforeChangingOriginal() {
    assumeTrue(LinkCapabilityProbe.current() is skilllink.application.ports.LinkCapability.DirectSymlink)
    val source = createBundle("occupied", "payload")
    val fixture = fixture()
    val destination =
      fixture.registry
        .destinationFor(AgentId.Junie)
        .root
        .resolve("occupied")
    Files.createDirectories(destination)
    Files.writeString(destination.resolve("foreign.txt"), "foreign")

    val outcome =
      fixture.operation.execute(
        InstallSkillRequest.NewImport(source.resolve("SKILL.md"), setOf(AgentId.Junie)),
      )

    assertEquals(InstallSkillOutcome.Failed.DestinationConflict, outcome)
    assertTrue(Files.exists(source))
    assertTrue(Files.exists(destination.resolve("foreign.txt")))
  }

  private fun createBundle(
    name: String,
    payload: String,
  ): Path {
    val root = Files.createTempDirectory(temp, "attempt").resolve(name)
    Files.createDirectories(root)
    Files.writeString(root.resolve("SKILL.md"), "---\nname: $name\ndescription: demo\n---\n")
    Files.writeString(root.resolve("nested.txt"), payload)
    return root
  }

  private fun fixture(): Fixture {
    val home = temp.resolve("home")
    val layout = HomeLibraryLayout(home)
    val registry = DefaultAgentRegistry(home)
    val store = SqliteCatalogStore(layout.resolve().databasePath)
    val filesystem = NativeFilesystemAdapter(layout, registry)
    val diagnostics = FileDiagnostics(layout.resolve().diagnosticsRoot)
    val recovery = RecoveryCoordinator(store, filesystem, store, diagnostics)
    return Fixture(
      layout,
      registry,
      store,
      InstallSkillOperation(
        layout,
        store,
        filesystem,
        registry,
        store,
        MutationGate(FileWriterLock(layout.resolve().lockPath), recovery),
      ),
      ListManagedSkillsOperation(layout, store, filesystem, FileWriterLock(layout.resolve().lockPath), recovery),
    )
  }
}

private data class Fixture(
  val layout: HomeLibraryLayout,
  val registry: DefaultAgentRegistry,
  val store: SqliteCatalogStore,
  val operation: InstallSkillOperation,
  val listOperation: ListManagedSkillsOperation,
)
