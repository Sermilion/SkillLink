package skilllink.infrastructure.installation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import skilllink.application.installation.RecoveryCoordinator
import skilllink.application.installation.RecoveryOutcome
import skilllink.application.ports.DiagnosticsPort
import skilllink.application.ports.OperationKind
import skilllink.application.ports.OperationPaths
import skilllink.application.ports.OperationPhase
import skilllink.domain.agent.AgentId
import skilllink.domain.library.NameComparisonKey
import skilllink.domain.library.SkillId
import skilllink.infrastructure.agent.DefaultAgentRegistry
import skilllink.infrastructure.filesystem.NativeFilesystemAdapter
import skilllink.infrastructure.filesystem.sourceFingerprint
import skilllink.infrastructure.layout.HomeLibraryLayout
import skilllink.infrastructure.persistence.InstallTestInput
import skilllink.infrastructure.persistence.ManagementTestInput
import skilllink.infrastructure.persistence.SqliteCatalogStore
import skilllink.infrastructure.persistence.beginInstall
import skilllink.infrastructure.persistence.beginManagement
import java.nio.file.Files
import java.nio.file.Path

class RecoveryCoordinatorTest {
  @TempDir
  lateinit var temp: Path

  @Test
  fun rollsBackUncommittedArtifactsAndReservation() {
    val home = temp.resolve("home")
    val layout = HomeLibraryLayout(home)
    val registry = DefaultAgentRegistry(home)
    val filesystem = NativeFilesystemAdapter()
    val store = SqliteCatalogStore(layout.resolve().databasePath)
    val skillId = SkillId("uncommitted")
    val source = temp.resolve("source/demo")
    val staging = layout.resolve().root.resolve("staging/op")
    val canonical = layout.resolve().skillsRoot.resolve("demo")
    val destination = registry.destinationFor(AgentId.Claude).root.resolve("demo")
    Files.createDirectories(source)
    Files.createDirectories(staging)
    Files.createDirectories(canonical)
    Files.createDirectories(destination.parent)
    createSymlinkOrSkip(destination, canonical)
    store.beginInstall(
      "op",
      skillId,
      "demo",
      InstallTestInput(source, "fingerprint", mapOf(AgentId.Claude to destination)),
    )
    store.reserveSkill(skillId, "demo", NameComparisonKey("demo"), canonical, mapOf(AgentId.Claude to destination))
    store.updatePhase(
      "op",
      OperationPhase.Linked,
      OperationPaths(
        stagingPath = staging,
        canonicalPath = canonical,
        stagingIdentity = filesystem.pathIdentity(staging),
        canonicalIdentity = filesystem.pathIdentity(canonical),
      ),
    )
    val unrelatedId = SkillId("unrelated")
    val unrelatedSource = temp.resolve("source/unrelated")
    val unrelatedCanonical = layout.resolve().skillsRoot.resolve("unrelated")
    Files.createDirectories(unrelatedSource)
    val unrelatedFingerprint = filesystem.sourceFingerprint(unrelatedSource)
    store.beginInstall(
      "unrelated-op",
      unrelatedId,
      "unrelated",
      InstallTestInput(unrelatedSource, unrelatedFingerprint, emptyMap()),
    )
    store.reserveSkill(unrelatedId, "unrelated", NameComparisonKey("unrelated"), unrelatedCanonical, emptyMap())
    store.commitOperation("unrelated-op")

    val outcome = recovery(store, filesystem).recoverIncomplete()

    assertTrue(outcome is RecoveryOutcome.Recovered)
    assertFalse(Files.exists(staging))
    assertFalse(Files.exists(canonical))
    assertFalse(Files.exists(destination))
    assertTrue(store.findActiveByNameKey(NameComparisonKey("demo")) == null)
    assertTrue(store.findActiveByNameKey(NameComparisonKey("unrelated")) == unrelatedId)
  }

