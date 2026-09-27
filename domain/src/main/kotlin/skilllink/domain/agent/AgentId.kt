package skilllink.domain.agent

enum class AgentId(
    val wireValue: String,
) {
    Claude("claude"),
    Codex("codex"),
    Junie("junie"),
    Cursor("cursor"),
    ;

    companion object {
        fun fromWire(value: String): AgentId? = entries.firstOrNull { it.wireValue == value }
    }
}
