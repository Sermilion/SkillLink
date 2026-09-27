package skilllink.application.installation.model

import skilllink.domain.agent.AgentId
import skilllink.domain.library.NameComparisonKey
import skilllink.domain.library.SkillId
import java.nio.file.Path

enum class DesiredInstallationState {
  Enabled,
  Disabled,
}

enum class ObservedLinkCondition {
  Linked,
  Missing,
  Foreign,
  Unreadable,
}

data class AgentInstallationSnapshot(
  val agent: AgentId,
  val destination: Path,
  val desired: DesiredInstallationState,
  val observed: ObservedLinkCondition,
)

data class ManagedSkillSnapshot(
  val id: SkillId,
  val displayName: String,
  val comparisonKey: NameComparisonKey,
  val canonicalPath: Path,
  val installations: List<AgentInstallationSnapshot>,
)

sealed interface InstallSkillRequest {
  data class NewImport(
    val skillFile: Path,
    val agents: Set<AgentId>,
  ) : InstallSkillRequest
}

sealed interface InstallSkillOutcome {
  data class Completed(
    val skillName: String,
    val canonicalPath: Path,
    val agents: Set<AgentId>,
    val cleanupPending: Boolean,
  ) : InstallSkillOutcome

  sealed interface Failed : InstallSkillOutcome {
    data object InvalidArguments : Failed

    data object NameConflict : Failed

    data object DestinationConflict : Failed

    data object SourceInvalid : Failed

    data object WriterBusy : Failed

    data object BlockedRecovery : Failed

    data object PlatformCapability : Failed

    data object IoFailure : Failed

    data object Cancelled : Failed

    data class CleanupPendingCommitted(
      val skillName: String,
    ) : Failed
  }
}

sealed interface ListSkillsOutcome {
  data class Rows(
    val skills: List<ManagedSkillSnapshot>,
  ) : ListSkillsOutcome

  data object EmptyLibrary : ListSkillsOutcome

  sealed interface Failed : ListSkillsOutcome {
    data object StorageInaccessible : Failed

    data object StorageInvalid : Failed
  }
}
