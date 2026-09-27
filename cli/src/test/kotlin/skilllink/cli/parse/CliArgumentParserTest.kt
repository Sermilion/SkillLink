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
  fun parsesInstallWithoutAgentsForInteractivePrompt() {
    val outcome = CliArgumentParser.parse(arrayOf("install", "x/SKILL.md"), workingDirectory)
    assertTrue(outcome is CliParseOutcome.Parsed)
    val command = (outcome as CliParseOutcome.Parsed).command as CliCommand.Install
    assertTrue(command.agents.isEmpty())
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
  fun parsesManagementByLiteralNameNotRowNumber() {
    val outcome = CliArgumentParser.parse(arrayOf("disable", "3"), workingDirectory)
    assertTrue(outcome is CliParseOutcome.Parsed)
    val command = (outcome as CliParseOutcome.Parsed).command as CliCommand.Disable
    assertEquals("3", command.skillName)
  }

  @Test
  fun parsesAllNameBasedManagementCommandsWithExplicitAndDefaultAgents() {
    val outcome =
      CliArgumentParser.parse(
        arrayOf("enable", "Code-Review", "--agent", "cursor"),
        workingDirectory,
      )
    assertTrue(outcome is CliParseOutcome.Parsed)
    val command = (outcome as CliParseOutcome.Parsed).command as CliCommand.Enable
    assertEquals("Code-Review", command.skillName)
    assertEquals(setOf(AgentId.Cursor), command.agents)

    val disable = CliArgumentParser.parse(arrayOf("disable", "Code-Review"), workingDirectory)
    assertTrue(disable is CliParseOutcome.Parsed)
    val parsedDisable = disable as CliParseOutcome.Parsed
    assertTrue(parsedDisable.command is CliCommand.Disable)
    assertEquals(emptySet<AgentId>(), (parsedDisable.command as CliCommand.Disable).agents)

    val remove = CliArgumentParser.parse(arrayOf("remove", "Code-Review"), workingDirectory)
    assertTrue(remove is CliParseOutcome.Parsed)
    val parsedRemove = remove as CliParseOutcome.Parsed
    assertTrue(parsedRemove.command is CliCommand.Remove)
    assertEquals("Code-Review", (parsedRemove.command as CliCommand.Remove).skillName)
  }

  @Test
  fun rejectsRemoveWithAgentOptions() {
    val outcome =
      CliArgumentParser.parse(
        arrayOf("remove", "demo", "--agent", "claude"),
        workingDirectory,
      )
    assertTrue(outcome is CliParseOutcome.Failed.InvalidArguments)
  }
}
