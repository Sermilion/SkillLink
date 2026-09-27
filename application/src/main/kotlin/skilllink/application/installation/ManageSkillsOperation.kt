package skilllink.application.installation

import skilllink.application.installation.model.AgentInstallationSnapshot
import skilllink.application.installation.model.DesiredInstallationState
import skilllink.application.installation.model.DisableSkillOutcome
import skilllink.application.installation.model.EnableSkillOutcome
import skilllink.application.installation.model.ManagedSkillSnapshot
import skilllink.application.installation.model.ObservedLinkCondition
import skilllink.application.installation.model.RemoveSkillOutcome
import skilllink.application.installation.model.SkillManagementRequest
import skilllink.application.ports.AgentRegistryPort
import skilllink.application.ports.CanonicalValidationOutcome
import skilllink.application.ports.CatalogPort
import skilllink.application.ports.FilesystemPort
import skilllink.application.ports.LibraryLayoutPort
import skilllink.application.ports.LinkCapability
import skilllink.application.ports.LinkOutcome
import skilllink.application.ports.ManagementOperationStart
import skilllink.application.ports.MoveToTrashOutcome
import skilllink.application.ports.OperationJournalPort
import skilllink.application.ports.OperationKind
import skilllink.application.ports.OperationPaths
import skilllink.application.ports.OperationPhase
import skilllink.application.ports.WriterLockOutcome
import skilllink.domain.agent.AgentId
import skilllink.domain.library.SkillNamePolicy
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.UUID

