package skilllink.application.installation

import skilllink.application.installation.model.InstallSkillOutcome
import skilllink.application.installation.model.InstallSkillRequest
import skilllink.application.ports.CatalogPort
import skilllink.application.ports.CopyBundleOutcome
import skilllink.application.ports.InstallOperationStart
import skilllink.application.ports.LinkCapability
import skilllink.application.ports.LinkOutcome
import skilllink.application.ports.OperationPaths
import skilllink.application.ports.OperationPhase
import skilllink.application.ports.PublishOutcome
import skilllink.application.ports.SafeRemovalOutcome
import skilllink.application.ports.SourceInspectionOutcome
import skilllink.domain.agent.AgentId
import skilllink.domain.library.SkillId
import skilllink.domain.library.SkillNameOutcome
import skilllink.domain.library.SkillNamePolicy
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

internal fun runInstallAttempt(
    operation: InstallSkillOperation,
    import: InstallSkillRequest.NewImport,
): InstallSkillOutcome {
    val skillFile = import.skillFile.toAbsolutePath().normalize()
    val inspected =
        if (skillFile.fileName.toString() == "SKILL.md") {
            operation.filesystem.inspectSource(skillFile)
        } else {
            SourceInspectionOutcome.Invalid.NotSkillFile
        }
    return when (inspected) {
        is SourceInspectionOutcome.Invalid -> InstallSkillOutcome.Failed.SourceInvalid
        is SourceInspectionOutcome.Valid -> prepareInstall(operation, import, inspected)
    }
}

private fun prepareInstall(
    operation: InstallSkillOperation,
    import: InstallSkillRequest.NewImport,
    inspected: SourceInspectionOutcome.Valid,
): InstallSkillOutcome {
    val name = SkillNamePolicy.validate(inspected.displayName)
    return when {
        name !is SkillNameOutcome.Accepted -> {
            InstallSkillOutcome.Failed.SourceInvalid
        }

        operation.catalog.findActiveByNameKey(name.comparisonKey) != null -> {
            InstallSkillOutcome.Failed.NameConflict
        }

        operation.agents.linkCapability() is LinkCapability.Unavailable -> {
            InstallSkillOutcome.Failed.PlatformCapability
        }

        else -> {
            reserveAndCopy(operation, import, inspected, name)
        }
    }
}

private fun reserveAndCopy(
    operation: InstallSkillOperation,
    import: InstallSkillRequest.NewImport,
    inspected: SourceInspectionOutcome.Valid,
    name: SkillNameOutcome.Accepted,
): InstallSkillOutcome {
    val paths = operation.layout.resolve()
    Files.createDirectories(paths.skillsRoot)
    val destinations =
        resolveDestinations(operation, import.agents, name.comparisonKey.value)
            ?: return InstallSkillOutcome.Failed.DestinationConflict
    val skillId = SkillId.newId()
    val operationId = UUID.randomUUID().toString()
    val stagingRoot = paths.root.resolve("staging").resolve(operationId)
    val canonicalPath = paths.skillsRoot.resolve(name.comparisonKey.value)
    reserveInstall(
        operation,
        InstallReservation(inspected, name, skillId, operationId, canonicalPath, destinations),
    )
    return when (val copied = operation.filesystem.copyBundleToStaging(inspected.bundleRoot, stagingRoot)) {
        is CopyBundleOutcome.Failed -> {
            rollbackAndMap(
                operation,
                RollbackContext(operationId, skillId, stagingRoot, null, destinations),
                when (copied) {
                    CopyBundleOutcome.Failed.SourceChanged,
                    CopyBundleOutcome.Failed.UnsupportedEntry,
                    -> InstallSkillOutcome.Failed.SourceInvalid

                    CopyBundleOutcome.Failed.Overlap -> InstallSkillOutcome.Failed.DestinationConflict

                    CopyBundleOutcome.Failed.IoFailure -> InstallSkillOutcome.Failed.IoFailure
                },
            )
        }

        is CopyBundleOutcome.Copied -> {
            val stagingIdentity = operation.filesystem.pathIdentity(stagingRoot)
            operation.journal.updatePhase(
                operationId,
                OperationPhase.Staging,
                OperationPaths(
                    stagingPath = stagingRoot,
                    canonicalPath = canonicalPath,
                    stagingIdentity = stagingIdentity,
                ),
            )
            publishAndInstall(
                operation,
                import,
                InstallState(
                    inspected,
                    name,
                    skillId,
                    operationId,
                    stagingRoot,
                    canonicalPath,
                    destinations,
                    stagingIdentity,
                ),
            )
        }
    }
}

