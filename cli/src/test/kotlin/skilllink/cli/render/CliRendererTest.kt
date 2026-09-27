package skilllink.cli.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import skilllink.application.installation.model.AgentInstallationSnapshot
import skilllink.application.installation.model.DesiredInstallationState
import skilllink.application.installation.model.InstallSkillOutcome
import skilllink.application.installation.model.ListSkillsOutcome
import skilllink.application.installation.model.ManagedSkillSnapshot
import skilllink.application.installation.model.ObservedLinkCondition
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
        val rendered =
            CliRenderer.renderList(
                ListSkillsOutcome.Rows(
                    listOf(
                        ManagedSkillSnapshot(
                            SkillId("one"),
                            "alpha",
                            NameComparisonKey("alpha"),
                            Path.of("/library/alpha"),
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
        assertTrue(rendered.stdout.contains("/library/alpha"))
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
}
