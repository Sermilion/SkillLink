package skilllink.cli.parse

import skilllink.domain.agent.AgentId
import java.nio.file.Path

sealed interface CliCommand {
    data object Help : CliCommand

    data object Version : CliCommand

    data object List : CliCommand

    data class Install(
        val skillFile: Path,
        val agents: Set<AgentId>,
    ) : CliCommand
}

sealed interface CliParseOutcome {
    data class Parsed(val command: CliCommand) : CliParseOutcome

    sealed interface Failed : CliParseOutcome {
        data object InvalidArguments : Failed

        data object UnknownOption : Failed

        data object UnknownAgent : Failed

        data object UnsupportedCommand : Failed
    }
}
