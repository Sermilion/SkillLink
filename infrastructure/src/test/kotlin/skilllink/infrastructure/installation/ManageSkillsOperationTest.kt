package skilllink.infrastructure.installation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import skilllink.application.installation.InstallSkillOperation
import skilllink.application.installation.ManageSkillsOperation
import skilllink.application.installation.MutationGate
import skilllink.application.installation.RecoveryCoordinator
import skilllink.application.installation.model.DesiredInstallationState
import skilllink.application.installation.model.DisableSkillOutcome
import skilllink.application.installation.model.EnableSkillOutcome
import skilllink.application.installation.model.InstallSkillOutcome
import skilllink.application.installation.model.InstallSkillRequest
import skilllink.application.installation.model.RemoveSkillOutcome
import skilllink.application.installation.model.SkillManagementRequest
import skilllink.application.library.ListManagedSkillsOperation
import skilllink.domain.agent.AgentId
import skilllink.domain.library.NameComparisonKey
import skilllink.domain.library.SkillId
import skilllink.domain.library.SkillNamePolicy
import skilllink.infrastructure.agent.DefaultAgentRegistry
import skilllink.infrastructure.agent.LinkCapabilityProbe
import skilllink.infrastructure.diagnostics.FileDiagnostics
import skilllink.infrastructure.filesystem.NativeFilesystemAdapter
import skilllink.infrastructure.layout.HomeLibraryLayout
import skilllink.infrastructure.lock.FileWriterLock
import skilllink.infrastructure.persistence.SqliteCatalogStore
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager

class ManageSkillsOperationTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun disablesRememberedAgentsAndReEnablesWithExplicitAddition() {
        assumeTrue(LinkCapabilityProbe.current() is skilllink.application.ports.LinkCapability.DirectSymlink)
        val fixture = fixture()
        install(fixture, "demo-skill", setOf(AgentId.Claude))

        val disable =
            fixture.manage.disable(
                SkillManagementRequest(NameComparisonKey("demo-skill"), emptySet()),
            )
        assertTrue(disable is DisableSkillOutcome.Completed)
        val link =
            fixture.registry
                .destinationFor(AgentId.Claude)
                .root
                .resolve("demo-skill")
        assertFalse(Files.exists(link))

