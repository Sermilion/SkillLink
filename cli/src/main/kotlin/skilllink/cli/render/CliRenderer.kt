package skilllink.cli.render

import skilllink.application.installation.model.InstallSkillOutcome
import skilllink.application.installation.model.ListSkillsOutcome
import skilllink.application.installation.model.ManagedSkillSnapshot
import skilllink.cli.exit.CliExitCodes
import skilllink.cli.parse.CliParseOutcome

data class RenderedCliOutcome(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

object CliRenderer {
    private const val VERSION = "0.1.0-SNAPSHOT"

    fun renderParseFailure(failure: CliParseOutcome.Failed): RenderedCliOutcome =
        RenderedCliOutcome(
            exitCode = CliExitCodes.INVALID_ARGUMENTS,
            stdout = "",
            stderr = renderParseMessage(failure),
        )

    fun renderHelp(): RenderedCliOutcome =
        RenderedCliOutcome(
            exitCode = CliExitCodes.SUCCESS,
            stdout = """
                skill-link install <SKILL.md> --agent <agent> [--agent <agent>...]
                skill-link list
                skill-link --help
                skill-link --version

                Agents: claude, codex, junie, cursor. Junie and Cursor may also read shared skill roots.
                Restart affected agents after link changes.
            """.trimIndent() + "\n",
            stderr = "",
        )

    fun renderVersion(): RenderedCliOutcome =
        RenderedCliOutcome(
            exitCode = CliExitCodes.SUCCESS,
            stdout = "skill-link $VERSION\n",
            stderr = "",
        )

    fun renderList(outcome: ListSkillsOutcome): RenderedCliOutcome =
        when (outcome) {
            is ListSkillsOutcome.EmptyLibrary ->
                RenderedCliOutcome(
                    exitCode = CliExitCodes.SUCCESS,
                    stdout = "No managed skills.\n",
                    stderr = "",
                )
            is ListSkillsOutcome.Rows ->
                RenderedCliOutcome(
                    exitCode = CliExitCodes.SUCCESS,
                    stdout = formatRows(outcome.skills),
                    stderr = "",
                )
            is ListSkillsOutcome.Failed.StorageInaccessible,
            is ListSkillsOutcome.Failed.StorageInvalid,
            ->
                RenderedCliOutcome(
                    exitCode = CliExitCodes.IO_OR_PLATFORM,
                    stdout = "",
                    stderr = "SkillLink storage is inaccessible or invalid.\n",
                )
        }

    fun renderInstall(outcome: InstallSkillOutcome): RenderedCliOutcome =
        when (outcome) {
            is InstallSkillOutcome.Completed -> {
                val cleanup =
                    if (outcome.cleanupPending) {
                        " Cleanup pending for original source."
                    } else {
                        ""
                    }
                RenderedCliOutcome(
                    exitCode = if (outcome.cleanupPending) CliExitCodes.CLEANUP_PENDING else CliExitCodes.SUCCESS,
                    stdout =
                        "Installed ${outcome.skillName} at ${outcome.canonicalPath} for " +
                            "${outcome.agents.joinToString { it.wireValue }}.$cleanup " +
                            "Restart affected agents.\n",
                    stderr = if (outcome.cleanupPending) "Committed installation; cleanup pending.\n" else "",
                )
            }
            is InstallSkillOutcome.Failed.InvalidArguments ->
                failure(CliExitCodes.INVALID_ARGUMENTS, "Invalid install arguments.")
            is InstallSkillOutcome.Failed.SourceInvalid ->
                failure(CliExitCodes.INVALID_ARGUMENTS, "Invalid skill source.")
            is InstallSkillOutcome.Failed.NameConflict ->
                failure(CliExitCodes.CONFLICT, "Managed skill name conflict.")
            is InstallSkillOutcome.Failed.DestinationConflict ->
                failure(CliExitCodes.CONFLICT, "Agent destination conflict.")
            is InstallSkillOutcome.Failed.WriterBusy ->
                failure(CliExitCodes.CONFLICT, "Another SkillLink writer is active.")
            is InstallSkillOutcome.Failed.BlockedRecovery ->
                failure(CliExitCodes.CONFLICT, "Recovery is blocked.")
            is InstallSkillOutcome.Failed.PlatformCapability ->
                failure(CliExitCodes.IO_OR_PLATFORM, "Direct symlink capability is unavailable.")
            is InstallSkillOutcome.Failed.IoFailure ->
                failure(CliExitCodes.IO_OR_PLATFORM, "Install failed due to IO or persistence.")
            is InstallSkillOutcome.Failed.Cancelled ->
                failure(CliExitCodes.CANCELLED, "Install cancelled before commitment.")
            is InstallSkillOutcome.Failed.CleanupPendingCommitted ->
                failure(CliExitCodes.CLEANUP_PENDING, "Committed installation for ${outcome.skillName}; cleanup pending.")
        }

    private fun failure(code: Int, message: String): RenderedCliOutcome =
        RenderedCliOutcome(exitCode = code, stdout = "", stderr = "$message\n")

    private fun renderParseMessage(failure: CliParseOutcome.Failed): String =
        when (failure) {
            CliParseOutcome.Failed.InvalidArguments -> "Invalid arguments.\n"
            CliParseOutcome.Failed.UnknownAgent -> "Unknown agent.\n"
            CliParseOutcome.Failed.UnknownOption -> "Unknown option.\n"
            CliParseOutcome.Failed.UnsupportedCommand -> "Command is not available in this release.\n"
        }

    private fun formatRows(skills: List<ManagedSkillSnapshot>): String {
        val builder = StringBuilder()
        skills.forEachIndexed { index, skill ->
            builder.append("${index + 1}. ${escape(skill.displayName)}  ${escape(skill.canonicalPath.toString())}\n")
            skill.installations.forEach { installation ->
                builder.append(
                    "   ${installation.agent.wireValue}: desired=${installation.desired.name.lowercase()} " +
                        "observed=${installation.observed.name.lowercase()} " +
                        "${escape(installation.destination.toString())}\n",
                )
            }
        }
        return builder.toString()
    }

    private fun escape(value: String): String = buildString(value.length) {
        value.forEach { char ->
            if (char.code < 32 || char.code == 127) {
                append('?')
            } else {
                append(char)
            }
        }
    }
}
