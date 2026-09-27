package skilllink.domain.source

enum class BundleEntryKind {
    RegularFile,
    Directory,
    Unsupported,
}

sealed interface BundleValidationOutcome {
    data object Accepted : BundleValidationOutcome

    sealed interface Rejected : BundleValidationOutcome {
        data object UnsupportedEntry : Rejected

        data object UnsafeRoot : Rejected

        data object RepositoryRoot : Rejected

        data object InsideSkillLinkData : Rejected

        data object InsideAgentRoot : Rejected
    }
}

object BundleRootPolicy {
    fun validateRoot(
        bundleRoot: String,
        skillLinkDataRoot: String,
        agentRoots: Set<String>,
    ): BundleValidationOutcome {
        val normalized = bundleRoot.replace('\\', '/').trimEnd('/')
        if (normalized.isEmpty() || normalized == "/" || normalized.matches(Regex("^[A-Za-z]:/?$"))) {
            return BundleValidationOutcome.Rejected.UnsafeRoot
        }
        val data = skillLinkDataRoot.replace('\\', '/').trimEnd('/')
        val home = System.getProperty("user.home")?.replace('\\', '/')?.trimEnd('/') ?: ""
        val configuredHome = data.substringBeforeLast('/', missingDelimiterValue = "")
        if (
            (home.isNotEmpty() && normalized == home) ||
            (configuredHome.isNotEmpty() && normalized == configuredHome)
        ) {
            return BundleValidationOutcome.Rejected.UnsafeRoot
        }
        if (normalized.contains("/.git") || normalized.endsWith("/.git")) {
            return BundleValidationOutcome.Rejected.RepositoryRoot
        }
        if (data.isNotEmpty() && (normalized == data || normalized.startsWith("$data/"))) {
            return BundleValidationOutcome.Rejected.InsideSkillLinkData
        }
        for (agentRoot in agentRoots) {
            val agent = agentRoot.replace('\\', '/').trimEnd('/')
            if (agent.isNotEmpty() && (normalized == agent || normalized.startsWith("$agent/"))) {
                return BundleValidationOutcome.Rejected.InsideAgentRoot
            }
        }
        return BundleValidationOutcome.Accepted
    }
}
