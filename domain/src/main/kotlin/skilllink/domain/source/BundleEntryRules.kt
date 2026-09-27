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
        val data = skillLinkDataRoot.replace('\\', '/').trimEnd('/')
        val home = System.getProperty("user.home")?.replace('\\', '/')?.trimEnd('/') ?: ""
        val configuredHome = data.substringBeforeLast('/', missingDelimiterValue = "")
        return when {
            isUnsafeRoot(normalized) || normalized == home || normalized == configuredHome -> {
                BundleValidationOutcome.Rejected.UnsafeRoot
            }

            normalized.contains("/.git") || normalized.endsWith("/.git") -> {
                BundleValidationOutcome.Rejected.RepositoryRoot
            }

            isInside(normalized, data) -> {
                BundleValidationOutcome.Rejected.InsideSkillLinkData
            }

            agentRoots.any { isInside(normalized, it.replace('\\', '/').trimEnd('/')) } -> {
                BundleValidationOutcome.Rejected.InsideAgentRoot
            }

            else -> {
                BundleValidationOutcome.Accepted
            }
        }
    }

    private fun isUnsafeRoot(normalized: String): Boolean =
        normalized.isEmpty() ||
            normalized == "/" ||
            normalized.matches(Regex("^[A-Za-z]:/?$"))

    private fun isInside(
        path: String,
        root: String,
    ): Boolean = root.isNotEmpty() && (path == root || path.startsWith("$root/"))
}
