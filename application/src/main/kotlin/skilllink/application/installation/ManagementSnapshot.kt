package skilllink.application.installation

import skilllink.application.installation.model.AgentInstallationSnapshot
import skilllink.application.installation.model.DesiredInstallationState
import skilllink.application.installation.model.ObservedLinkCondition
import skilllink.domain.agent.AgentId
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.Base64

internal object ManagementSnapshot {
    private const val ENCODED_PATH_PREFIX = "b64:"
    private const val ENTRY_PARTS = 4
    private const val DESIRED_INDEX = 1
    private const val OBSERVED_INDEX = 2
    private const val DESTINATION_INDEX = 3

    fun encode(installations: List<AgentInstallationSnapshot>): String =
        installations.joinToString("|") { installation ->
            listOf(
                installation.agent.wireValue,
                installation.desired.name,
                installation.observed.name,
                encodePath(installation.destination),
            ).joinToString(",")
        }

    fun decode(serialized: String): List<AgentInstallationSnapshot>? {
        if (serialized.isBlank()) {
            return emptyList()
        }
        val entries = serialized.split("|").map(::decodeEntry)
        return entries.takeIf { entry -> entries.none { it == null } }?.filterNotNull()
    }

    private fun decodeEntry(entry: String): AgentInstallationSnapshot? {
        val parts = entry.split(",", limit = ENTRY_PARTS)
        val agent = parts.getOrNull(0)?.let(AgentId::fromWire)
        return if (parts.size != ENTRY_PARTS || agent == null) {
            null
        } else {
            decodeInstallation(agent, parts)
        }
    }

    private fun decodeInstallation(
        agent: AgentId,
        parts: List<String>,
    ): AgentInstallationSnapshot? =
        try {
            AgentInstallationSnapshot(
                agent = agent,
                desired = DesiredInstallationState.valueOf(parts[DESIRED_INDEX]),
                observed = ObservedLinkCondition.valueOf(parts[OBSERVED_INDEX]),
                destination = Path.of(decodePath(parts[DESTINATION_INDEX])),
            )
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun encodePath(path: Path): String =
        ENCODED_PATH_PREFIX +
            Base64.getUrlEncoder().withoutPadding().encodeToString(
                path.toString().toByteArray(StandardCharsets.UTF_8),
            )

    private fun decodePath(value: String): String =
        if (!value.startsWith(ENCODED_PATH_PREFIX)) {
            value
        } else {
            String(
                Base64.getUrlDecoder().decode(value.removePrefix(ENCODED_PATH_PREFIX)),
                StandardCharsets.UTF_8,
            )
        }
}
