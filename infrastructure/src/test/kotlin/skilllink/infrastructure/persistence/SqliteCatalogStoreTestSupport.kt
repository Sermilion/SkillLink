package skilllink.infrastructure.persistence

import skilllink.application.ports.InstallOperationStart
import skilllink.application.ports.ManagementOperationStart
import skilllink.application.ports.OperationKind
import skilllink.domain.agent.AgentId
import skilllink.domain.library.SkillId
import java.nio.file.Path

data class InstallTestInput(
  val sourceRoot: Path,
  val sourceFingerprint: String,
  val agents: Map<AgentId, Path>,
)

data class ManagementTestInput(
  val canonicalPath: Path,
  val agentDestinations: Map<AgentId, Path>,
  val managementSnapshot: String,
)

internal fun SqliteCatalogStore.beginInstall(
  operationId: String,
  skillId: SkillId,
  comparisonKey: String,
  input: InstallTestInput,
) = beginInstall(
  InstallOperationStart(
    operationId,
    skillId,
    comparisonKey,
    input.sourceRoot,
    input.sourceFingerprint,
    input.agents,
  ),
)

internal fun SqliteCatalogStore.beginManagement(
  operationId: String,
  kind: OperationKind,
  skillId: SkillId,
  comparisonKey: String,
  input: ManagementTestInput,
) = beginManagement(
  ManagementOperationStart(
    operationId,
    kind,
    skillId,
    comparisonKey,
    input.canonicalPath,
    input.agentDestinations,
    input.managementSnapshot,
  ),
)
