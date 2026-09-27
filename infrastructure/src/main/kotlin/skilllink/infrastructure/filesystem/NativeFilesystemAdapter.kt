package skilllink.infrastructure.filesystem

import skilllink.application.installation.model.ObservedLinkCondition
import skilllink.application.ports.CanonicalValidationOutcome
import skilllink.application.ports.CopyBundleOutcome
import skilllink.application.ports.FilesystemPort
import skilllink.application.ports.LinkOutcome
import skilllink.application.ports.MoveToTrashOutcome
import skilllink.application.ports.PublishOutcome
import skilllink.application.ports.SafeRemovalOutcome
import skilllink.application.ports.SourceInspectionOutcome
import skilllink.domain.agent.AgentId
import skilllink.domain.library.SkillNameOutcome
import skilllink.domain.library.SkillNamePolicy
import skilllink.domain.source.FrontmatterOutcome
import skilllink.domain.source.FrontmatterReader
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

class NativeFilesystemAdapter : FilesystemPort {
    override fun inspectSource(skillFile: Path): SourceInspectionOutcome =
        NativeFilesystemSupport.inspectSource(
            skillFile,
        )

    override fun copyBundleToStaging(
        bundleRoot: Path,
        stagingRoot: Path,
    ): CopyBundleOutcome {
        val resolvedBundleRoot = NativeFilesystemSupport.comparisonPath(bundleRoot)
        val resolvedStagingRoot = NativeFilesystemSupport.comparisonPath(stagingRoot)
        return when {
            resolvedStagingRoot.startsWith(resolvedBundleRoot) ||
                resolvedBundleRoot.startsWith(resolvedStagingRoot) -> {
                CopyBundleOutcome.Failed.Overlap
            }

            !NativeFilesystemSupport.validateTree(bundleRoot) -> {
                CopyBundleOutcome.Failed.UnsupportedEntry
            }

            else -> {
                val before = NativeFilesystemSupport.fingerprint(bundleRoot)
                runCatching {
                    Files.createDirectories(stagingRoot.parent)
                    NativeFilesystemSupport.copyTree(bundleRoot, stagingRoot)
                }.fold(
                    onSuccess = {
                        if (NativeFilesystemSupport.fingerprint(bundleRoot) == before) {
                            CopyBundleOutcome.Copied(stagingRoot)
                        } else {
                            NativeFilesystemSupport.removeRecursive(stagingRoot)
                            CopyBundleOutcome.Failed.SourceChanged
                        }
                    },
                    onFailure = { CopyBundleOutcome.Failed.IoFailure },
                )
            }
        }
    }

    override fun publishCanonical(
        stagingRoot: Path,
        skillsRoot: Path,
        comparisonKey: String,
    ): PublishOutcome {
        val target = skillsRoot.resolve(comparisonKey)
        return when {
            Files.exists(target, LinkOption.NOFOLLOW_LINKS) -> {
                PublishOutcome.Failed.NameOccupied
            }

            else -> {
                runCatching {
                    Files.createDirectories(skillsRoot)
                    Files.move(stagingRoot, target)
                    PublishOutcome.Published(target)
                }.getOrElse { PublishOutcome.Failed.IoFailure }
            }
        }
    }

    override fun createOwnedLink(
        canonicalPath: Path,
        destination: Path,
        agent: AgentId,
    ): LinkOutcome =
        when {
            Files.exists(destination, LinkOption.NOFOLLOW_LINKS) -> {
                LinkOutcome.Failed.Occupied
            }

            else -> {
                try {
                    Files.createDirectories(destination.parent)
                    Files.createSymbolicLink(destination, canonicalPath)
                    LinkOutcome.Linked(destination)
                } catch (_: UnsupportedOperationException) {
                    LinkOutcome.Failed.CapabilityUnavailable
                } catch (_: Exception) {
                    LinkOutcome.Failed.IoFailure
                }
            }
        }

    override fun removePathIfOwned(
        path: Path,
        expectedTarget: Path,
        expectedIdentity: String?,
    ): Boolean = NativeFilesystemSupport.removePathIfOwned(path, expectedTarget, expectedIdentity)

    override fun pathIdentity(path: Path): String? = NativeFilesystemSupport.pathIdentity(path)