        val enable =
            fixture.manage.enable(
                SkillManagementRequest(NameComparisonKey("demo-skill"), setOf(AgentId.Cursor)),
            )
        assertTrue(enable is EnableSkillOutcome.Completed)
        val cursorLink =
            fixture.registry
                .destinationFor(AgentId.Cursor)
                .root
                .resolve("demo-skill")
        assertTrue(Files.isSymbolicLink(cursorLink))
        val committed = fixture.store.listActiveOrdered().single()
        assertEquals(
            setOf(AgentId.Claude, AgentId.Cursor),
            committed.installations.map { it.agent }.toSet(),
        )
        assertEquals(
            setOf(AgentId.Cursor),
            committed.installations
                .filter { it.desired == DesiredInstallationState.Enabled }
                .map { it.agent }
                .toSet(),
        )
    }

    @Test
    fun defaultsDisableAndEnableAllRememberedAgents() {
        assumeTrue(LinkCapabilityProbe.current() is skilllink.application.ports.LinkCapability.DirectSymlink)
        val fixture = fixture()
        install(fixture, "remembered", setOf(AgentId.Claude, AgentId.Codex))

        val disabled =
            fixture.manage.disable(
                SkillManagementRequest(NameComparisonKey("remembered"), emptySet()),
            )
        assertTrue(disabled is DisableSkillOutcome.Completed)
        assertLinks(fixture, "remembered", setOf(AgentId.Claude, AgentId.Codex), expected = false)

        val enabled =
            fixture.manage.enable(
                SkillManagementRequest(NameComparisonKey("REMEMBERED"), emptySet()),
            )
        assertTrue(enabled is EnableSkillOutcome.Completed)
        assertLinks(fixture, "remembered", setOf(AgentId.Claude, AgentId.Codex), expected = true)

        val selectedOnly =
            fixture.manage.disable(
                SkillManagementRequest(NameComparisonKey("remembered"), setOf(AgentId.Claude)),
            )
        assertTrue(selectedOnly is DisableSkillOutcome.Completed)
        assertLinks(fixture, "remembered", setOf(AgentId.Claude), expected = false)
        assertLinks(fixture, "remembered", setOf(AgentId.Codex), expected = true)
        assertEquals(
            DisableSkillOutcome.NoOp,
            fixture.manage.disable(
                SkillManagementRequest(NameComparisonKey("remembered"), setOf(AgentId.Claude)),
            ),
        )
    }

    private fun assertLinks(
        fixture: ManageFixture,
        name: String,
        agents: Set<AgentId>,
        expected: Boolean,
    ) {
        agents.forEach { agent ->
            val destination =
                fixture.registry
                    .destinationFor(agent)
                    .root
                    .resolve(name)
            assertEquals(expected, Files.isSymbolicLink(destination))
        }
    }

    @Test
    fun recreatesMissingLinkAndLeavesUnselectedAgentUnchanged() {
        assumeTrue(LinkCapabilityProbe.current() is skilllink.application.ports.LinkCapability.DirectSymlink)
        val fixture = fixture()
        install(fixture, "missing-link", setOf(AgentId.Claude))
        val claude =
            fixture.registry
                .destinationFor(AgentId.Claude)
                .root
                .resolve("missing-link")
        val cursor =
            fixture.registry
                .destinationFor(AgentId.Cursor)
                .root
                .resolve("missing-link")
        val canonical =
            fixture.layout
                .resolve()
                .skillsRoot
                .resolve("missing-link")
        val canonicalContent = Files.readString(canonical.resolve("SKILL.md"))
        Files.delete(claude)

        val outcome =
            fixture.manage.enable(
                SkillManagementRequest(NameComparisonKey("missing-link"), emptySet()),
            )

        assertTrue(outcome is EnableSkillOutcome.Completed)
        assertTrue(Files.isSymbolicLink(claude))
        assertFalse(Files.exists(cursor, java.nio.file.LinkOption.NOFOLLOW_LINKS))
        assertTrue(
            fixture.manage.disable(
                SkillManagementRequest(NameComparisonKey("missing-link"), emptySet()),
            ) is DisableSkillOutcome.Completed,
        )
        assertEquals(canonicalContent, Files.readString(canonical.resolve("SKILL.md")))
    }

    @Test
    fun rejectsMissingAndInvalidCanonicalContentBeforeEnabling() {
        assumeTrue(LinkCapabilityProbe.current() is skilllink.application.ports.LinkCapability.DirectSymlink)
        val fixture = fixture()
        install(fixture, "missing-canonical", setOf(AgentId.Claude))
        val missingCanonical =
            fixture.layout
                .resolve()
                .skillsRoot
                .resolve("missing-canonical")
        Files.delete(missingCanonical.resolve("nested.txt"))
        Files.delete(missingCanonical.resolve("SKILL.md"))
        Files.delete(missingCanonical)

        val missing =
            fixture.manage.enable(
                SkillManagementRequest(NameComparisonKey("missing-canonical"), emptySet()),
            )

        install(fixture, "invalid-canonical", setOf(AgentId.Codex))
        Files.writeString(
            fixture.layout
                .resolve()
                .skillsRoot
                .resolve("invalid-canonical/SKILL.md"),
            "invalid",
        )
        val invalid =
            fixture.manage.enable(
                SkillManagementRequest(NameComparisonKey("invalid-canonical"), emptySet()),
            )

        assertEquals(EnableSkillOutcome.Failed.IntegrityFailure, missing)
        assertEquals(EnableSkillOutcome.Failed.IntegrityFailure, invalid)
    }

    @Test
    fun disableRemovesDanglingOwnedLinkWithoutRemovingCanonicalContent() {
        assumeTrue(LinkCapabilityProbe.current() is skilllink.application.ports.LinkCapability.DirectSymlink)
        val fixture = fixture()
        install(fixture, "dangling", setOf(AgentId.Claude))
        val canonical =
            fixture.layout
                .resolve()
                .skillsRoot
                .resolve("dangling")
        val link =
            fixture.registry
                .destinationFor(AgentId.Claude)
                .root
                .resolve("dangling")
        Files.delete(canonical.resolve("nested.txt"))
        Files.delete(canonical.resolve("SKILL.md"))
        Files.delete(canonical)

        val outcome =
            fixture.manage.disable(
                SkillManagementRequest(NameComparisonKey("dangling"), emptySet()),
            )

        assertTrue(outcome is DisableSkillOutcome.Completed)
        assertFalse(Files.exists(link, java.nio.file.LinkOption.NOFOLLOW_LINKS))
        assertFalse(Files.exists(canonical, java.nio.file.LinkOption.NOFOLLOW_LINKS))
    }

    @Test
    fun foreignReplacementBlocksDisableWithoutRemovingReplacement() {
        assumeTrue(LinkCapabilityProbe.current() is skilllink.application.ports.LinkCapability.DirectSymlink)
        val fixture = fixture()
        install(fixture, "foreign", setOf(AgentId.Claude))
        val link =
            fixture.registry
                .destinationFor(AgentId.Claude)
                .root
                .resolve("foreign")
        Files.delete(link)
        Files.writeString(link, "foreign")

        val outcome =
            fixture.manage.disable(
                SkillManagementRequest(NameComparisonKey("foreign"), emptySet()),
            )

        assertEquals(DisableSkillOutcome.Failed.DestinationConflict, outcome)
        assertEquals("foreign", Files.readString(link))
    }

    @Test
    fun rememberedDestinationSurvivesEnvironmentChange() {
        assumeTrue(LinkCapabilityProbe.current() is skilllink.application.ports.LinkCapability.DirectSymlink)
        val oldRoot = temp.resolve("old-claude")
        val newRoot = temp.resolve("new-claude")
        val fixture = fixture(mapOf("CLAUDE_CONFIG_DIR" to oldRoot.toString()))
        install(fixture, "environment-stable", setOf(AgentId.Claude))
        val oldLink = oldRoot.resolve("skills/environment-stable")
        val newRegistry =
            DefaultAgentRegistry(
                fixture.layout
                    .resolve()
                    .root.parent,
                mapOf("CLAUDE_CONFIG_DIR" to newRoot.toString()),
            )
        val reconfigured =
            manageOperation(
                ManageOperationInputs(
                    fixture.layout,
                    fixture.store,
                    newRegistry,
                    fixture.recovery,
                ),
            )

        assertTrue(
            reconfigured.disable(
                SkillManagementRequest(NameComparisonKey("environment-stable"), emptySet()),
            ) is DisableSkillOutcome.Completed,
        )
        assertFalse(Files.exists(oldLink, java.nio.file.LinkOption.NOFOLLOW_LINKS))
        assertTrue(
            reconfigured.enable(
                SkillManagementRequest(NameComparisonKey("environment-stable"), emptySet()),
            ) is EnableSkillOutcome.Completed,
        )
        assertTrue(Files.isSymbolicLink(oldLink))
        assertFalse(Files.exists(newRoot.resolve("skills/environment-stable"), java.nio.file.LinkOption.NOFOLLOW_LINKS))
    }

    @Test
    fun reportsCaseInsensitiveNotFoundAndPreservesNumericNamesLiterally() {
        assumeTrue(LinkCapabilityProbe.current() is skilllink.application.ports.LinkCapability.DirectSymlink)
        val fixture = fixture()
        install(fixture, "Case-Sensitive", setOf(AgentId.Junie))

        val enabled =
            fixture.manage.enable(
                SkillManagementRequest(
                    SkillNamePolicy.comparisonKeyForLookup("CASE-SENSITIVE")!!,
                    emptySet(),
                ),
            )
        val missing =
            fixture.manage.disable(
                SkillManagementRequest(NameComparisonKey("does-not-exist"), emptySet()),
            )
        val missingEnable =
            fixture.manage.enable(
                SkillManagementRequest(NameComparisonKey("does-not-exist"), emptySet()),
            )
        val missingRemove =
            fixture.manage.remove(
                SkillManagementRequest(NameComparisonKey("does-not-exist"), emptySet()),
            )

        assertEquals(EnableSkillOutcome.NoOp, enabled)
        assertEquals(DisableSkillOutcome.Failed.NotFound, missing)
        assertEquals(EnableSkillOutcome.Failed.NotFound, missingEnable)
        assertEquals(RemoveSkillOutcome.Failed.NotFound, missingRemove)
    }

    @Test
    fun removeRetainsByteIdenticalTrashAndExcludesActiveCatalog() {
        assumeTrue(LinkCapabilityProbe.current() is skilllink.application.ports.LinkCapability.DirectSymlink)
        val fixture = fixture()
        install(fixture, "trash-me", setOf(AgentId.Codex))
        val before =
            Files.readString(
                fixture.layout
                    .resolve()
                    .skillsRoot
                    .resolve("trash-me/nested.txt"),
            )

        val removed =
            fixture.manage.remove(
                SkillManagementRequest(NameComparisonKey("trash-me"), emptySet()),
            )
        assertTrue(removed is RemoveSkillOutcome.Completed)
        val completed = removed as RemoveSkillOutcome.Completed
        assertFalse(
            Files.exists(
                fixture.layout
                    .resolve()
                    .skillsRoot
                    .resolve("trash-me"),
            ),
        )
        assertEquals(before, Files.readString(completed.trashPath.resolve("nested.txt")))
        assertEquals(0, fixture.store.listActiveOrdered().size)
    }

    @Test
    fun removePreservesTrashMetadataAndAllowsSameNameImport() {
        assumeTrue(LinkCapabilityProbe.current() is skilllink.application.ports.LinkCapability.DirectSymlink)
        val fixture = fixture()
        install(fixture, "reusable", setOf(AgentId.Claude, AgentId.Cursor))
        install(fixture, "unrelated", setOf(AgentId.Codex))
        val originalId = fixture.store.findActiveByNameKey(NameComparisonKey("reusable"))
        val claudeLink =
            fixture.registry
                .destinationFor(AgentId.Claude)
                .root
                .resolve("reusable")
        val cursorLink =
            fixture.registry
                .destinationFor(AgentId.Cursor)
                .root
                .resolve("reusable")
        val unrelatedLink =
            fixture.registry
                .destinationFor(AgentId.Codex)
                .root
                .resolve("unrelated")
        assertNotNull(originalId)

        val removed =
            fixture.manage.remove(
                SkillManagementRequest(NameComparisonKey("reusable"), emptySet()),
            )
        assertTrue(removed is RemoveSkillOutcome.Completed)
        val completed = removed as RemoveSkillOutcome.Completed
        val replacement = createBundle("reusable", "replacement")
        val imported =
            fixture.install.execute(
                InstallSkillRequest.NewImport(replacement.resolve("SKILL.md"), setOf(AgentId.Junie)),
            )

        assertTrue(imported is InstallSkillOutcome.Completed)
        assertNotEquals(originalId, fixture.store.findActiveByNameKey(NameComparisonKey("reusable")))
        assertFalse(Files.exists(claudeLink, java.nio.file.LinkOption.NOFOLLOW_LINKS))
        assertFalse(Files.exists(cursorLink, java.nio.file.LinkOption.NOFOLLOW_LINKS))
        assertTrue(Files.isSymbolicLink(unrelatedLink))
        assertEquals(
            setOf("reusable", "unrelated"),
            fixture.store
                .listActiveOrdered()
                .map { it.displayName }
                .toSet(),
        )
        assertTrashRecord(fixture, originalId, completed.trashPath)
    }

    private fun assertTrashRecord(
        fixture: ManageFixture,
        originalId: SkillId?,
        trashPath: Path,
    ) {
        DriverManager.getConnection("jdbc:sqlite:${fixture.layout.resolve().databasePath}").use { connection ->
            connection
                .prepareStatement(
                    """
                    SELECT skill_id, display_name, former_canonical_path, trash_path, agent_selections
                    FROM trash_records
                    """.trimIndent(),
                ).use { statement ->
                    statement.executeQuery().use { result ->
                        assertTrue(result.next())
                        assertEquals(originalId?.value, result.getString("skill_id"))
                        assertEquals("reusable", result.getString("display_name"))
                        assertEquals(
                            fixture.layout
                                .resolve()
                                .skillsRoot
                                .resolve("reusable")
                                .toString(),
                            result.getString("former_canonical_path"),
                        )
                        assertEquals(trashPath.toString(), result.getString("trash_path"))
                        assertEquals(
                            setOf("claude", "cursor"),
                            result.getString("agent_selections").split(";").toSet(),
                        )
                    }
                }
        }
    }

    @Test
    fun literalNumericNameDoesNotUseListIndex() {
        assumeTrue(LinkCapabilityProbe.current() is skilllink.application.ports.LinkCapability.DirectSymlink)
        val fixture = fixture()
        install(fixture, "1", setOf(AgentId.Junie))
        install(fixture, "2", setOf(AgentId.Junie))

        val outcome =
            fixture.manage.disable(
                SkillManagementRequest(NameComparisonKey("2"), emptySet()),
            )
        assertTrue(outcome is DisableSkillOutcome.Completed)
        assertEquals(2, fixture.store.listActiveOrdered().size)
        val stillLinked =
            fixture.registry
                .destinationFor(AgentId.Junie)
                .root
                .resolve("1")
        assertTrue(Files.isSymbolicLink(stillLinked))
    }

    @Test
    fun enableNoOpWhenAlreadyLinked() {
        assumeTrue(LinkCapabilityProbe.current() is skilllink.application.ports.LinkCapability.DirectSymlink)
        val fixture = fixture()
        install(fixture, "noop", setOf(AgentId.Claude))
        val outcome =
            fixture.manage.enable(
                SkillManagementRequest(NameComparisonKey("noop"), emptySet()),
            )
        assertEquals(EnableSkillOutcome.NoOp, outcome)
    }

    private fun install(
        fixture: ManageFixture,
        name: String,
        agents: Set<AgentId>,
    ) {
        val source = createBundle(name, "payload-$name")
        val outcome = fixture.install.execute(InstallSkillRequest.NewImport(source.resolve("SKILL.md"), agents))
        assertTrue(outcome is InstallSkillOutcome.Completed)
    }

    private fun createBundle(
        name: String,
        payload: String,
    ): Path {
        val root = Files.createTempDirectory(temp, "bundle").resolve(name)
        Files.createDirectories(root)
        Files.writeString(root.resolve("SKILL.md"), "---\nname: $name\ndescription: demo\n---\n")
        Files.writeString(root.resolve("nested.txt"), payload)
        return root
    }

    private fun fixture(environment: Map<String, String> = emptyMap()): ManageFixture {
        val home = temp.resolve("home")
        val layout = HomeLibraryLayout(home)
        val registry = DefaultAgentRegistry(home, environment)
        val store = SqliteCatalogStore(layout.resolve().databasePath)
        val filesystem = NativeFilesystemAdapter(layout, registry)
        val diagnostics = FileDiagnostics(layout.resolve().diagnosticsRoot)
        val recovery = RecoveryCoordinator(store, filesystem, store, diagnostics)
        val writerLock = FileWriterLock(layout.resolve().lockPath)
        return ManageFixture(
            layout,
            registry,
            store,
            InstallSkillOperation(
                layout,
                store,
                filesystem,
                registry,
                store,
                MutationGate(writerLock, recovery),
            ),
            manageOperation(
                ManageOperationInputs(layout, store, registry, recovery, filesystem, writerLock),
            ),
            ListManagedSkillsOperation(layout, store, filesystem, writerLock, recovery),
            recovery,
        )
    }

    private fun manageOperation(input: ManageOperationInputs): ManageSkillsOperation =
        ManageSkillsOperation(
            input.layout,
            input.store,
            input.filesystem ?: NativeFilesystemAdapter(input.layout, input.registry),
            input.registry,
            input.store,
            MutationGate(
                input.writerLock ?: FileWriterLock(input.layout.resolve().lockPath),
                input.recovery,
            ),
        )
}

private data class ManageOperationInputs(
    val layout: HomeLibraryLayout,
    val store: SqliteCatalogStore,
    val registry: DefaultAgentRegistry,
    val recovery: RecoveryCoordinator,
    val filesystem: NativeFilesystemAdapter? = null,
    val writerLock: FileWriterLock? = null,
)

private data class ManageFixture(
    val layout: HomeLibraryLayout,
    val registry: DefaultAgentRegistry,
    val store: SqliteCatalogStore,
    val install: InstallSkillOperation,
    val manage: ManageSkillsOperation,
    val list: ListManagedSkillsOperation,
    val recovery: RecoveryCoordinator,
)
