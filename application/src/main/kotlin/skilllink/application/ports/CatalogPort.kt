package skilllink.application.ports

import skilllink.application.installation.model.DesiredInstallationState
import skilllink.application.installation.model.ManagedSkillSnapshot
import skilllink.application.installation.model.ObservedLinkCondition
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

    fun updateObservedCondition(
        skillId: SkillId,
        agent: AgentId,
        observed: ObservedLinkCondition,
    )

    fun recordInstallationIntent(
        skillId: SkillId,
        agent: AgentId,
        destination: Path,
        desired: DesiredInstallationState,
    )
}
