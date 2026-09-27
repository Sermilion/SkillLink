package skilllink.application.library

import skilllink.application.installation.model.ManagedSkillSnapshot
import skilllink.application.ports.CanonicalValidationOutcome
import skilllink.application.ports.CatalogPort
import skilllink.application.ports.DefaultFileOpenOutcome
import skilllink.application.ports.DefaultFileOpenerPort
import skilllink.application.ports.FilesystemPort
import skilllink.application.ports.LibraryLayoutPort
import skilllink.application.ports.WriterLockOutcome
import skilllink.application.ports.WriterLockPort
import skilllink.domain.library.NameComparisonKey
import skilllink.domain.library.SkillId
import java.nio.file.Path

data class OpenManagedSkillRequest(
  val comparisonKey: NameComparisonKey,
)

sealed interface OpenManagedSkillOutcome {
  data class Opened(
    val skillName: String,
    val skillFile: Path,
  ) : OpenManagedSkillOutcome

  sealed interface Failed : OpenManagedSkillOutcome {
    data object InvalidArguments : Failed

    data object NotFound : Failed

    data object IntegrityFailure : Failed

    data object WriterBusy : Failed

    data object Unavailable : Failed

    data object IoFailure : Failed
  }
}

class OpenManagedSkillOperation(
  private val layout: LibraryLayoutPort,
  private val catalog: CatalogPort,
  private val filesystem: FilesystemPort,
  private val opener: DefaultFileOpenerPort,
  private val writerLock: WriterLockPort,
) {
  fun execute(request: OpenManagedSkillRequest): OpenManagedSkillOutcome =
    try {
      if (!layout.isInitialized()) return OpenManagedSkillOutcome.Failed.NotFound
      when (val lock = writerLock.tryAcquire()) {
        is WriterLockOutcome.Busy -> OpenManagedSkillOutcome.Failed.WriterBusy
        is WriterLockOutcome.IoFailure -> OpenManagedSkillOutcome.Failed.IoFailure
        is WriterLockOutcome.Acquired -> lock.handle.use { openManagedSkill(request) }
      }
    } catch (_: Exception) {
      OpenManagedSkillOutcome.Failed.IoFailure
    }

  private fun openManagedSkill(request: OpenManagedSkillRequest): OpenManagedSkillOutcome =
    catalog
      .findActiveByNameKey(request.comparisonKey)
      ?.let(::openSkill)
      ?: OpenManagedSkillOutcome.Failed.NotFound

  private fun openSkill(skillId: SkillId): OpenManagedSkillOutcome =
    catalog
      .findActiveSnapshot(skillId)
      ?.let(::validateAndOpen)
      ?: OpenManagedSkillOutcome.Failed.NotFound

  private fun validateAndOpen(skill: ManagedSkillSnapshot): OpenManagedSkillOutcome =
    when (filesystem.validateCanonicalBundle(skill.canonicalPath)) {
      CanonicalValidationOutcome.Invalid.Missing,
      CanonicalValidationOutcome.Invalid.Corrupt,
      -> {
        OpenManagedSkillOutcome.Failed.IntegrityFailure
      }

      CanonicalValidationOutcome.Valid -> {
        when (opener.open(skill.canonicalPath.resolve("SKILL.md"))) {
          DefaultFileOpenOutcome.Opened -> {
            OpenManagedSkillOutcome.Opened(
              skillName = skill.displayName,
              skillFile = skill.canonicalPath.resolve("SKILL.md"),
            )
          }

          DefaultFileOpenOutcome.Unavailable -> {
            OpenManagedSkillOutcome.Failed.Unavailable
          }

          DefaultFileOpenOutcome.Failed -> {
            OpenManagedSkillOutcome.Failed.IoFailure
          }
        }
      }
    }
}