  @Test
  fun finishesCommittedCleanupWithoutRollingBackActiveCatalog() {
    val home = temp.resolve("home")
    val layout = HomeLibraryLayout(home)
    val registry = DefaultAgentRegistry(home)
    val filesystem = NativeFilesystemAdapter()
    val store = SqliteCatalogStore(layout.resolve().databasePath)
    val skillId = SkillId("committed")
    val source = temp.resolve("source/demo")
    val canonical = layout.resolve().skillsRoot.resolve("demo")
    val destination = registry.destinationFor(AgentId.Cursor).root.resolve("demo")
    Files.createDirectories(source)
    Files.writeString(source.resolve("SKILL.md"), "source")
    val fingerprint = filesystem.sourceFingerprint(source)
    store.beginInstall(
      "op",
      skillId,
      "demo",
      InstallTestInput(source, fingerprint, mapOf(AgentId.Cursor to destination)),
    )
    store.reserveSkill(skillId, "demo", NameComparisonKey("demo"), canonical, mapOf(AgentId.Cursor to destination))
    store.commitOperation("op")

    val outcome = recovery(store, filesystem).recoverIncomplete()

    assertTrue(outcome is RecoveryOutcome.Recovered)
    assertFalse(Files.exists(source))
    assertTrue(store.findActiveByNameKey(NameComparisonKey("demo")) == skillId)
    assertTrue(store.findIncomplete().isEmpty())
  }

  @Test
  fun blocksRetryWhenCommittedSourceWasReplaced() {
    val home = temp.resolve("home")
    val layout = HomeLibraryLayout(home)
    val registry = DefaultAgentRegistry(home)
    val filesystem = NativeFilesystemAdapter()
    val store = SqliteCatalogStore(layout.resolve().databasePath)
    val skillId = SkillId("blocked")
    val source = temp.resolve("source/demo")
    val canonical = layout.resolve().skillsRoot.resolve("demo")
    val destination = registry.destinationFor(AgentId.Cursor).root.resolve("demo")
    Files.createDirectories(source)
    Files.writeString(source.resolve("SKILL.md"), "original")
    val fingerprint = filesystem.sourceFingerprint(source)
    store.beginInstall(
      "op",
      skillId,
      "demo",
      InstallTestInput(source, fingerprint, mapOf(AgentId.Cursor to destination)),
    )
    store.reserveSkill(skillId, "demo", NameComparisonKey("demo"), canonical, mapOf(AgentId.Cursor to destination))
    store.commitOperation("op")
    Files.writeString(source.resolve("SKILL.md"), "replacement")

    val outcome = recovery(store, filesystem).recoverIncomplete()

    assertTrue(outcome is RecoveryOutcome.Blocked)
    assertTrue(Files.exists(source))
    assertFalse(store.findIncomplete().isEmpty())
  }

  @Test
  fun rollsBackInterruptedManagementAndMarksOnlyOwnedChanges() {
    val home = temp.resolve("management-home")
    val layout = HomeLibraryLayout(home)
    val registry = DefaultAgentRegistry(home)
    val filesystem = NativeFilesystemAdapter()
    val store = SqliteCatalogStore(layout.resolve().databasePath)
    val canonical = layout.resolve().skillsRoot.resolve("managed")
    val destination = registry.destinationFor(AgentId.Claude).root.resolve("managed")
    Files.createDirectories(canonical)
    Files.createDirectories(destination.parent)
    store.beginManagement(
      "management-op",
      OperationKind.Enable,
      SkillId("managed"),
      "managed",
      ManagementTestInput(
        canonical,
        mapOf(AgentId.Claude to destination),
        "claude,Disabled,Missing,$destination",
      ),
    )
    createSymlinkOrSkip(destination, canonical)

    val outcome = recovery(store, filesystem).recoverIncomplete()

    assertTrue(outcome is RecoveryOutcome.Recovered)
    assertFalse(Files.exists(destination, java.nio.file.LinkOption.NOFOLLOW_LINKS))
    assertTrue(store.findIncomplete().isEmpty())
  }

