package skilllink.application.ports

import skilllink.domain.agent.AgentId
import java.nio.file.Path

data class AgentDestination(
    val agent: AgentId,
    val root: Path,
    val reservedNames: Set<String>,
)

sealed interface LinkCapability {
    data object DirectSymlink : LinkCapability

    data object Unavailable : LinkCapability
}

interface AgentRegistryPort {
    fun destinationFor(agent: AgentId): AgentDestination

    fun linkCapability(): LinkCapability

    fun allAgentRoots(): Set<Path>
}