    override fun observeLink(
        destination: Path,
        canonicalPath: Path,
    ): ObservedLinkCondition =
        when {
            !Files.exists(destination, LinkOption.NOFOLLOW_LINKS) -> {
                ObservedLinkCondition.Missing
            }

            !Files.isSymbolicLink(destination) -> {
                ObservedLinkCondition.Foreign
            }

            else -> {
                runCatching { Files.readSymbolicLink(destination).normalize() }
                    .fold(
                        onSuccess = { target ->
                            if (target == canonicalPath.normalize()) {
                                ObservedLinkCondition.Linked
                            } else {
                                ObservedLinkCondition.Foreign
                            }
                        },
                        onFailure = { ObservedLinkCondition.Unreadable },
                    )
            }
        }

    override fun validateCanonicalBundle(canonicalPath: Path): CanonicalValidationOutcome {
        val skillFile = canonicalPath.resolve("SKILL.md")
        return when {
            !Files.isDirectory(canonicalPath, LinkOption.NOFOLLOW_LINKS) ||
                !Files.isRegularFile(skillFile, LinkOption.NOFOLLOW_LINKS) -> {
                CanonicalValidationOutcome.Invalid.Missing
            }

            else -> {
                runCatching { Files.readString(skillFile) }
                    .fold(
                        onSuccess = { content ->
                            when (val frontmatter = FrontmatterReader.parse(content)) {
                                is FrontmatterOutcome.Rejected -> {
                                    CanonicalValidationOutcome.Invalid.Corrupt
                                }

                                is FrontmatterOutcome.Parsed -> {
                                    if (SkillNamePolicy.validate(frontmatter.name) is SkillNameOutcome.Rejected ||
                                        !NativeFilesystemSupport.validateTree(canonicalPath)
                                    ) {
                                        CanonicalValidationOutcome.Invalid.Corrupt
                                    } else {
                                        CanonicalValidationOutcome.Valid
                                    }
                                }
                            }
                        },
                        onFailure = { CanonicalValidationOutcome.Invalid.Corrupt },
                    )
            }
        }
    }

    override fun moveCanonicalToTrash(
        canonicalPath: Path,
        trashDestination: Path,
    ): MoveToTrashOutcome =
        when {
            Files.exists(trashDestination, LinkOption.NOFOLLOW_LINKS) -> {
                MoveToTrashOutcome.Failed.Occupied
            }

            !Files.isDirectory(canonicalPath, LinkOption.NOFOLLOW_LINKS) -> {
                MoveToTrashOutcome.Failed.IoFailure
            }

            else -> {
                runCatching {
                    Files.createDirectories(trashDestination.parent)
                    Files.move(canonicalPath, trashDestination)
                    MoveToTrashOutcome.Moved(trashDestination)
                }.getOrElse { MoveToTrashOutcome.Failed.IoFailure }
            }
        }

    override fun safeRemoveOriginal(
        sourceRoot: Path,
        fingerprint: String,
    ): SafeRemovalOutcome {
        val quarantine =
            sourceRoot.resolveSibling(
                "${sourceRoot.fileName}.skilllink-removal-${UUID.randomUUID()}",
            )
        return when {
            !Files.exists(sourceRoot, LinkOption.NOFOLLOW_LINKS) -> {
                SafeRemovalOutcome.Unchanged
            }

            currentFingerprint(sourceRoot) != fingerprint -> {
                SafeRemovalOutcome.Blocked
            }

            else -> {
                runCatching {
                    Files.move(sourceRoot, quarantine)
                    if (Files.isDirectory(quarantine, LinkOption.NOFOLLOW_LINKS)) {
                        if (NativeFilesystemSupport.fingerprint(quarantine) != fingerprint) {
                            Files.move(quarantine, sourceRoot)
                            SafeRemovalOutcome.Blocked
                        } else {
                            NativeFilesystemSupport.removeRecursive(quarantine)
                            SafeRemovalOutcome.Removed
                        }
                    } else {
                        Files.deleteIfExists(quarantine)
                        SafeRemovalOutcome.Removed
                    }
                }.getOrElse {
                    if (Files.exists(quarantine, LinkOption.NOFOLLOW_LINKS) &&
                        !Files.exists(sourceRoot, LinkOption.NOFOLLOW_LINKS)
                    ) {
                        runCatching { Files.move(quarantine, sourceRoot) }
                    }
                    SafeRemovalOutcome.Blocked
                }
            }
        }
    }
}

private fun currentFingerprint(path: Path): String =
    if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
        NativeFilesystemSupport.fingerprint(path)
    } else {
        val hash =
            MessageDigest
                .getInstance("SHA-256")
                .digest(Files.readAllBytes(path))
        "f:${path.fileName}:${hash.joinToString("") { "%02x".format(it) }}"
    }

internal fun NativeFilesystemAdapter.sourceFingerprint(root: Path) = NativeFilesystemSupport.fingerprint(root)
