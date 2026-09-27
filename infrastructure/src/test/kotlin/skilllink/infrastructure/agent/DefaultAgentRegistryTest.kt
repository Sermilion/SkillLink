package skilllink.infrastructure.agent

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import skilllink.domain.agent.AgentId
import java.nio.file.Path

class DefaultAgentRegistryTest {
  @Test
  fun resolvesDocumentedAgentRoots() {
    val home = Path.of("/home/tester")
    val registry = DefaultAgentRegistry(home, emptyMap())
    assertEquals(
      setOf(
        home.resolve(".claude/skills"),
        home.resolve(".agents/skills"),
        home.resolve(".junie/skills"),
        home.resolve(".cursor/skills"),
      ),
      registry.allAgentRoots(),
    )
    assertEquals(home.resolve(".cursor/skills"), registry.destinationFor(AgentId.Cursor).root)
    assertTrue(registry.destinationFor(AgentId.Claude).reservedNames.contains("synced"))
  }

  @Test
  fun honorsSupportedClaudeConfigOverride() {
    val override = Path.of("/custom/claude")
    val registry = DefaultAgentRegistry(Path.of("/home/tester"), mapOf("CLAUDE_CONFIG_DIR" to override.toString()))

    assertEquals(override.resolve("skills"), registry.destinationFor(AgentId.Claude).root)
  }
}