  @Test
  fun rollsBackOwnedPartOfMultiAgentTransitionAndPreservesForeignPath() {
    val home = temp.resolve("multi-agent-home")
    val layout = HomeLibraryLayout(home)
    val registry = DefaultAgentRegistry(home)
    val filesystem = NativeFilesystemAdapter()
    val store = SqliteCatalogStore(layout.resolve().databasePath)
    val canonical = layout.resolve().skillsRoot.resolve("managed")
    val claude = registry.destinationFor(AgentId.Claude).root.resolve("managed")
    val cursor = registry.destinationFor(AgentId.Cursor).root.resolve("managed")
    Files.createDirectories(canonical)
    Files.createDirectories(claude.parent)
    Files.createDirectories(cursor.parent)
    store.beginManagement(
      "multi-agent-op",
      OperationKind.Enable,
      SkillId("managed"),
      "managed",
      ManagementTestInput(
        canonical,
        mapOf(AgentId.Claude to claude, AgentId.Cursor to cursor),
        "claude,Disabled,Missing,$claude|cursor,Disabled,Missing,$cursor",
      ),
    )
    createSymlinkOrSkip(claude, canonical)
    Files.writeString(cursor, "foreign")

    val outcome = recovery(store, filesystem).recoverIncomplete()

    assertTrue(outcome is RecoveryOutcome.Recovered)
    assertFalse(Files.exists(claude, java.nio.file.LinkOption.NOFOLLOW_LINKS))
    assertEquals("foreign", Files.readString(cursor))
    assertTrue(store.findIncomplete().isEmpty())
  }

  @Test
  fun blocksManagementRollbackAfterForeignReplacement() {
    val home = temp.resolve("management-conflict-home")
    val layout = HomeLibraryLayout(home)
    val registry = DefaultAgentRegistry(home)
    val filesystem = NativeFilesystemAdapter()
    val store = SqliteCatalogStore(layout.resolve().databasePath)
    val canonical = layout.resolve().skillsRoot.resolve("managed")
    val destination = registry.destinationFor(AgentId.Claude).root.resolve("managed")
    Files.createDirectories(canonical)
    Files.createDirectories(destination.parent)
    store.beginManagement(
      "management-conflict",
      OperationKind.Enable,
      SkillId("managed"),
      "managed",
      ManagementTestInput(
        canonical,
        mapOf(AgentId.Claude to destination),
        "claude,Enabled,Linked,$destination",
      ),
    )
    createSymlinkOrSkip(destination, canonical)
    Files.delete(destination)
    Files.writeString(destination, "foreign")

    val outcome = recovery(store, filesystem).recoverIncomplete()

    assertTrue(outcome is RecoveryOutcome.Blocked)
    assertEquals("foreign", Files.readString(destination))
    assertFalse(store.findIncomplete().isEmpty())
  }

  @Test
  fun preservesCommittedManagementStateDuringRecovery() {
    val home = temp.resolve("committed-management-home")
    val layout = HomeLibraryLayout(home)
    val registry = DefaultAgentRegistry(home)
    val filesystem = NativeFilesystemAdapter()
    val store = SqliteCatalogStore(layout.resolve().databasePath)
    val canonical = layout.resolve().skillsRoot.resolve("managed")
    val destination = registry.destinationFor(AgentId.Claude).root.resolve("managed")
    Files.createDirectories(canonical)
    Files.createDirectories(destination.parent)
    createSymlinkOrSkip(destination, canonical)
    store.beginManagement(
      "committed-management",
      OperationKind.Enable,
      SkillId("managed"),
      "managed",
      ManagementTestInput(
        canonical,
        mapOf(AgentId.Claude to destination),
        "claude,Disabled,Missing,$destination",
      ),
    )
    store.updatePhase(
      "committed-management",
      OperationPhase.Committed,
      OperationPaths(canonicalPath = canonical),
    )

    val outcome = recovery(store, filesystem).recoverIncomplete()

    assertTrue(outcome is RecoveryOutcome.Recovered)
    assertTrue(Files.isSymbolicLink(destination))
    assertTrue(store.findIncomplete().isEmpty())
  }

