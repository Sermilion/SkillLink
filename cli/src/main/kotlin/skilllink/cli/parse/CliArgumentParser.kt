package skilllink.cli.parse

import skilllink.domain.agent.AgentId
import java.nio.file.Path

object CliArgumentParser {
    fun parse(args: Array<String>, workingDirectory: Path): CliParseOutcome {
        val tokens = if (args.isEmpty()) emptyList() else tokenize(args)
        if (tokens.isEmpty()) {
            return CliParseOutcome.Parsed(CliCommand.Help)
        }
        return when (tokens[0]) {
            "--help", "-h", "help" -> CliParseOutcome.Parsed(CliCommand.Help)
            "--version", "-V", "version" -> CliParseOutcome.Parsed(CliCommand.Version)
            "list" -> CliParseOutcome.Parsed(CliCommand.List)
            "install" -> {
                if (tokens.size < 2) {
                    return CliParseOutcome.Failed.InvalidArguments
                }
                val skillPath = workingDirectory.resolve(tokens[1]).normalize()
                if (skillPath.fileName?.toString() != "SKILL.md") {
                    return CliParseOutcome.Failed.InvalidArguments
                }
                var index = 2
                val agents = linkedSetOf<AgentId>()
                while (index < tokens.size) {
                    when (tokens[index]) {
                        "--agent" -> {
                            index++
                            if (index >= tokens.size) {
                                return CliParseOutcome.Failed.InvalidArguments
                            }
                            val agent = AgentId.fromWire(tokens[index]) ?: return CliParseOutcome.Failed.UnknownAgent
                            agents.add(agent)
                            index++
                        }
                        else -> return CliParseOutcome.Failed.UnknownOption
                    }
                }
                if (agents.isEmpty()) {
                    CliParseOutcome.Failed.InvalidArguments
                } else {
                    CliParseOutcome.Parsed(CliCommand.Install(skillPath, agents))
                }
            }
            "disable", "enable", "remove" -> CliParseOutcome.Failed.UnsupportedCommand
            else -> CliParseOutcome.Failed.InvalidArguments
        }
    }

    private fun tokenize(args: Array<String>): List<String> {
        val tokens = mutableListOf<String>()
        var index = 0
        while (index < args.size) {
            val arg = args[index]
            if (arg == "--") {
                index++
                while (index < args.size) {
                    tokens.add(args[index])
                    index++
                }
                break
            }
            if (arg.startsWith("--agent=")) {
                tokens.add("--agent")
                tokens.add(arg.removePrefix("--agent="))
            } else {
                tokens.add(arg)
            }
            index++
        }
        return tokens
    }
}
