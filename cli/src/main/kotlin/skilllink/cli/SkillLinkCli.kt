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
                        try {
                            CliRenderer.renderInstall(
                                installOperationProvider().execute(
                                    InstallSkillRequest.NewImport(
                                        skillFile = command.skillFile,
                                        agents = command.agents,
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
                }
            }
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
}
