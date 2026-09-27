package skilllink.application.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import skilllink.application.installation.RecoveryCoordinator
import skilllink.application.installation.model.AgentInstallationSnapshot
import skilllink.application.installation.model.ListSkillsOutcome
import skilllink.application.installation.model.ManagedSkillSnapshot
import skilllink.application.installation.model.ObservedLinkCondition
import skilllink.application.ports.CanonicalValidationOutcome
import skilllink.application.ports.CatalogPort
import skilllink.application.ports.CopyBundleOutcome
import skilllink.application.ports.DiagnosticsPort
import skilllink.application.ports.FilesystemPort
import skilllink.application.ports.InstallOperationStart
import skilllink.application.ports.LibraryLayoutPort
import skilllink.application.ports.LinkOutcome
import skilllink.application.ports.ManagementOperationStart
import skilllink.application.ports.MoveToTrashOutcome
import skilllink.application.ports.OperationJournalPort
import skilllink.application.ports.OperationPaths
import skilllink.application.ports.OperationPhase
import skilllink.application.ports.OperationRecord
import skilllink.application.ports.PublishOutcome
import skilllink.application.ports.SafeRemovalOutcome
import skilllink.application.ports.SkillLinkLayout
import skilllink.application.ports.SourceInspectionOutcome
import skilllink.application.ports.WriterLockOutcome
import skilllink.application.ports.WriterLockPort
import skilllink.domain.agent.AgentId
import skilllink.domain.library.NameComparisonKey
import skilllink.domain.library.SkillId
import java.io.Closeable
import java.nio.file.Path

class ListManagedSkillsOperationTest {
  @Test
  fun returnsEmptyWithoutInitializingAnAbsentLibrary() {
    val catalog = RecordingCatalog()
    val operation = operation(layoutInitialized = false, catalog = catalog)

    val outcome = operation.execute()

    assertEquals(ListSkillsOutcome.EmptyLibrary, outcome)
    assertEquals(0, catalog.listCalls)
  }

  @Test
  fun reportsInvalidStorageInsteadOfTurningReadFailureIntoEmpty() {
    val catalog = RecordingCatalog(throwOnList = true)
    val operation = operation(layoutInitialized = true, catalog = catalog)

    val outcome = operation.execute()

    assertEquals(ListSkillsOutcome.Failed.StorageInvalid, outcome)
  }

  private fun operation(
    layoutInitialized: Boolean,
    catalog: RecordingCatalog,
  ): ListManagedSkillsOperation {
    val journal = EmptyJournal()
    val filesystem = EmptyFilesystem()
    val recovery = RecoveryCoordinator(journal, filesystem, catalog, EmptyDiagnostics())
    return ListManagedSkillsOperation(
      FixedLayout(layoutInitialized),
      catalog,
      filesystem,
      FixedWriterLock(),
      recovery,
    )
  }
}

private val missingCanonical = CanonicalValidationOutcome.Invalid.Missing

private class FixedLayout(
  private val initialized: Boolean,
) : LibraryLayoutPort {
  override fun resolve(): SkillLinkLayout =
    SkillLinkLayout(
      Path.of("root"),
      Path.of("skills"),
      Path.of("trash"),
      Path.of("db"),
      Path.of("diagnostics"),
      Path.of("lock"),
    )

  override fun isInitialized(): Boolean = initialized
}

private class FixedWriterLock : WriterLockPort {
  override fun tryAcquire(): WriterLockOutcome =
    WriterLockOutcome.Acquired(
      object : Closeable {
        override fun close() = Unit
      },
    )
}

private class RecordingCatalog(
  private val throwOnList: Boolean = false,
) : CatalogPort {
  var listCalls = 0

  override fun findActiveByNameKey(key: NameComparisonKey): SkillId? = null

  override fun listActiveOrdered(): List<ManagedSkillSnapshot> {
    listCalls++
    if (throwOnList) {
      error("storage failure")
    }
    return emptyList()
  }

  override fun reserveSkill(
    id: SkillId,
    displayName: String,
    comparisonKey: NameComparisonKey,
    canonicalPath: Path,
    agents: Map<AgentId, Path>,
  ) = Unit

  override fun commitOperation(operationId: String) = Unit

  override fun markCleanupPending(skillId: SkillId) = Unit

  override fun clearReservation(skillId: SkillId) = Unit

  override fun findActiveSnapshot(skillId: SkillId): ManagedSkillSnapshot? = null

  override fun commitManagementState(
    operationId: String,
    skillId: SkillId,
    installations: List<AgentInstallationSnapshot>,
  ) = Unit

  override fun commitRemoval(
    operationId: String,
    skillId: SkillId,
    trashPath: Path,
    formerAgents: Set<AgentId>,
  ) = Unit
}

private class EmptyJournal : OperationJournalPort {
  override fun beginInstall(start: InstallOperationStart) = Unit

  override fun updatePhase(
    operationId: String,
    phase: OperationPhase,
    paths: OperationPaths,
  ) = Unit

  override fun findIncomplete(): List<OperationRecord> = emptyList()

  override fun markRolledBack(operationId: String) = Unit

  override fun markCommitted(operationId: String) = Unit

  override fun markCompleted(operationId: String) = Unit

  override fun beginManagement(start: ManagementOperationStart) = Unit
}

private class EmptyFilesystem : FilesystemPort {
  override fun inspectSource(skillFile: Path): SourceInspectionOutcome = SourceInspectionOutcome.Invalid.NotSkillFile

  override fun copyBundleToStaging(
    bundleRoot: Path,
    stagingRoot: Path,
  ): CopyBundleOutcome = CopyBundleOutcome.Failed.IoFailure

  override fun publishCanonical(
    stagingRoot: Path,
    skillsRoot: Path,
    comparisonKey: String,
  ): PublishOutcome = PublishOutcome.Failed.IoFailure

  override fun createOwnedLink(
    canonicalPath: Path,
    destination: Path,
    agent: AgentId,
  ): LinkOutcome = LinkOutcome.Failed.IoFailure

  override fun removePathIfOwned(
    path: Path,
    expectedTarget: Path,
    expectedIdentity: String?,
  ): Boolean = true

  override fun observeLink(
    destination: Path,
    canonicalPath: Path,
  ): ObservedLinkCondition = ObservedLinkCondition.Missing

  override fun safeRemoveOriginal(
    sourceRoot: Path,
    fingerprint: String,
  ): SafeRemovalOutcome = SafeRemovalOutcome.Unchanged

  override fun validateCanonicalBundle(canonicalPath: Path) = missingCanonical

  override fun moveCanonicalToTrash(
    canonicalPath: Path,
    trashDestination: Path,
  ): MoveToTrashOutcome = MoveToTrashOutcome.Failed.IoFailure
}

private class EmptyDiagnostics : DiagnosticsPort {
  override fun record(
    eventCode: String,
    managedId: String?,
    detail: String,
  ) = Unit

  override fun reportSecondaryFailure(
    primaryCode: String,
    secondaryCode: String,
    detail: String,
  ) = Unit
}