private fun resolveDestinations(
    operation: InstallSkillOperation,
    agents: Set<AgentId>,
    comparisonKey: String,
): Map<AgentId, Path>? {
    val destinations =
        agents.associateWith { agent ->
            val destination = operation.agents.destinationFor(agent)
            val target = destination.root.resolve(comparisonKey)
            target.takeUnless {
                Files.exists(it) || destination.reservedNames.contains(comparisonKey)
            }
        }
    return destinations
        .takeUnless { it.values.any { path -> path == null } }
        ?.mapValues { it.value!! }
}

private fun publishAndInstall(
    operation: InstallSkillOperation,
    import: InstallSkillRequest.NewImport,
    state: InstallState,
): InstallSkillOutcome =
    when (val published = publishCanonical(operation, state)) {
        is PublishResult.Failed -> {
            published.outcome
        }

        is PublishResult.Published -> {
            val linkFailure = linkDestinations(operation, published.canonicalPath, state.destinations)
            if (linkFailure == null) {
                commitPublished(operation, import, state, published)
            } else {
                rollbackAndMap(
                    operation,
                    RollbackContext(
                        state.operationId,
                        state.skillId,
                        state.stagingRoot,
                        published.canonicalPath,
                        state.destinations,
                        state.stagingIdentity,
                        published.canonicalIdentity,
                    ),
                    linkFailure,
                )
            }
        }
    }

private fun publishCanonical(
    operation: InstallSkillOperation,
    state: InstallState,
): PublishResult =
    when (
        val result =
            operation.filesystem.publishCanonical(
                state.stagingRoot,
                state.canonicalPath.parent,
                state.name.comparisonKey.value,
            )
    ) {
        is PublishOutcome.Published -> {
            val canonicalIdentity = operation.filesystem.pathIdentity(result.canonicalPath)
            operation.journal.updatePhase(
                state.operationId,
                OperationPhase.Published,
                OperationPaths(
                    stagingPath = state.stagingRoot,
                    canonicalPath = result.canonicalPath,
                    stagingIdentity = state.stagingIdentity,
                    canonicalIdentity = canonicalIdentity,
                ),
            )
            PublishResult.Published(result.canonicalPath, canonicalIdentity)
        }

        is PublishOutcome.Failed.NameOccupied -> {
            PublishResult.Failed(
                rollbackAndMap(
                    operation,
                    RollbackContext(
                        state.operationId,
                        state.skillId,
                        state.stagingRoot,
                        null,
                        state.destinations,
                        state.stagingIdentity,
                    ),
                    InstallSkillOutcome.Failed.NameConflict,
                ),
            )
        }

        is PublishOutcome.Failed.IoFailure -> {
            PublishResult.Failed(
                rollbackAndMap(
                    operation,
                    RollbackContext(
                        state.operationId,
                        state.skillId,
                        state.stagingRoot,
                        null,
                        state.destinations,
                        state.stagingIdentity,
                    ),
                    InstallSkillOutcome.Failed.IoFailure,
                ),
            )
        }
    }

