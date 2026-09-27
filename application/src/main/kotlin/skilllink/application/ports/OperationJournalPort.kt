package skilllink.application.ports

import skilllink.domain.agent.AgentId
import skilllink.domain.library.SkillId
import java.nio.file.Path

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
    val phase: OperationPhase,
    val stagingPath: Path?,
    val canonicalPath: Path?,
    val stagingIdentity: String?,
    val canonicalIdentity: String?,
    val sourceRoot: Path?,
    val sourceFingerprint: String?,
    val agentDestinations: Map<AgentId, Path>,
)

interface OperationJournalPort {
    fun beginInstall(
        operationId: String,
        skillId: SkillId,
        comparisonKey: String,
        sourceRoot: Path,
        sourceFingerprint: String,
        agents: Map<AgentId, Path>,
    )

    fun updatePhase(operationId: String, phase: OperationPhase, paths: OperationPaths)

    fun findIncomplete(): List<OperationRecord>

    fun markRolledBack(operationId: String)

    fun markCommitted(operationId: String)

    fun markCompleted(operationId: String)
}

data class OperationPaths(
    val stagingPath: Path? = null,
    val canonicalPath: Path? = null,
    val stagingIdentity: String? = null,
    val canonicalIdentity: String? = null,
)
