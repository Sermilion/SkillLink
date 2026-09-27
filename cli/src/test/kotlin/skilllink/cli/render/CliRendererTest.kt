package skilllink.cli.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import skilllink.application.installation.model.AgentInstallationSnapshot
import skilllink.application.installation.model.DesiredInstallationState
import skilllink.application.installation.model.DisableSkillOutcome
import skilllink.application.installation.model.EnableSkillOutcome
import skilllink.application.installation.model.InstallSkillOutcome
import skilllink.application.installation.model.ListSkillsOutcome
import skilllink.application.installation.model.ManagedSkillSnapshot
import skilllink.application.installation.model.ObservedLinkCondition
import skilllink.application.installation.model.RemoveSkillOutcome
import skilllink.cli.exit.CliExitCodes
import skilllink.domain.agent.AgentId
import skilllink.domain.library.NameComparisonKey
import skilllink.domain.library.SkillId
import java.nio.file.Path

class CliRendererTest {
    @Test
    fun mapsConflictFailuresToExitCodeFour() {
        val rendered = CliRenderer.renderInstall(InstallSkillOutcome.Failed.NameConflict)
        assertEquals(CliExitCodes.CONFLICT, rendered.exitCode)
        assertTrue(rendered.stderr.contains("conflict"))
    }

    @Test
    fun includesRestartReminderAfterSuccessfulInstall() {
        val rendered =
            CliRenderer.renderInstall(
                InstallSkillOutcome.Completed(
                    skillName = "demo",
                    canonicalPath = Path.of("/tmp/demo"),
                    agents = setOf(skilllink.domain.agent.AgentId.Claude),
                    cleanupPending = false,
                ),
            )
        assertTrue(rendered.stdout.contains("Restart affected agents"))
    }

    @Test
    fun rendersDeterministicRowsWithCanonicalAndObservedInstallationState() {
        val canonicalPath = Path.of("/library/alpha")
        val rendered =
            CliRenderer.renderList(
                ListSkillsOutcome.Rows(
                    listOf(
                        ManagedSkillSnapshot(
                            SkillId("one"),
                            "alpha",
                            NameComparisonKey("alpha"),
                            canonicalPath,
                            listOf(
                                AgentInstallationSnapshot(
                                    AgentId.Cursor,
                                    Path.of("/agents/cursor/alpha"),
                                    DesiredInstallationState.Enabled,
                                    ObservedLinkCondition.Foreign,
                                ),
                            ),
                        ),
                        ManagedSkillSnapshot(
                            SkillId("two"),
                            "beta",
                            NameComparisonKey("beta"),
                            Path.of("/library/beta"),
                            emptyList(),
                        ),
                    ),
                ),
            )

        assertEquals(CliExitCodes.SUCCESS, rendered.exitCode)
        assertTrue(rendered.stdout.indexOf("1. alpha") < rendered.stdout.indexOf("2. beta"))
        assertTrue(rendered.stdout.contains(canonicalPath.toString()))
        assertTrue(rendered.stdout.contains("cursor: desired=enabled observed=foreign"))
    }

    @Test
    fun mapsCancellationAndCommittedCleanupToTheirExitCodes() {
        val cancelled = CliRenderer.renderInstall(InstallSkillOutcome.Failed.Cancelled)
        val pending =
            CliRenderer.renderInstall(
                InstallSkillOutcome.Completed(
                    "demo",
                    Path.of("/library/demo"),
                    setOf(AgentId.Claude),
                    cleanupPending = true,
                ),
            )

        assertEquals(CliExitCodes.CANCELLED, cancelled.exitCode)
        assertEquals(CliExitCodes.CLEANUP_PENDING, pending.exitCode)
        assertTrue(pending.stderr.contains("Committed installation"))
    }

    @Test
    fun mapsCapabilityAndStorageFailuresToIoExitCode() {
        val capability = CliRenderer.renderInstall(InstallSkillOutcome.Failed.PlatformCapability)
        val storage = CliRenderer.renderList(ListSkillsOutcome.Failed.StorageInvalid)

        assertEquals(CliExitCodes.IO_OR_PLATFORM, capability.exitCode)
        assertEquals(CliExitCodes.IO_OR_PLATFORM, storage.exitCode)
    }

    @Test
    fun distinguishesManagementOutcomesAndChangedLinkReminders() {
        val noOp = CliRenderer.renderEnable(EnableSkillOutcome.NoOp)
        val notFound = CliRenderer.renderDisable(DisableSkillOutcome.Failed.NotFound)
        val integrity = CliRenderer.renderEnable(EnableSkillOutcome.Failed.IntegrityFailure)
        val conflict = CliRenderer.renderDisable(DisableSkillOutcome.Failed.DestinationConflict)
        val blocked = CliRenderer.renderRemove(RemoveSkillOutcome.Failed.BlockedRecovery)
        val trashPath = Path.of("/home/user/.skilllink/trash/op")
        val completed =
            CliRenderer.renderRemove(
                RemoveSkillOutcome.Completed(
                    skillName = "demo",
                    trashPath = trashPath,
                    formerAgents = setOf(AgentId.Claude),
                    cleanupPending = false,
                ),
            )
        val pendingTrashPath = Path.of("/home/user/.skilllink/trash/op")
        val pending =
            CliRenderer.renderRemove(
                RemoveSkillOutcome.Failed.CleanupPendingCommitted(
                    skillName = "demo",
                    trashPath = pendingTrashPath,
                ),
            )

        assertEquals(CliExitCodes.SUCCESS, noOp.exitCode)
        assertTrue(noOp.stdout.contains("No changes"))
        assertEquals(CliExitCodes.NOT_FOUND, notFound.exitCode)
        assertTrue(notFound.stderr.contains("not found"))
        assertEquals(CliExitCodes.IO_OR_PLATFORM, integrity.exitCode)
        assertTrue(integrity.stderr.contains("missing or invalid"))
        assertEquals(CliExitCodes.CONFLICT, conflict.exitCode)
        assertTrue(conflict.stderr.contains("conflict"))
        assertEquals(CliExitCodes.CONFLICT, blocked.exitCode)
        assertTrue(blocked.stderr.contains("blocked"))
        assertEquals(CliExitCodes.SUCCESS, completed.exitCode)
        assertTrue(completed.stdout.contains(trashPath.toString()))
        assertTrue(completed.stdout.contains("Restart affected agents"))
        assertEquals(CliExitCodes.CLEANUP_PENDING, pending.exitCode)
        assertTrue(pending.stderr.contains("Retained at"))
    }
}
