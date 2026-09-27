package skilllink.application.ports

import skilllink.application.installation.model.AgentInstallationSnapshot
import skilllink.application.installation.model.ManagedSkillSnapshot
import skilllink.domain.agent.AgentId
import skilllink.domain.library.NameComparisonKey
import skilllink.domain.library.SkillId
import java.nio.file.Path

interface CatalogPort {
    fun findActiveByNameKey(key: NameComparisonKey): SkillId?

    fun listActiveOrdered(): List<ManagedSkillSnapshot>

    fun reserveSkill(
        id: SkillId,
        displayName: String,
        comparisonKey: NameComparisonKey,
        canonicalPath: Path,
        agents: Map<AgentId, Path>,
    )

    fun commitOperation(operationId: String)

    fun markCleanupPending(skillId: SkillId)

    fun clearReservation(skillId: SkillId)

    fun findActiveSnapshot(skillId: SkillId): ManagedSkillSnapshot?

    fun commitManagementState(
        operationId: String,
        skillId: SkillId,
        installations: List<AgentInstallationSnapshot>,
    )

    fun commitRemoval(
        operationId: String,
        skillId: SkillId,
        trashPath: Path,
        formerAgents: Set<AgentId>,
    )
}
