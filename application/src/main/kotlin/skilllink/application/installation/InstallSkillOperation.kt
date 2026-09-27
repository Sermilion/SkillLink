package skilllink.application.installation

import skilllink.application.installation.model.InstallSkillOutcome
import skilllink.application.installation.model.InstallSkillRequest
import skilllink.application.ports.AgentRegistryPort
import skilllink.application.ports.CatalogPort
import skilllink.application.ports.CopyBundleOutcome
import skilllink.application.ports.DiagnosticsPort
import skilllink.application.ports.FilesystemPort
import skilllink.application.ports.LibraryLayoutPort
import skilllink.application.ports.LinkCapability
import skilllink.application.ports.LinkOutcome
import skilllink.application.ports.OperationJournalPort
import skilllink.application.ports.OperationPhase
import skilllink.application.ports.OperationPaths
import skilllink.application.ports.PublishOutcome
import skilllink.application.ports.SafeRemovalOutcome
import skilllink.application.ports.SourceInspectionOutcome
import skilllink.application.ports.WriterLockOutcome
import skilllink.application.ports.WriterLockPort
import skilllink.domain.library.SkillId
import skilllink.domain.library.SkillNameOutcome
import skilllink.domain.library.SkillNamePolicy
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class InstallSkillOperation(
    private val layout: LibraryLayoutPort,
    private val catalog: CatalogPort,
    private val filesystem: FilesystemPort,
    private val agents: AgentRegistryPort,
    private val journal: OperationJournalPort,
    private val writerLock: WriterLockPort,
    private val diagnostics: DiagnosticsPort,
    private val recovery: RecoveryCoordinator,
) {
    fun execute(request: InstallSkillRequest): InstallSkillOutcome {
        val import =
            request as? InstallSkillRequest.NewImport
                ?: return InstallSkillOutcome.Failed.InvalidArguments
        if (import.agents.isEmpty()) {
            return InstallSkillOutcome.Failed.InvalidArguments
        }
        when (val lock = writerLock.tryAcquire()) {
            is WriterLockOutcome.Busy -> return InstallSkillOutcome.Failed.WriterBusy
            is WriterLockOutcome.IoFailure -> return InstallSkillOutcome.Failed.IoFailure
            is WriterLockOutcome.Acquired -> lock.handle.use { locked ->
                val recoveryOutcome = recovery.recoverIncomplete()
                if (recoveryOutcome is RecoveryOutcome.Blocked) {
                    return InstallSkillOutcome.Failed.BlockedRecovery
                }
                return runInstall(import)
            }
        }
    }

    private fun runInstall(import: InstallSkillRequest.NewImport): InstallSkillOutcome {
        val skillFile = import.skillFile.toAbsolutePath().normalize()
        if (!skillFile.fileName.toString().equals("SKILL.md", ignoreCase = false)) {
            return InstallSkillOutcome.Failed.SourceInvalid
        }
        when (val inspected = filesystem.inspectSource(skillFile)) {
            is SourceInspectionOutcome.Invalid -> return InstallSkillOutcome.Failed.SourceInvalid
            is SourceInspectionOutcome.Valid -> {
                val nameOutcome = SkillNamePolicy.validate(inspected.displayName)
                if (nameOutcome !is SkillNameOutcome.Accepted) {
                    return InstallSkillOutcome.Failed.SourceInvalid
                }
                val comparisonKey = nameOutcome.comparisonKey
                if (catalog.findActiveByNameKey(comparisonKey) != null) {
                    return InstallSkillOutcome.Failed.NameConflict
                }
                if (agents.linkCapability() is LinkCapability.Unavailable) {
                    return InstallSkillOutcome.Failed.PlatformCapability
                }
                val layoutPaths = layout.resolve()
                Files.createDirectories(layoutPaths.skillsRoot)
                val destinations =
                    import.agents.associateWith { agent ->
                        val destination = agents.destinationFor(agent)
                        val target = destination.root.resolve(comparisonKey.value)
                        if (Files.exists(target)) {
                            return InstallSkillOutcome.Failed.DestinationConflict
                        }
                        if (destination.reservedNames.contains(comparisonKey.value)) {
                            return InstallSkillOutcome.Failed.DestinationConflict
                        }
                        target
                    }
                val skillId = SkillId.newId()
                val operationId = UUID.randomUUID().toString()
                journal.beginInstall(
                    operationId,
                    skillId,
                    comparisonKey.value,
                    inspected.bundleRoot,
                    inspected.sourceFingerprint,
                    destinations,
                )
                val stagingRoot = layoutPaths.root.resolve("staging").resolve(operationId)
                val canonicalPath = layoutPaths.skillsRoot.resolve(comparisonKey.value)
                var stagingIdentity: String? = null
                catalog.reserveSkill(
                    skillId,
                    nameOutcome.displayName,
                    comparisonKey,
                    canonicalPath,
                    destinations,
                )
                when (val copied = filesystem.copyBundleToStaging(inspected.bundleRoot, stagingRoot)) {
                    is CopyBundleOutcome.Failed -> {
                        return if (rollbackAttempt(operationId, skillId, stagingRoot, null, destinations)) {
                            mapCopyFailure(copied)
                        } else {
                            InstallSkillOutcome.Failed.BlockedRecovery
                        }
                    }
                    is CopyBundleOutcome.Copied -> {
                        stagingIdentity = filesystem.pathIdentity(stagingRoot)
                        journal.updatePhase(
                            operationId,
                            OperationPhase.Staging,
                            OperationPaths(
                                stagingPath = stagingRoot,
                                canonicalPath = canonicalPath,
                                stagingIdentity = stagingIdentity,
                            ),
                        )
                    }
                }
                when (
                    val published =
                        filesystem.publishCanonical(
                            stagingRoot,
                            layoutPaths.skillsRoot,
                            comparisonKey.value,
                        )
                ) {
                    is PublishOutcome.Failed.NameOccupied -> {
                        return if (rollbackAttempt(
                                operationId,
                                skillId,
                                stagingRoot,
                                null,
                                destinations,
                                stagingIdentity = stagingIdentity,
                            )
                        ) {
                            InstallSkillOutcome.Failed.NameConflict
                        } else {
                            InstallSkillOutcome.Failed.BlockedRecovery
                        }
                    }
                    is PublishOutcome.Failed.IoFailure -> {
                        return if (rollbackAttempt(
                                operationId,
                                skillId,
                                stagingRoot,
                                null,
                                destinations,
                                stagingIdentity = stagingIdentity,
                            )
                        ) {
                            InstallSkillOutcome.Failed.IoFailure
                        } else {
                            InstallSkillOutcome.Failed.BlockedRecovery
                        }
                    }
                    is PublishOutcome.Published -> {
                        val canonicalIdentity = filesystem.pathIdentity(published.canonicalPath)
                        journal.updatePhase(
                            operationId,
                            OperationPhase.Published,
                            OperationPaths(
                                stagingPath = stagingRoot,
                                canonicalPath = published.canonicalPath,
                                stagingIdentity = stagingIdentity,
                                canonicalIdentity = canonicalIdentity,
                            ),
                        )
                        for (destination in destinations.values.distinct()) {
                            val agent = destinations.entries.first { it.value == destination }.key
                            when (val linked = filesystem.createOwnedLink(published.canonicalPath, destination, agent)) {
                                is LinkOutcome.Failed.Occupied -> {
                                    return if (rollbackAttempt(
                                            operationId,
                                            skillId,
                                            stagingRoot,
                                            published.canonicalPath,
                                            destinations,
                                            stagingIdentity,
                                            canonicalIdentity,
                                        )
                                    ) {
                                        InstallSkillOutcome.Failed.DestinationConflict
                                    } else {
                                        InstallSkillOutcome.Failed.BlockedRecovery
                                    }
                                }
                                is LinkOutcome.Failed.CapabilityUnavailable -> {
                                    return if (rollbackAttempt(
                                            operationId,
                                            skillId,
                                            stagingRoot,
                                            published.canonicalPath,
                                            destinations,
                                            stagingIdentity,
                                            canonicalIdentity,
                                        )
                                    ) {
                                        InstallSkillOutcome.Failed.PlatformCapability
                                    } else {
                                        InstallSkillOutcome.Failed.BlockedRecovery
                                    }
                                }
                                is LinkOutcome.Failed.IoFailure -> {
                                    return if (rollbackAttempt(
                                            operationId,
                                            skillId,
                                            stagingRoot,
                                            published.canonicalPath,
                                            destinations,
                                            stagingIdentity,
                                            canonicalIdentity,
                                        )
                                    ) {
                                        InstallSkillOutcome.Failed.IoFailure
                                    } else {
                                        InstallSkillOutcome.Failed.BlockedRecovery
                                    }
                                }
                                is LinkOutcome.Linked -> continue
                            }
                        }
                        journal.updatePhase(
                            operationId,
                            OperationPhase.Linked,
                            OperationPaths(
                                stagingPath = stagingRoot,
                                canonicalPath = published.canonicalPath,
                                stagingIdentity = stagingIdentity,
                                canonicalIdentity = canonicalIdentity,
                            ),
                        )
                        catalog.commitOperation(operationId)
                        val removal =
                            filesystem.safeRemoveOriginal(
                                inspected.bundleRoot,
                                inspected.sourceFingerprint,
                            )
                        val stagingCleanup =
                            filesystem.removePathIfOwned(stagingRoot, stagingRoot, stagingIdentity)
                        val cleanupPending = removal is SafeRemovalOutcome.Blocked || !stagingCleanup
                        if (cleanupPending) {
                            catalog.markCleanupPending(skillId)
                            journal.updatePhase(
                                operationId,
                                OperationPhase.CleanupPending,
                                OperationPaths(
                                    stagingPath = if (stagingCleanup) null else stagingRoot,
                                    stagingIdentity = if (stagingCleanup) null else stagingIdentity,
                                ),
                            )
                        } else {
                            journal.markCompleted(operationId)
                        }
                        diagnostics.record("install_complete", skillId.value, comparisonKey.value)
                        return InstallSkillOutcome.Completed(
                            skillName = nameOutcome.displayName,
                            canonicalPath = published.canonicalPath,
                            agents = import.agents,
                            cleanupPending = cleanupPending,
                        )
                    }
                }
            }
        }
    }

    private fun mapCopyFailure(failure: CopyBundleOutcome.Failed): InstallSkillOutcome =
        when (failure) {
            CopyBundleOutcome.Failed.SourceChanged,
            CopyBundleOutcome.Failed.UnsupportedEntry,
            -> InstallSkillOutcome.Failed.SourceInvalid
            CopyBundleOutcome.Failed.Overlap -> InstallSkillOutcome.Failed.DestinationConflict
            CopyBundleOutcome.Failed.IoFailure -> InstallSkillOutcome.Failed.IoFailure
        }

    private fun rollbackAttempt(
        operationId: String,
        skillId: SkillId,
        stagingRoot: Path,
        canonicalPath: Path?,
        destinations: Map<skilllink.domain.agent.AgentId, Path>,
        stagingIdentity: String? = null,
        canonicalIdentity: String? = null,
    ): Boolean {
        var cleaned = true
        for (destination in destinations.values) {
            cleaned =
                filesystem.removePathIfOwned(destination, canonicalPath ?: destination, null) &&
                    cleaned
        }
        if (canonicalPath != null) {
            cleaned =
                filesystem.removePathIfOwned(canonicalPath, canonicalPath, canonicalIdentity) &&
                    cleaned
        }
        cleaned =
            filesystem.removePathIfOwned(stagingRoot, stagingRoot, stagingIdentity) &&
                cleaned
        if (!cleaned) {
            diagnostics.reportSecondaryFailure("install_failure", "rollback_blocked", operationId)
            return false
        }
        catalog.clearReservation(skillId)
        journal.markRolledBack(operationId)
        diagnostics.record("install_rollback", skillId.value, operationId)
        return true
    }
}
