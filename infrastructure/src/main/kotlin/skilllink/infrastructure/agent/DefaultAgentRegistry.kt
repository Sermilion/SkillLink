package skilllink.infrastructure.agent

import skilllink.application.ports.AgentDestination
import skilllink.application.ports.AgentRegistryPort
import skilllink.application.ports.LinkCapability
import skilllink.domain.agent.AgentId
import java.nio.file.Path

class DefaultAgentRegistry(
  private val home: Path = Path.of(System.getProperty("user.home")),
  private val environment: Map<String, String> = System.getenv(),
) : AgentRegistryPort {
  override fun destinationFor(agent: AgentId): AgentDestination {
    val root =
      when (agent) {
        AgentId.Claude -> {
          val override = environment["CLAUDE_CONFIG_DIR"]
          if (!override.isNullOrBlank()) {
            Path.of(override).resolve("skills")
          } else {
            home.resolve(".claude").resolve("skills")
          }
        }

        AgentId.Codex -> {
          val override = environment["CODEX_HOME"]
          if (!override.isNullOrBlank()) {
            Path.of(override).resolve("skills")
          } else {
            home.resolve(".agents").resolve("skills")
          }
        }

        AgentId.Junie -> {
          home.resolve(".junie").resolve("skills")
        }

        AgentId.Cursor -> {
          home.resolve(".cursor").resolve("skills")
        }
      }
    val reserved =
      when (agent) {
        AgentId.Claude -> setOf("synced")
        else -> emptySet()
      }
    return AgentDestination(agent, root, reserved)
  }

  override fun linkCapability(): LinkCapability = LinkCapabilityProbe.current()

  override fun allAgentRoots(): Set<Path> = AgentId.entries.map { destinationFor(it).root }.toSet()
}
