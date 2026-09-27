package skilllink.infrastructure.installation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import skilllink.application.installation.RecoveryCoordinator
import skilllink.application.installation.RecoveryOutcome
import skilllink.application.ports.DiagnosticsPort
import skilllink.application.ports.OperationPaths
import skilllink.application.ports.OperationPhase
import skilllink.domain.agent.AgentId
import skilllink.domain.library.NameComparisonKey
import skilllink.domain.library.SkillId
import skilllink.infrastructure.agent.DefaultAgentRegistry
import skilllink.infrastructure.filesystem.NativeFilesystemAdapter
import skilllink.infrastructure.layout.HomeLibraryLayout
import skilllink.infrastructure.persistence.SqliteCatalogStore
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
        val filesystem = NativeFilesystemAdapter(layout, registry)
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
        Files.createSymbolicLink(destination, canonical)
        store.beginInstall("op", skillId, "demo", source, "fingerprint", mapOf(AgentId.Claude to destination))
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
        store.beginInstall("unrelated-op", unrelatedId, "unrelated", unrelatedSource, unrelatedFingerprint, emptyMap())
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
        val filesystem = NativeFilesystemAdapter(layout, registry)
        val store = SqliteCatalogStore(layout.resolve().databasePath)
        val skillId = SkillId("committed")
        val source = temp.resolve("source/demo")
        val canonical = layout.resolve().skillsRoot.resolve("demo")
        val destination = registry.destinationFor(AgentId.Cursor).root.resolve("demo")
        Files.createDirectories(source)
        Files.writeString(source.resolve("SKILL.md"), "source")
        val fingerprint = filesystem.sourceFingerprint(source)
        store.beginInstall("op", skillId, "demo", source, fingerprint, mapOf(AgentId.Cursor to destination))
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
        val filesystem = NativeFilesystemAdapter(layout, registry)
        val store = SqliteCatalogStore(layout.resolve().databasePath)
        val skillId = SkillId("blocked")
        val source = temp.resolve("source/demo")
        val canonical = layout.resolve().skillsRoot.resolve("demo")
        val destination = registry.destinationFor(AgentId.Cursor).root.resolve("demo")
        Files.createDirectories(source)
        Files.writeString(source.resolve("SKILL.md"), "original")
        val fingerprint = filesystem.sourceFingerprint(source)
        store.beginInstall("op", skillId, "demo", source, fingerprint, mapOf(AgentId.Cursor to destination))
        store.reserveSkill(skillId, "demo", NameComparisonKey("demo"), canonical, mapOf(AgentId.Cursor to destination))
        store.commitOperation("op")
        Files.writeString(source.resolve("SKILL.md"), "replacement")

        val outcome = recovery(store, filesystem).recoverIncomplete()

        assertTrue(outcome is RecoveryOutcome.Blocked)
        assertTrue(Files.exists(source))
        assertFalse(store.findIncomplete().isEmpty())
    }

    private fun recovery(
        store: SqliteCatalogStore,
        filesystem: NativeFilesystemAdapter,
    ): RecoveryCoordinator = RecoveryCoordinator(store, filesystem, store, NoopDiagnostics())
}

private class NoopDiagnostics : DiagnosticsPort {
    override fun record(eventCode: String, managedId: String?, detail: String) = Unit

    override fun reportSecondaryFailure(primaryCode: String, secondaryCode: String, detail: String) = Unit
}
