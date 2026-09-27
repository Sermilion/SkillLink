package skilllink.cli

import skilllink.application.installation.InstallSkillOperation
import skilllink.application.installation.model.InstallSkillOutcome
import skilllink.application.installation.model.InstallSkillRequest
import skilllink.application.installation.model.ListSkillsOutcome
import skilllink.application.library.ListManagedSkillsOperation
import skilllink.cli.parse.CliArgumentParser
import skilllink.cli.parse.CliCommand
import skilllink.cli.parse.CliParseOutcome
import skilllink.cli.render.CliRenderer
import skilllink.cli.render.RenderedCliOutcome
import java.nio.file.Path

class SkillLinkCli(
    private val installOperationProvider: () -> InstallSkillOperation,
    private val listOperationProvider: () -> ListManagedSkillsOperation,
    private val workingDirectory: Path = Path.of("").toAbsolutePath(),
) {
    fun run(args: Array<String>): RenderedCliOutcome {
        return when (val parsed = CliArgumentParser.parse(args, workingDirectory)) {
            is CliParseOutcome.Failed -> CliRenderer.renderParseFailure(parsed)
            is CliParseOutcome.Parsed ->
                when (val command = parsed.command) {
                    CliCommand.Help -> CliRenderer.renderHelp()
                    CliCommand.Version -> CliRenderer.renderVersion()
                    CliCommand.List ->
                        try {
                            CliRenderer.renderList(listOperationProvider().execute())
                        } catch (_: Exception) {
                            CliRenderer.renderList(ListSkillsOutcome.Failed.StorageInvalid)
                        }
                    is CliCommand.Install ->
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
        }
    }
}
