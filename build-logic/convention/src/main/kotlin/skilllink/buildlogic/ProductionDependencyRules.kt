package skilllink.buildlogic

internal object ProductionDependencyRules {
    val allowed =
        mapOf(
            "domain" to emptySet(),
            "application" to setOf("domain"),
            "infrastructure" to setOf("application", "domain"),
            "desktop" to setOf("application", "domain"),
            "app" to setOf("desktop", "infrastructure", "application"),
        )
}