  @Test
  fun restoresPublishedRemovalAndMakesRepeatedRecoverySafe() {
    val home = temp.resolve("remove-recovery-home")
    val layout = HomeLibraryLayout(home)
    val registry = DefaultAgentRegistry(home)
    val filesystem = NativeFilesystemAdapter()
    val store = SqliteCatalogStore(layout.resolve().databasePath)
    val canonical = layout.resolve().skillsRoot.resolve("removed")
    val destination = registry.destinationFor(AgentId.Cursor).root.resolve("removed")
    val trash = layout.resolve().trashRoot.resolve("interrupted-remove")
    Files.createDirectories(canonical)
    Files.writeString(canonical.resolve("SKILL.md"), "content")
    Files.createDirectories(destination.parent)
    createSymlinkOrSkip(destination, canonical)
    store.beginManagement(
      "remove-op",
      OperationKind.Remove,
      SkillId("removed"),
      "removed",
      ManagementTestInput(
        canonical,
        mapOf(AgentId.Cursor to destination),
        "cursor,Enabled,Linked,$destination",
      ),
    )
    Files.delete(destination)
    Files.createDirectories(trash.parent)
    Files.move(canonical, trash)
    store.updatePhase(
      "remove-op",
      OperationPhase.Published,
      OperationPaths(canonicalPath = canonical, trashPath = trash),
    )

    val first = recovery(store, filesystem).recoverIncomplete()
    val second = recovery(store, filesystem).recoverIncomplete()

    assertTrue(first is RecoveryOutcome.Recovered)
    assertTrue(second is RecoveryOutcome.Clean)
    assertTrue(Files.exists(canonical.resolve("SKILL.md")))
    assertTrue(Files.isSymbolicLink(destination))
    assertFalse(Files.exists(trash, java.nio.file.LinkOption.NOFOLLOW_LINKS))
  }

  @Test
  fun completesCommittedRemovalAfterDatabaseCommitWithoutTouchingTrash() {
    val home = temp.resolve("committed-remove-home")
    val layout = HomeLibraryLayout(home)
    val registry = DefaultAgentRegistry(home)
    val filesystem = NativeFilesystemAdapter()
    val store = SqliteCatalogStore(layout.resolve().databasePath)
    val trash = layout.resolve().trashRoot.resolve("committed-remove")
    Files.createDirectories(trash)
    Files.writeString(trash.resolve("SKILL.md"), "retained")
    store.beginManagement(
      "committed-remove-op",
      OperationKind.Remove,
      SkillId("committed-remove"),
      "committed-remove",
      ManagementTestInput(
        layout.resolve().skillsRoot.resolve("committed-remove"),
        emptyMap(),
        "",
      ),
    )
    store.updatePhase(
      "committed-remove-op",
      OperationPhase.Committed,
      OperationPaths(trashPath = trash),
    )

    val first = recovery(store, filesystem).recoverIncomplete()
    val second = recovery(store, filesystem).recoverIncomplete()

    assertTrue(first is RecoveryOutcome.Recovered)
    assertTrue(second is RecoveryOutcome.Clean)
    assertEquals("retained", Files.readString(trash.resolve("SKILL.md")))
  }

  private fun recovery(
    store: SqliteCatalogStore,
    filesystem: NativeFilesystemAdapter,
  ): RecoveryCoordinator = RecoveryCoordinator(store, filesystem, store, NoopDiagnostics())
}

private class NoopDiagnostics : DiagnosticsPort {
  override fun record(
    eventCode: String,
    managedId: String?,
    detail: String,
  ) = Unit

  override fun reportSecondaryFailure(
    primaryCode: String,
    secondaryCode: String,
    detail: String,
  ) = Unit
}

private fun createSymlinkOrSkip(
  link: Path,
  target: Path,
) {
  val created = runCatching { Files.createSymbolicLink(link, target) }.isSuccess
  Assumptions.assumeTrue(created, "symlinks unsupported on this filesystem")
}
