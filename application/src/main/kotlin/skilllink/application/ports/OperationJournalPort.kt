package skilllink.application.ports

import skilllink.domain.agent.AgentId
import skilllink.domain.library.SkillId
import java.nio.file.Path

enum class OperationKind {
    Install,
    Enable,
    Disable,
    Remove,
}

enum class OperationPhase {
    Staging,
    Published,
    Linked,
    Committed,
    CleanupPending,
    Completed,
    RolledBack,
}

data class OperationRecord(
    val operationId: String,
    val skillId: SkillId,
    val comparisonKey: String,
    val kind: OperationKind,
    val phase: OperationPhase,
    val stagingPath: Path?,
    val canonicalPath: Path?,
    val stagingIdentity: String?,
    val canonicalIdentity: String?,
    val sourceRoot: Path?,
    val sourceFingerprint: String?,
    val agentDestinations: Map<AgentId, Path>,
    val managementSnapshot: String?,
    val trashPath: Path?,
)

interface OperationJournalPort {
    fun beginInstall(start: InstallOperationStart)

    fun updatePhase(
        operationId: String,
        phase: OperationPhase,
        paths: OperationPaths,
    )

    fun findIncomplete(): List<OperationRecord>

    fun markRolledBack(operationId: String)

    fun markCommitted(operationId: String)

    fun markCompleted(operationId: String)

    fun beginManagement(start: ManagementOperationStart)
}

data class InstallOperationStart(
    val operationId: String,
    val skillId: SkillId,
    val comparisonKey: String,
    val sourceRoot: Path,
    val sourceFingerprint: String,
    val agents: Map<AgentId, Path>,
)

data class ManagementOperationStart(
    val operationId: String,
    val kind: OperationKind,
    val skillId: SkillId,
    val comparisonKey: String,
    val canonicalPath: Path,
    val agentDestinations: Map<AgentId, Path>,
    val managementSnapshot: String,
)

data class OperationPaths(
    val stagingPath: Path? = null,
    val canonicalPath: Path? = null,
    val stagingIdentity: String? = null,
    val canonicalIdentity: String? = null,
    val trashPath: Path? = null,
)
