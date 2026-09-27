package skilllink.application.installation.model

import skilllink.domain.agent.AgentId
import skilllink.domain.library.NameComparisonKey
import java.nio.file.Path

data class SkillManagementRequest(
    val comparisonKey: NameComparisonKey,
    val explicitAgents: Set<AgentId>,
)

sealed interface EnableSkillOutcome {
    data class Completed(
        val skillName: String,
        val canonicalPath: Path,
        val changedAgents: Set<AgentId>,
        val cleanupPending: Boolean,
    ) : EnableSkillOutcome

    data object NoOp : EnableSkillOutcome

    sealed interface Failed : EnableSkillOutcome {
        data object InvalidArguments : Failed

        data object NotFound : Failed

        data object IntegrityFailure : Failed

        data object DestinationConflict : Failed

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

sealed interface DisableSkillOutcome {
    data class Completed(
        val skillName: String,
        val changedAgents: Set<AgentId>,
        val cleanupPending: Boolean,
    ) : DisableSkillOutcome

    data object NoOp : DisableSkillOutcome

    sealed interface Failed : DisableSkillOutcome {
        data object InvalidArguments : Failed

        data object NotFound : Failed

        data object DestinationConflict : Failed

        data object WriterBusy : Failed

        data object BlockedRecovery : Failed

        data object IoFailure : Failed

        data object Cancelled : Failed

        data class CleanupPendingCommitted(
            val skillName: String,
        ) : Failed
    }
}

sealed interface RemoveSkillOutcome {
    data class Completed(
        val skillName: String,
        val trashPath: Path,
        val formerAgents: Set<AgentId>,
        val cleanupPending: Boolean,
    ) : RemoveSkillOutcome

    sealed interface Failed : RemoveSkillOutcome {
        data object InvalidArguments : Failed

        data object NotFound : Failed

        data object IntegrityFailure : Failed

        data object DestinationConflict : Failed

        data object WriterBusy : Failed

        data object BlockedRecovery : Failed

        data object IoFailure : Failed

        data object Cancelled : Failed

        data class CleanupPendingCommitted(
            val skillName: String,
            val trashPath: Path?,
        ) : Failed
    }
}