class ManageSkillsOperation(
  internal val layout: LibraryLayoutPort,
  internal val catalog: CatalogPort,
  internal val filesystem: FilesystemPort,
  internal val agents: AgentRegistryPort,
  internal val journal: OperationJournalPort,
  internal val gate: MutationGate,
) {
  internal val recovery: RecoveryCoordinator
    get() = gate.recovery

  fun enable(request: SkillManagementRequest): EnableSkillOutcome =
    withWriter(
      busy = { EnableSkillOutcome.Failed.WriterBusy },
      blocked = { EnableSkillOutcome.Failed.BlockedRecovery },
      io = { EnableSkillOutcome.Failed.IoFailure },
    ) { enableLocked(request) }

  fun disable(request: SkillManagementRequest): DisableSkillOutcome =
    withWriter(
      busy = { DisableSkillOutcome.Failed.WriterBusy },
      blocked = { DisableSkillOutcome.Failed.BlockedRecovery },
      io = { DisableSkillOutcome.Failed.IoFailure },
    ) { disableLocked(request) }

  fun remove(request: SkillManagementRequest): RemoveSkillOutcome {
    if (request.explicitAgents.isNotEmpty()) {
      return RemoveSkillOutcome.Failed.InvalidArguments
    }
    return withWriter(
      busy = { RemoveSkillOutcome.Failed.WriterBusy },
      blocked = { RemoveSkillOutcome.Failed.BlockedRecovery },
      io = { RemoveSkillOutcome.Failed.IoFailure },
    ) { removeLocked(request) }
  }

  private inline fun <T> withWriter(
    busy: () -> T,
    blocked: () -> T,
    io: () -> T,
    block: () -> T,
  ): T =
    when (val lock = gate.tryAcquire()) {
      is WriterLockOutcome.Busy -> {
        busy()
      }

      is WriterLockOutcome.IoFailure -> {
        io()
      }

      is WriterLockOutcome.Acquired -> {
        lock.handle.use {
          if (recovery.recoverIncomplete() is RecoveryOutcome.Blocked) blocked() else block()
        }
      }
    }

  private fun enableLocked(request: SkillManagementRequest): EnableSkillOutcome =
    when (val skill = findManagedSkill(catalog, request)) {
      null -> {
        EnableSkillOutcome.Failed.NotFound
      }

      else -> {
        when {
          filesystem.validateCanonicalBundle(skill.canonicalPath) !is CanonicalValidationOutcome.Valid -> {
            EnableSkillOutcome.Failed.IntegrityFailure
          }

          agents.linkCapability() is LinkCapability.Unavailable -> {
            EnableSkillOutcome.Failed.PlatformCapability
          }

          else -> {
            when (val plan = planEnableForSkill(skill, request)) {
              EnablePlan.InvalidArguments -> {
                EnableSkillOutcome.Failed.InvalidArguments
              }

              EnablePlan.DestinationConflict -> {
                EnableSkillOutcome.Failed.DestinationConflict
              }

              EnablePlan.NoOp -> {
                EnableSkillOutcome.NoOp
              }

              is EnablePlan.Ready -> {
                applyEnable(plan.skill, plan.before, plan.planned, plan.changedAgents)
              }

              else -> {
                EnableSkillOutcome.Failed.IoFailure
              }
            }
          }
        }
      }
    }

  private fun planEnableForSkill(
    skill: ManagedSkillSnapshot,
    request: SkillManagementRequest,
  ): EnablePlan {
    val targetAgents =
      request.explicitAgents.takeUnless { it.isEmpty() }
        ?: skill.installations.map { it.agent }.toSet()
    if (targetAgents.isEmpty()) {
      return EnablePlan.InvalidArguments
    }
    val before = skill.installations
    val planned = mutableListOf<AgentInstallationSnapshot>()
    val changedAgents = mutableSetOf<AgentId>()
    var failure: EnablePlan? = null
    targetAgents.forEach { agent ->
      if (failure == null) {
        failure = planEnableAgent(skill, agent, planned, changedAgents)
      }
    }
    return failure ?: if (changedAgents.isEmpty()) {
      EnablePlan.NoOp
    } else {
      EnablePlan.Ready(skill, before, planned, changedAgents)
    }
  }

  private fun planEnableAgent(
    skill: ManagedSkillSnapshot,
    agent: AgentId,
    planned: MutableList<AgentInstallationSnapshot>,
    changedAgents: MutableSet<AgentId>,
  ): EnablePlan? {
    val existing = skill.installations.firstOrNull { it.agent == agent }
    val destination =
      existing?.destination ?: agents.destinationFor(agent).let { destination ->
        val target = destination.root.resolve(skill.comparisonKey.value)
        target.takeUnless {
          Files.exists(it, LinkOption.NOFOLLOW_LINKS) ||
            destination.reservedNames.contains(skill.comparisonKey.value)
        }
      }
    return if (destination == null) {
      EnablePlan.DestinationConflict
    } else {
      val observed = filesystem.observeLink(destination, skill.canonicalPath)
      val conflict =
        (existing == null && observed != ObservedLinkCondition.Missing) ||
          observed == ObservedLinkCondition.Foreign ||
          observed == ObservedLinkCondition.Unreadable
      if (conflict) {
        EnablePlan.DestinationConflict
      } else if (observed == ObservedLinkCondition.Linked &&
        existing?.desired == DesiredInstallationState.Enabled
      ) {
        planned.add(requireNotNull(existing))
        null
      } else {
        changedAgents.add(agent)
        planned.add(
          AgentInstallationSnapshot(
            agent,
            destination,
            DesiredInstallationState.Enabled,
            ObservedLinkCondition.Linked,
          ),
        )
        null
      }
    }
  }
}

private sealed interface EnablePlan {
  data object NotFound : EnablePlan

  data object IntegrityFailure : EnablePlan

  data object PlatformCapability : EnablePlan

  data object InvalidArguments : EnablePlan

  data object DestinationConflict : EnablePlan

  data object NoOp : EnablePlan

  data class Ready(
    val skill: ManagedSkillSnapshot,
    val before: List<AgentInstallationSnapshot>,
    val planned: List<AgentInstallationSnapshot>,
    val changedAgents: Set<AgentId>,
  ) : EnablePlan
}

internal sealed interface DisablePlan {
  data object NotFound : DisablePlan

  data object NoOp : DisablePlan

  data object DestinationConflict : DisablePlan

  data class Ready(
    val skill: ManagedSkillSnapshot,
    val before: List<AgentInstallationSnapshot>,
    val planned: List<AgentInstallationSnapshot>,
    val changedAgents: Set<AgentId>,
  ) : DisablePlan
}
