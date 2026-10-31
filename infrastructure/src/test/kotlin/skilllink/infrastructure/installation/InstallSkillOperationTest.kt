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
import skilllink.domain.source.FrontmatterOutcome
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
        InstallSkillRequest.NewImport(
          source.resolve("SKILL.md"),
          setOf(AgentId.Claude),
          removeOriginal = true,
        ),
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

  @Test
  fun importsMultilineYamlWithoutChangingSourceOrCanonicalBytes() {
    assumeTrue(LinkCapabilityProbe.current() is skilllink.application.ports.LinkCapability.DirectSymlink)
    val source = createBundle("appfunctions", "reference")
    val skillFile = source.resolve("SKILL.md")
    val content =
      """
      ---
      name: appfunctions
      description: Exposes Android workflows
        to AI agents.
      metadata:
        author: Google LLC
        keywords:
        - AppFunctions
        - Kotlin
      ---
      Original instructions.
      """.trimIndent()
    Files.writeString(skillFile, content)
    val fixture = fixture()

    val outcome = fixture.operation.execute(InstallSkillRequest.NewImport(skillFile, setOf(AgentId.Claude)))

    assertTrue(outcome is InstallSkillOutcome.Completed)
    assertEquals(content, Files.readString(skillFile))
    val canonical =
      fixture.layout
        .resolve()
        .skillsRoot
        .resolve("appfunctions")
    assertEquals(content, Files.readString(canonical.resolve("SKILL.md")))
    assertEquals("reference", Files.readString(canonical.resolve("nested.txt")))
    val link =
      fixture.registry
        .destinationFor(AgentId.Claude)
        .root
        .resolve("appfunctions")
    assertEquals(canonical, Files.readSymbolicLink(link))
    assertEquals(listOf("appfunctions"), fixture.store.listActiveOrdered().map { it.displayName })
  }

  @Test
  fun preservesSpecificFrontmatterRejectionWithoutChangingTheSourceOrCatalog() {
    val source = createBundle("demo", "payload")
    val skillFile = source.resolve("SKILL.md")
    val content = "---\nname: demo\ndescription: one\ndescription: two\n---\n"
    Files.writeString(skillFile, content)
    val fixture = fixture()

    val outcome =
      fixture.operation.execute(
        InstallSkillRequest.NewImport(skillFile, setOf(AgentId.Claude), removeOriginal = true),
      )

    assertEquals(
      InstallSkillOutcome.Failed.InvalidFrontmatter(FrontmatterOutcome.Rejected.DuplicateDescription),
      outcome,
    )
    assertEquals(content, Files.readString(skillFile))
    assertTrue(fixture.store.listActiveOrdered().isEmpty())
    assertFalse(
      Files.exists(
        fixture.registry
          .destinationFor(AgentId.Claude)
          .root
          .resolve("demo"),
      ),
    )
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
    val filesystem = NativeFilesystemAdapter()
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
