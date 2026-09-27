package skilllink.cli

import skilllink.application.installation.InstallSkillOperation
import skilllink.application.installation.ManageSkillsOperation
import skilllink.application.installation.model.DisableSkillOutcome
import skilllink.application.installation.model.EnableSkillOutcome
import skilllink.application.installation.model.InstallSkillOutcome
import skilllink.application.installation.model.InstallSkillRequest
import skilllink.application.installation.model.ListSkillsOutcome
import skilllink.application.installation.model.RemoveSkillOutcome
import skilllink.application.installation.model.SkillManagementRequest
import skilllink.application.library.ListManagedSkillsOperation
import skilllink.application.library.OpenManagedSkillOperation
import skilllink.application.library.OpenManagedSkillOutcome
import skilllink.application.library.OpenManagedSkillRequest
import skilllink.cli.parse.CliArgumentParser
import skilllink.cli.parse.CliCommand
import skilllink.cli.parse.CliParseOutcome
import skilllink.cli.render.CliRenderer
import skilllink.cli.render.RenderedCliOutcome
import skilllink.domain.agent.AgentId
import skilllink.domain.library.SkillNamePolicy
import java.nio.file.Path

class SkillLinkCli(
  private val installOperationProvider: () -> InstallSkillOperation,
  private val listOperationProvider: () -> ListManagedSkillsOperation,
  private val manageOperationProvider: () -> ManageSkillsOperation,
  private val openOperationProvider: () -> OpenManagedSkillOperation,
  private val workingDirectory: Path = Path.of("").toAbsolutePath(),
) {
  fun run(args: Array<String>): RenderedCliOutcome =
    when (val parsed = CliArgumentParser.parse(args, workingDirectory)) {
      is CliParseOutcome.Failed -> {
        CliRenderer.renderParseFailure(parsed)
      }

      is CliParseOutcome.Parsed -> {
        when (val command = parsed.command) {
          CliCommand.Help -> {
            CliRenderer.renderHelp()
          }

          CliCommand.Version -> {
            CliRenderer.renderVersion()
          }

          CliCommand.List -> {
            try {
              CliRenderer.renderList(listOperationProvider().execute())
            } catch (_: Exception) {
              CliRenderer.renderList(ListSkillsOutcome.Failed.StorageInvalid)
            }
          }

          is CliCommand.Install -> {
            val agents =
              command.agents.ifEmpty {
                promptForAgents() ?: return CliRenderer.renderParseFailure(
                  CliParseOutcome.Failed.InvalidArguments,
                )
              }
            try {
              CliRenderer.renderInstall(
                installOperationProvider().execute(
                  InstallSkillRequest.NewImport(
                    skillFile = command.skillFile,
                    agents = agents,
                    removeOriginal = command.removeOriginal,
                  ),
                ),
              )
            } catch (_: Exception) {
              CliRenderer.renderInstall(InstallSkillOutcome.Failed.IoFailure)
            }
          }

          is CliCommand.Enable -> {
            executeManagement(command.skillName, command.agents, ManagementAction.Enable)
          }

          is CliCommand.Disable -> {
            executeManagement(command.skillName, command.agents, ManagementAction.Disable)
          }

          is CliCommand.Remove -> {
            executeManagement(command.skillName, emptySet(), ManagementAction.Remove)
          }

          is CliCommand.Open -> {
            executeOpen(command.skillName)
          }
        }
      }
    }

  private fun promptForAgents(): Set<AgentId>? {
    val supported = AgentId.entries
    println("Select agents to install for (comma-separated):")
    supported.forEachIndexed { index, agent ->
      println("  ${index + 1}. ${agent.wireValue}")
    }
    println("  ${supported.size + 1}. all")
    print("▸ Agents: ")
    val input =
      try {
        readlnOrNull()?.trim().orEmpty()
      } catch (_: Exception) {
        ""
      }
    if (input.isEmpty()) return null
    return parseAgentInput(input, supported)
  }

  private fun parseAgentInput(
    input: String,
    supported: List<AgentId>,
  ): Set<AgentId>? {
    val allAgents = supported.toSet()
    val selected = linkedSetOf<AgentId>()
    var failed = false
    for (token in input.split(",")) {
      val trimmed = token.trim()
      if (trimmed.isEmpty()) continue
      val resolved = resolveAgentToken(trimmed, trimmed.toIntOrNull(), supported)
      if (resolved == null) {
        System.err.println("Unknown agent: $trimmed")
        failed = true
      } else if (resolved.size == allAgents.size) {
        selected.addAll(allAgents)
      } else {
        selected.addAll(resolved)
      }
    }
    if (failed) return null
    return selected.ifEmpty { null }
  }

  private fun resolveAgentToken(
    token: String,
    asNumber: Int?,
    supported: List<AgentId>,
  ): Set<AgentId>? =
    when {
      asNumber == supported.size + 1 -> supported.toSet()
      asNumber != null && asNumber in 1..supported.size -> setOf(supported[asNumber - 1])
      token.equals("all", ignoreCase = true) -> supported.toSet()
      else -> AgentId.fromWire(token.lowercase())?.let { setOf(it) }
    }

  private enum class ManagementAction {
    Enable,
    Disable,
    Remove,
  }

  private fun executeManagement(
    skillName: String,
    agents: Set<AgentId>,
    action: ManagementAction,
  ): RenderedCliOutcome {
    val key = SkillNamePolicy.comparisonKeyForLookup(skillName)
    if (key == null) {
      return when (action) {
        ManagementAction.Enable -> CliRenderer.renderEnable(EnableSkillOutcome.Failed.InvalidArguments)
        ManagementAction.Disable -> CliRenderer.renderDisable(DisableSkillOutcome.Failed.InvalidArguments)
        ManagementAction.Remove -> CliRenderer.renderRemove(RemoveSkillOutcome.Failed.InvalidArguments)
      }
    }
    val request = SkillManagementRequest(comparisonKey = key, explicitAgents = agents)
    return try {
      when (action) {
        ManagementAction.Enable -> CliRenderer.renderEnable(manageOperationProvider().enable(request))
        ManagementAction.Disable -> CliRenderer.renderDisable(manageOperationProvider().disable(request))
        ManagementAction.Remove -> CliRenderer.renderRemove(manageOperationProvider().remove(request))
      }
    } catch (_: Exception) {
      when (action) {
        ManagementAction.Enable -> CliRenderer.renderEnable(EnableSkillOutcome.Failed.IoFailure)
        ManagementAction.Disable -> CliRenderer.renderDisable(DisableSkillOutcome.Failed.IoFailure)
        ManagementAction.Remove -> CliRenderer.renderRemove(RemoveSkillOutcome.Failed.IoFailure)
      }
    }
  }

  private fun executeOpen(skillName: String): RenderedCliOutcome {
    val key =
      SkillNamePolicy.comparisonKeyForLookup(skillName)
        ?: return CliRenderer.renderOpen(OpenManagedSkillOutcome.Failed.InvalidArguments)
    return try {
      CliRenderer.renderOpen(openOperationProvider().execute(OpenManagedSkillRequest(key)))
    } catch (_: Exception) {
      CliRenderer.renderOpen(OpenManagedSkillOutcome.Failed.IoFailure)
    }
  }
}
