package skilllink.cli.parse

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import skilllink.domain.agent.AgentId
import java.nio.file.Path

class CliArgumentParserTest {
    @TempDir
    lateinit var workingDirectory: Path

    @Test
    fun resolvesRelativeSkillPathAgainstWorkingDirectory() {
        val skill = workingDirectory.resolve("bundle/SKILL.md")
        skill.parent.toFile().mkdirs()
        skill.toFile().writeText("---\nname: demo\ndescription: d\n---\n")
        val outcome =
            CliArgumentParser.parse(
                arrayOf("install", "bundle/SKILL.md", "--agent", "claude"),
                workingDirectory,
            )
        assertTrue(outcome is CliParseOutcome.Parsed)
        val command = (outcome as CliParseOutcome.Parsed).command as CliCommand.Install
        assertEquals(skill.normalize(), command.skillFile)
    }

    @Test
    fun acceptsQuotedPathWithSpaces() {
        val spaced = workingDirectory.resolve("my skills/SKILL.md")
        spaced.parent.toFile().mkdirs()
        spaced.toFile().writeText("x")
        val outcome =
            CliArgumentParser.parse(
                arrayOf("install", "my skills/SKILL.md", "--agent", "codex"),
                workingDirectory,
            )
        assertTrue(outcome is CliParseOutcome.Parsed)
    }

    @Test
    fun deduplicatesRepeatedAgents() {
        val outcome =
            CliArgumentParser.parse(
                arrayOf("install", "x/SKILL.md", "--agent", "claude", "--agent", "claude"),
                workingDirectory,
            )
        val command = (outcome as CliParseOutcome.Parsed).command as CliCommand.Install
        assertEquals(setOf(AgentId.Claude), command.agents)
    }

    @Test
    fun rejectsMissingAgentSelection() {
        val outcome = CliArgumentParser.parse(arrayOf("install", "x/SKILL.md"), workingDirectory)
        assertTrue(outcome is CliParseOutcome.Failed.InvalidArguments)
    }

    @Test
    fun rejectsMissingSkillFileSelection() {
        val outcome = CliArgumentParser.parse(arrayOf("install", "--agent", "claude"), workingDirectory)
        assertTrue(outcome is CliParseOutcome.Failed.InvalidArguments)
    }

    @Test
    fun rejectsNonSkillMarkdownPath() {
        val outcome = CliArgumentParser.parse(arrayOf("install", "x/readme.md", "--agent", "claude"), workingDirectory)
        assertTrue(outcome is CliParseOutcome.Failed.InvalidArguments)
    }

    @Test
    fun rejectsUnknownAgent() {
        val outcome =
            CliArgumentParser.parse(
                arrayOf("install", "x/SKILL.md", "--agent", "unknown"),
                workingDirectory,
            )
        assertTrue(outcome is CliParseOutcome.Failed.UnknownAgent)
    }

    @Test
    fun rejectsUnknownOption() {
        val outcome =
            CliArgumentParser.parse(
                arrayOf("install", "x/SKILL.md", "--unexpected", "value", "--agent", "claude"),
                workingDirectory,
            )
        assertTrue(outcome is CliParseOutcome.Failed.UnknownOption)
    }

    @Test
    fun rejectsManagementCommands() {
        val outcome = CliArgumentParser.parse(arrayOf("disable", "demo"), workingDirectory)
        assertTrue(outcome is CliParseOutcome.Failed.UnsupportedCommand)
    }
}