private fun commitPublished(
    operation: InstallSkillOperation,
    import: InstallSkillRequest.NewImport,
    state: InstallState,
    published: PublishResult.Published,
): InstallSkillOutcome {
    operation.journal.updatePhase(
        state.operationId,
        OperationPhase.Linked,
        OperationPaths(
            stagingPath = state.stagingRoot,
            canonicalPath = published.canonicalPath,
            stagingIdentity = state.stagingIdentity,
            canonicalIdentity = published.canonicalIdentity,
        ),
    )
    operation.catalog.commitOperation(state.operationId)
    val removal =
        operation.filesystem.safeRemoveOriginal(
            state.inspected.bundleRoot,
            state.inspected.sourceFingerprint,
        )
    val stagingClean =
        operation.filesystem.removePathIfOwned(
            state.stagingRoot,
            state.stagingRoot,
            state.stagingIdentity,
        )
    val cleanupPending = removal is SafeRemovalOutcome.Blocked || !stagingClean
    if (cleanupPending) {
        operation.catalog.markCleanupPending(state.skillId)
        operation.journal.updatePhase(
            state.operationId,
            OperationPhase.CleanupPending,
            OperationPaths(
                stagingPath = state.stagingRoot.takeUnless { stagingClean },
                stagingIdentity = state.stagingIdentity.takeUnless { stagingClean },
            ),
        )
    } else {
        operation.journal.markCompleted(state.operationId)
    }
    operation.recovery.diagnosticsPort.record(
        "install_complete",
        state.skillId.value,
        state.name.comparisonKey.value,
    )
    return InstallSkillOutcome.Completed(
        skillName = state.name.displayName,
        canonicalPath = published.canonicalPath,
        agents = import.agents,
        cleanupPending = cleanupPending,
    )
}

private sealed interface PublishResult {
    data class Published(
        val canonicalPath: Path,
        val canonicalIdentity: String?,
    ) : PublishResult

    data class Failed(
        val outcome: InstallSkillOutcome.Failed,
    ) : PublishResult
}

private data class InstallState(
    val inspected: SourceInspectionOutcome.Valid,
    val name: SkillNameOutcome.Accepted,
    val skillId: SkillId,
    val operationId: String,
    val stagingRoot: Path,
    val canonicalPath: Path,
    val destinations: Map<AgentId, Path>,
    val stagingIdentity: String?,
)

private fun linkDestinations(
    operation: InstallSkillOperation,
    canonicalPath: Path,
    destinations: Map<AgentId, Path>,
): InstallSkillOutcome.Failed? =
    destinations.entries
        .map { (agent, destination) ->
            when (operation.filesystem.createOwnedLink(canonicalPath, destination, agent)) {
                is LinkOutcome.Linked -> null
                is LinkOutcome.Failed.Occupied -> InstallSkillOutcome.Failed.DestinationConflict
                is LinkOutcome.Failed.CapabilityUnavailable -> InstallSkillOutcome.Failed.PlatformCapability
                is LinkOutcome.Failed.IoFailure -> InstallSkillOutcome.Failed.IoFailure
            }
        }.firstOrNull { it != null }

private data class RollbackContext(
    val operationId: String,
    val skillId: SkillId,
    val stagingRoot: Path,
    val canonicalPath: Path?,
    val destinations: Map<AgentId, Path>,
    val stagingIdentity: String? = null,
    val canonicalIdentity: String? = null,
)

private fun rollbackAndMap(
    operation: InstallSkillOperation,
    context: RollbackContext,
    failure: InstallSkillOutcome.Failed,
): InstallSkillOutcome.Failed =
    if (rollbackAttempt(operation, context)) {
        failure
    } else {
        InstallSkillOutcome.Failed.BlockedRecovery
    }

private fun rollbackAttempt(
    operation: InstallSkillOperation,
    context: RollbackContext,
): Boolean {
    var cleaned = true
    context.destinations.values.forEach { destination ->
        cleaned =
            operation.filesystem.removePathIfOwned(
                destination,
                context.canonicalPath ?: destination,
                null,
            ) && cleaned
    }
    context.canonicalPath?.let {
        cleaned =
            operation.filesystem.removePathIfOwned(it, it, context.canonicalIdentity) && cleaned
    }
    cleaned =
        operation.filesystem.removePathIfOwned(
            context.stagingRoot,
            context.stagingRoot,
            context.stagingIdentity,
        ) && cleaned
    return if (!cleaned) {
        operation.recovery.diagnosticsPort.reportSecondaryFailure(
            "install_failure",
            "rollback_blocked",
            context.operationId,
        )
        false
    } else {
        operation.catalog.clearReservation(context.skillId)
        operation.journal.markRolledBack(context.operationId)
        operation.recovery.diagnosticsPort.record(
            "install_rollback",
            context.skillId.value,
            context.operationId,
        )
        true
    }
}
