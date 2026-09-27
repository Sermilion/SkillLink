package skilllink.application.ports

import skilllink.application.installation.model.ObservedLinkCondition
import skilllink.domain.agent.AgentId
import java.nio.file.Path

sealed interface CopyBundleOutcome {
  data class Copied(
    val stagingRoot: Path,
  ) : CopyBundleOutcome

  sealed interface Failed : CopyBundleOutcome {
    data object SourceChanged : Failed

    data object Overlap : Failed

    data object IoFailure : Failed

    data object UnsupportedEntry : Failed
  }
}

sealed interface PublishOutcome {
  data class Published(
    val canonicalPath: Path,
  ) : PublishOutcome

  sealed interface Failed : PublishOutcome {
    data object NameOccupied : Failed

    data object IoFailure : Failed
  }
}

sealed interface LinkOutcome {
  data class Linked(
    val destination: Path,
  ) : LinkOutcome

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

  fun removePathIfOwned(
    path: Path,
    expectedTarget: Path,
    expectedIdentity: String?,
  ): Boolean

  fun pathIdentity(path: Path): String? = null

  fun observeLink(
    destination: Path,
    canonicalPath: Path,
  ): ObservedLinkCondition

  fun safeRemoveOriginal(
    sourceRoot: Path,
    fingerprint: String,
  ): SafeRemovalOutcome

  fun validateCanonicalBundle(canonicalPath: Path): CanonicalValidationOutcome

  fun moveCanonicalToTrash(
    canonicalPath: Path,
    trashDestination: Path,
  ): MoveToTrashOutcome
}

sealed interface CanonicalValidationOutcome {
  data object Valid : CanonicalValidationOutcome

  sealed interface Invalid : CanonicalValidationOutcome {
    data object Missing : Invalid

    data object Corrupt : Invalid
  }
}

sealed interface MoveToTrashOutcome {
  data class Moved(
    val trashPath: Path,
  ) : MoveToTrashOutcome

  sealed interface Failed : MoveToTrashOutcome {
    data object Occupied : Failed

    data object IoFailure : Failed
  }
}

sealed interface SafeRemovalOutcome {
  data object Removed : SafeRemovalOutcome

  data object Unchanged : SafeRemovalOutcome

  data object Blocked : SafeRemovalOutcome
}
