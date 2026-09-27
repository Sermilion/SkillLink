package skilllink.cli.parse

import skilllink.domain.agent.AgentId
import java.nio.file.Path

object CliArgumentParser {
    fun parse(
        args: Array<String>,
        workingDirectory: Path,
    ): CliParseOutcome {
        val tokens = if (args.isEmpty()) emptyList() else tokenize(args)
        return when {
            tokens.isEmpty() -> CliParseOutcome.Parsed(CliCommand.Help)
            else -> parseCommand(tokens, workingDirectory)
        }
    }

    private fun parseCommand(
        tokens: List<String>,
        workingDirectory: Path,
    ): CliParseOutcome =
        when (tokens[0]) {
            "--help", "-h", "help" -> CliParseOutcome.Parsed(CliCommand.Help)
            "--version", "-V", "version" -> CliParseOutcome.Parsed(CliCommand.Version)
            "list" -> CliParseOutcome.Parsed(CliCommand.List)
            "install" -> parseInstall(tokens, workingDirectory)
            "enable", "disable" -> parseManagement(tokens, allowAgents = true)
            "remove" -> parseManagement(tokens, allowAgents = false)
            else -> CliParseOutcome.Failed.InvalidArguments
        }

    private fun parseInstall(
        tokens: List<String>,
        workingDirectory: Path,
    ): CliParseOutcome =
        when {
            tokens.size < 2 -> {
                CliParseOutcome.Failed.InvalidArguments
            }

            workingDirectory
                .resolve(tokens[1])
                .normalize()
                .fileName
                ?.toString() != "SKILL.md" -> {
                CliParseOutcome.Failed.InvalidArguments
            }

            else -> {
                val removeOriginal = tokens.contains("--remove-original")
                val agentTokens =
                    if (removeOriginal) tokens.filterNot { it == "--remove-original" } else tokens
                val agents = parseAgents(agentTokens, startIndex = 2, allowAgents = true)
                when (agents) {
                    is AgentParseOutcome.Failed -> {
                        agents.failure
                    }

                    is AgentParseOutcome.Parsed -> {
                        CliParseOutcome.Parsed(
                            CliCommand.Install(
                                workingDirectory.resolve(tokens[1]).normalize(),
                                agents.agents,
                                removeOriginal,
                            ),
                        )
                    }
                }
            }
        }

    private fun parseManagement(
        tokens: List<String>,
        allowAgents: Boolean,
    ): CliParseOutcome =
        when {
            tokens.size < 2 -> {
                CliParseOutcome.Failed.InvalidArguments
            }

            else -> {
                when (val parsedAgents = parseAgents(tokens, 2, allowAgents)) {
                    is AgentParseOutcome.Failed -> {
                        parsedAgents.failure
                    }

                    is AgentParseOutcome.Parsed -> {
                        when (tokens[0]) {
                            "enable" -> CliParseOutcome.Parsed(CliCommand.Enable(tokens[1], parsedAgents.agents))
                            "disable" -> CliParseOutcome.Parsed(CliCommand.Disable(tokens[1], parsedAgents.agents))
                            "remove" -> CliParseOutcome.Parsed(CliCommand.Remove(tokens[1]))
                            else -> CliParseOutcome.Failed.InvalidArguments
                        }
                    }
                }
            }
        }

    private fun parseAgents(
        tokens: List<String>,
        startIndex: Int,
        allowAgents: Boolean,
    ): AgentParseOutcome {
        val agents = linkedSetOf<AgentId>()
        var index = startIndex
        var failure: CliParseOutcome.Failed? = null
        while (index < tokens.size && failure == null) {
            if (tokens[index] != "--agent") {
                failure = CliParseOutcome.Failed.UnknownOption
            } else if (!allowAgents || index + 1 >= tokens.size) {
                failure = CliParseOutcome.Failed.InvalidArguments
            } else {
                val agent = AgentId.fromWire(tokens[index + 1])
                if (agent == null) {
                    failure = CliParseOutcome.Failed.UnknownAgent
                } else {
                    agents.add(agent)
                    index += 2
                }
            }
        }
        return failure?.let(AgentParseOutcome::Failed) ?: AgentParseOutcome.Parsed(agents)
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

private sealed interface AgentParseOutcome {
    data class Parsed(
        val agents: Set<AgentId>,
    ) : AgentParseOutcome

    data class Failed(
        val failure: CliParseOutcome.Failed,
    ) : AgentParseOutcome
}
