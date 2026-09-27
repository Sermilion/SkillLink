package skilllink.application.installation

import skilllink.application.ports.InstallOperationStart
import skilllink.application.ports.SourceInspectionOutcome
import skilllink.domain.agent.AgentId
import skilllink.domain.library.SkillId
import skilllink.domain.library.SkillNameOutcome
import java.nio.file.Path

internal fun reserveInstall(
    operation: InstallSkillOperation,
    reservation: InstallReservation,
) {
    operation.journal.beginInstall(
        InstallOperationStart(
            reservation.operationId,
            reservation.skillId,
            reservation.name.comparisonKey.value,
            reservation.inspected.bundleRoot,
            reservation.inspected.sourceFingerprint,
            reservation.destinations,
        ),
    )
    operation.catalog.reserveSkill(
        reservation.skillId,
        reservation.name.displayName,
        reservation.name.comparisonKey,
        reservation.canonicalPath,
        reservation.destinations,
    )
}

internal data class InstallReservation(
    val inspected: SourceInspectionOutcome.Valid,
    val name: SkillNameOutcome.Accepted,
    val skillId: SkillId,
    val operationId: String,
    val canonicalPath: Path,
    val destinations: Map<AgentId, Path>,
)
