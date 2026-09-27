package skilllink.cli.render

import skilllink.application.installation.model.DisableSkillOutcome
import skilllink.application.installation.model.EnableSkillOutcome
import skilllink.application.installation.model.InstallSkillOutcome
import skilllink.application.installation.model.ListSkillsOutcome
import skilllink.application.installation.model.RemoveSkillOutcome
import skilllink.application.library.OpenManagedSkillOutcome
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
      stdout =
        """
        skill-link install <SKILL.md> --agent <agent> [--agent <agent>...]
        skill-link list
        skill-link open <name>
        skill-link enable <name> [--agent <agent>...]
        skill-link disable <name> [--agent <agent>...]
        skill-link remove <name>
        skill-link --help
        skill-link --version

        Management uses skill names (case-insensitive), never list row numbers.
        Removed skills remain under ~/.skilllink/trash/; restore and permanent deletion are not available.
        Agents: claude, codex, junie, cursor. Junie and Cursor may also read shared skill roots.
        Restart affected agents after link changes or source edits.
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
      is ListSkillsOutcome.EmptyLibrary -> {
        RenderedCliOutcome(
          exitCode = CliExitCodes.SUCCESS,
          stdout = "No managed skills.\n",
          stderr = "",
        )
      }

      is ListSkillsOutcome.Rows -> {
        RenderedCliOutcome(
          exitCode = CliExitCodes.SUCCESS,
          stdout = formatRows(outcome.skills),
          stderr = "",
        )
      }

      is ListSkillsOutcome.Failed.StorageInaccessible,
      is ListSkillsOutcome.Failed.StorageInvalid,
      -> {
        RenderedCliOutcome(
          exitCode = CliExitCodes.IO_OR_PLATFORM,
          stdout = "",
          stderr = "SkillLink storage is inaccessible or invalid.\n",
        )
      }
    }

  fun renderInstall(outcome: InstallSkillOutcome): RenderedCliOutcome =
    when (outcome) {
      is InstallSkillOutcome.Completed -> renderCompletedInstall(outcome)
      is InstallSkillOutcome.Failed -> renderInstallFailure(outcome)
    }

  fun renderEnable(outcome: EnableSkillOutcome): RenderedCliOutcome =
    when (outcome) {
      EnableSkillOutcome.NoOp -> {
        RenderedCliOutcome(CliExitCodes.SUCCESS, "No changes were needed.\n", "")
      }

      is EnableSkillOutcome.Completed -> {
        renderCompletedEnable(outcome)
      }

      is EnableSkillOutcome.Failed -> {
        renderEnableFailure(outcome)
      }
    }

  fun renderDisable(outcome: DisableSkillOutcome): RenderedCliOutcome =
    when (outcome) {
      DisableSkillOutcome.NoOp -> {
        RenderedCliOutcome(CliExitCodes.SUCCESS, "No changes were needed.\n", "")
      }

      is DisableSkillOutcome.Completed -> {
        renderCompletedDisable(outcome)
      }

      is DisableSkillOutcome.Failed -> {
        renderDisableFailure(outcome)
      }
    }

  fun renderRemove(outcome: RemoveSkillOutcome): RenderedCliOutcome =
    when (outcome) {
      is RemoveSkillOutcome.Completed -> renderCompletedRemove(outcome)
      is RemoveSkillOutcome.Failed -> renderRemoveFailure(outcome)
    }

  fun renderOpen(outcome: OpenManagedSkillOutcome): RenderedCliOutcome =
    when (outcome) {
      is OpenManagedSkillOutcome.Opened -> {
        RenderedCliOutcome(
          CliExitCodes.SUCCESS,
          "Opened ${outcome.skillName} at ${outcome.skillFile}. Restart affected agents after editing.\n",
          "",
        )
      }

      OpenManagedSkillOutcome.Failed.InvalidArguments -> {
        failure(CliExitCodes.INVALID_ARGUMENTS, "Invalid skill name.")
      }

      OpenManagedSkillOutcome.Failed.NotFound -> {
        failure(CliExitCodes.NOT_FOUND, "Managed skill not found.")
      }

      OpenManagedSkillOutcome.Failed.IntegrityFailure -> {
        failure(CliExitCodes.IO_OR_PLATFORM, "Canonical skill content is missing or invalid.")
      }

      OpenManagedSkillOutcome.Failed.WriterBusy -> {
        failure(CliExitCodes.CONFLICT, "Another SkillLink writer is active.")
      }

      OpenManagedSkillOutcome.Failed.Unavailable -> {
        failure(CliExitCodes.IO_OR_PLATFORM, "No default editor or file handler is available for the skill file.")
      }

      OpenManagedSkillOutcome.Failed.IoFailure -> {
        failure(CliExitCodes.IO_OR_PLATFORM, "Could not open the skill file.")
      }
    }
}

private fun renderCompletedInstall(outcome: InstallSkillOutcome.Completed): RenderedCliOutcome {
  val cleanup = if (outcome.cleanupPending) " Cleanup pending for original source." else ""
  return RenderedCliOutcome(
    exitCode = if (outcome.cleanupPending) CliExitCodes.CLEANUP_PENDING else CliExitCodes.SUCCESS,
    stdout =
      "Installed ${outcome.skillName} at ${outcome.canonicalPath} for " +
        "${outcome.agents.joinToString { it.wireValue }}.$cleanup Restart affected agents.\n",
    stderr = if (outcome.cleanupPending) "Committed installation; cleanup pending.\n" else "",
  )
}

private fun renderInstallFailure(outcome: InstallSkillOutcome.Failed): RenderedCliOutcome =
  when (outcome) {
    InstallSkillOutcome.Failed.InvalidArguments -> {
      failure(CliExitCodes.INVALID_ARGUMENTS, "Invalid install arguments.")
    }

    InstallSkillOutcome.Failed.SourceInvalid -> {
      failure(CliExitCodes.INVALID_ARGUMENTS, "Invalid skill source.")
    }

    InstallSkillOutcome.Failed.NameConflict -> {
      failure(CliExitCodes.CONFLICT, "Managed skill name conflict.")
    }

    InstallSkillOutcome.Failed.DestinationConflict -> {
      failure(CliExitCodes.CONFLICT, "Agent destination conflict.")
    }

    InstallSkillOutcome.Failed.WriterBusy -> {
      failure(CliExitCodes.CONFLICT, "Another SkillLink writer is active.")
    }

    InstallSkillOutcome.Failed.BlockedRecovery -> {
      failure(CliExitCodes.CONFLICT, "Recovery is blocked.")
    }

    InstallSkillOutcome.Failed.PlatformCapability -> {
      failure(CliExitCodes.IO_OR_PLATFORM, "Direct symlink capability is unavailable.")
    }

    InstallSkillOutcome.Failed.IoFailure -> {
      failure(CliExitCodes.IO_OR_PLATFORM, "Install failed due to IO or persistence.")
    }

    InstallSkillOutcome.Failed.Cancelled -> {
      failure(CliExitCodes.CANCELLED, "Install cancelled before commitment.")
    }

    is InstallSkillOutcome.Failed.CleanupPendingCommitted -> {
      failure(
        CliExitCodes.CLEANUP_PENDING,
        "Committed installation for ${outcome.skillName}; cleanup pending.",
      )
    }
  }

private fun renderCompletedEnable(outcome: EnableSkillOutcome.Completed): RenderedCliOutcome =
  RenderedCliOutcome(
    exitCode = if (outcome.cleanupPending) CliExitCodes.CLEANUP_PENDING else CliExitCodes.SUCCESS,
    stdout =
      "Enabled ${outcome.skillName} at ${outcome.canonicalPath} for " +
        "${outcome.changedAgents.joinToString { it.wireValue }}. Restart affected agents.\n",
    stderr = if (outcome.cleanupPending) "Committed enable; cleanup pending.\n" else "",
  )

private fun renderEnableFailure(outcome: EnableSkillOutcome.Failed): RenderedCliOutcome =
  when (outcome) {
    EnableSkillOutcome.Failed.InvalidArguments -> {
      failure(CliExitCodes.INVALID_ARGUMENTS, "Invalid enable arguments.")
    }

    EnableSkillOutcome.Failed.NotFound -> {
      failure(CliExitCodes.NOT_FOUND, "Managed skill not found.")
    }

    EnableSkillOutcome.Failed.IntegrityFailure -> {
      failure(CliExitCodes.IO_OR_PLATFORM, "Canonical skill content is missing or invalid.")
    }

    EnableSkillOutcome.Failed.DestinationConflict -> {
      failure(CliExitCodes.CONFLICT, "Agent destination conflict.")
    }

    EnableSkillOutcome.Failed.WriterBusy -> {
      failure(CliExitCodes.CONFLICT, "Another SkillLink writer is active.")
    }

    EnableSkillOutcome.Failed.BlockedRecovery -> {
      failure(CliExitCodes.CONFLICT, "Recovery is blocked.")
    }

    EnableSkillOutcome.Failed.PlatformCapability -> {
      failure(CliExitCodes.IO_OR_PLATFORM, "Direct symlink capability is unavailable.")
    }

    EnableSkillOutcome.Failed.IoFailure -> {
      failure(CliExitCodes.IO_OR_PLATFORM, "Enable failed due to IO or persistence.")
    }

    EnableSkillOutcome.Failed.Cancelled -> {
      failure(CliExitCodes.CANCELLED, "Enable cancelled before commitment.")
    }

    is EnableSkillOutcome.Failed.CleanupPendingCommitted -> {
      failure(CliExitCodes.CLEANUP_PENDING, "Committed enable for ${outcome.skillName}; cleanup pending.")
    }
  }

private fun renderCompletedDisable(outcome: DisableSkillOutcome.Completed): RenderedCliOutcome =
  RenderedCliOutcome(
    exitCode = if (outcome.cleanupPending) CliExitCodes.CLEANUP_PENDING else CliExitCodes.SUCCESS,
    stdout =
      "Disabled ${outcome.skillName} for " +
        "${outcome.changedAgents.joinToString { it.wireValue }}. Restart affected agents.\n",
    stderr = if (outcome.cleanupPending) "Committed disable; cleanup pending.\n" else "",
  )

private fun renderDisableFailure(outcome: DisableSkillOutcome.Failed): RenderedCliOutcome =
  when (outcome) {
    DisableSkillOutcome.Failed.InvalidArguments -> {
      failure(CliExitCodes.INVALID_ARGUMENTS, "Invalid disable arguments.")
    }

    DisableSkillOutcome.Failed.NotFound -> {
      failure(CliExitCodes.NOT_FOUND, "Managed skill not found.")
    }

    DisableSkillOutcome.Failed.DestinationConflict -> {
      failure(CliExitCodes.CONFLICT, "Agent destination conflict.")
    }

    DisableSkillOutcome.Failed.WriterBusy -> {
      failure(CliExitCodes.CONFLICT, "Another SkillLink writer is active.")
    }

    DisableSkillOutcome.Failed.BlockedRecovery -> {
      failure(CliExitCodes.CONFLICT, "Recovery is blocked.")
    }

    DisableSkillOutcome.Failed.IoFailure -> {
      failure(CliExitCodes.IO_OR_PLATFORM, "Disable failed due to IO or persistence.")
    }

    DisableSkillOutcome.Failed.Cancelled -> {
      failure(CliExitCodes.CANCELLED, "Disable cancelled before commitment.")
    }

    is DisableSkillOutcome.Failed.CleanupPendingCommitted -> {
      failure(CliExitCodes.CLEANUP_PENDING, "Committed disable for ${outcome.skillName}; cleanup pending.")
    }
  }

private fun renderCompletedRemove(outcome: RemoveSkillOutcome.Completed): RenderedCliOutcome =
  RenderedCliOutcome(
    exitCode = if (outcome.cleanupPending) CliExitCodes.CLEANUP_PENDING else CliExitCodes.SUCCESS,
    stdout = "Removed ${outcome.skillName} to ${outcome.trashPath}. Restart affected agents.\n",
    stderr = if (outcome.cleanupPending) "Committed removal; cleanup pending.\n" else "",
  )

private fun renderRemoveFailure(outcome: RemoveSkillOutcome.Failed): RenderedCliOutcome =
  when (outcome) {
    RemoveSkillOutcome.Failed.InvalidArguments -> {
      failure(CliExitCodes.INVALID_ARGUMENTS, "Invalid remove arguments.")
    }

    RemoveSkillOutcome.Failed.NotFound -> {
      failure(CliExitCodes.NOT_FOUND, "Managed skill not found.")
    }

    RemoveSkillOutcome.Failed.IntegrityFailure -> {
      failure(CliExitCodes.IO_OR_PLATFORM, "Canonical skill content is missing or invalid.")
    }

    RemoveSkillOutcome.Failed.DestinationConflict -> {
      failure(CliExitCodes.CONFLICT, "Agent destination conflict.")
    }

    RemoveSkillOutcome.Failed.WriterBusy -> {
      failure(CliExitCodes.CONFLICT, "Another SkillLink writer is active.")
    }

    RemoveSkillOutcome.Failed.BlockedRecovery -> {
      failure(CliExitCodes.CONFLICT, "Recovery is blocked.")
    }

    RemoveSkillOutcome.Failed.IoFailure -> {
      failure(CliExitCodes.IO_OR_PLATFORM, "Remove failed due to IO or persistence.")
    }

    RemoveSkillOutcome.Failed.Cancelled -> {
      failure(CliExitCodes.CANCELLED, "Remove cancelled before commitment.")
    }

    is RemoveSkillOutcome.Failed.CleanupPendingCommitted -> {
      failure(
        CliExitCodes.CLEANUP_PENDING,
        "Committed removal for ${outcome.skillName}; cleanup pending." +
          (outcome.trashPath?.let { " Retained at $it." } ?: ""),
      )
    }
  }

private fun failure(
  code: Int,
  message: String,
): RenderedCliOutcome = RenderedCliOutcome(exitCode = code, stdout = "", stderr = "$message\n")

private fun renderParseMessage(failure: CliParseOutcome.Failed): String =
  when (failure) {
    CliParseOutcome.Failed.InvalidArguments -> "Invalid arguments.\n"
    CliParseOutcome.Failed.UnknownAgent -> "Unknown agent.\n"
    CliParseOutcome.Failed.UnknownOption -> "Unknown option.\n"
    CliParseOutcome.Failed.UnsupportedCommand -> "Command is not available in this release.\n"
  }
