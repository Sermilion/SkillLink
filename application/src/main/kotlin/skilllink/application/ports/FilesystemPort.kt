package skilllink.application.ports

import skilllink.domain.agent.AgentId
import java.nio.file.Path

sealed interface CopyBundleOutcome {
    data class Copied(val stagingRoot: Path) : CopyBundleOutcome

    sealed interface Failed : CopyBundleOutcome {
        data object SourceChanged : Failed

        data object Overlap : Failed

        data object IoFailure : Failed

        data object UnsupportedEntry : Failed
    }
}

sealed interface PublishOutcome {
    data class Published(val canonicalPath: Path) : PublishOutcome

    sealed interface Failed : PublishOutcome {
        data object NameOccupied : Failed

        data object IoFailure : Failed
    }
}

sealed interface LinkOutcome {
    data class Linked(val destination: Path) : LinkOutcome

    sealed interface Failed : LinkOutcome {
        data object Occupied : Failed

        data object CapabilityUnavailable : Failed

        data object IoFailure : Failed
    }
}

sealed interface SourceInspectionOutcome {
    data class Valid(
        val bundleRoot: Path,
        val displayName: String,
        val comparisonKey: String,
        val sourceFingerprint: String,
    ) : SourceInspectionOutcome

    sealed interface Invalid : SourceInspectionOutcome {
        data object NotSkillFile : Invalid

        data object Frontmatter : Invalid

        data object NamePolicy : Invalid

        data object BundleRoot : Invalid

        data object UnsupportedEntry : Invalid
    }
}

interface FilesystemPort {
    fun inspectSource(skillFile: Path): SourceInspectionOutcome

    fun sourceFingerprint(sourceRoot: Path): String

    fun copyBundleToStaging(
        bundleRoot: Path,
        stagingRoot: Path,
    ): CopyBundleOutcome

    fun publishCanonical(
        stagingRoot: Path,
        skillsRoot: Path,
        comparisonKey: String,
    ): PublishOutcome

    fun createOwnedLink(
        canonicalPath: Path,
        destination: Path,
        agent: AgentId,
    ): LinkOutcome

    fun removePathIfOwned(path: Path, expectedTarget: Path): Boolean

    fun removePathIfOwned(
        path: Path,
        expectedTarget: Path,
        expectedIdentity: String?,
    ): Boolean = removePathIfOwned(path, expectedTarget)

    fun pathIdentity(path: Path): String? = null

    fun observeLink(destination: Path, canonicalPath: Path): skilllink.application.installation.model.ObservedLinkCondition

    fun safeRemoveOriginal(sourceRoot: Path, fingerprint: String): SafeRemovalOutcome
}

sealed interface SafeRemovalOutcome {
    data object Removed : SafeRemovalOutcome

    data object Unchanged : SafeRemovalOutcome

    data object Blocked : SafeRemovalOutcome
}
