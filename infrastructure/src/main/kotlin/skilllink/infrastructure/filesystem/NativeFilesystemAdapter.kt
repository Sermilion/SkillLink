package skilllink.infrastructure.filesystem

import skilllink.application.installation.model.ObservedLinkCondition
import skilllink.application.ports.CopyBundleOutcome
import skilllink.application.ports.FilesystemPort
import skilllink.application.ports.LinkOutcome
import skilllink.application.ports.PublishOutcome
import skilllink.application.ports.SafeRemovalOutcome
import skilllink.application.ports.SourceInspectionOutcome
import skilllink.domain.agent.AgentId
import skilllink.domain.library.SkillNamePolicy
import skilllink.domain.source.BundleRootPolicy
import skilllink.domain.source.FrontmatterReader
import skilllink.infrastructure.agent.DefaultAgentRegistry
import skilllink.infrastructure.layout.HomeLibraryLayout
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

class NativeFilesystemAdapter(
    private val layout: HomeLibraryLayout = HomeLibraryLayout(),
    private val agentRegistry: DefaultAgentRegistry = DefaultAgentRegistry(),
) : FilesystemPort {
    override fun inspectSource(skillFile: Path): SourceInspectionOutcome {
        if (!Files.isRegularFile(skillFile, LinkOption.NOFOLLOW_LINKS)) {
            return SourceInspectionOutcome.Invalid.NotSkillFile
        }
        if (skillFile.fileName.toString() != "SKILL.md") {
            return SourceInspectionOutcome.Invalid.NotSkillFile
        }
        val resolvedSkillFile =
            try {
                skillFile.toRealPath()
            } catch (_: Exception) {
                return SourceInspectionOutcome.Invalid.NotSkillFile
            }
        val bundleRoot = resolvedSkillFile.parent ?: return SourceInspectionOutcome.Invalid.NotSkillFile
        if (!Files.isDirectory(bundleRoot, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(bundleRoot)) {
            return SourceInspectionOutcome.Invalid.BundleRoot
        }
        if (isInsideRepository(bundleRoot)) {
            return SourceInspectionOutcome.Invalid.BundleRoot
        }
        val content =
            try {
                Files.readString(resolvedSkillFile)
            } catch (_: Exception) {
                return SourceInspectionOutcome.Invalid.NotSkillFile
            }
        when (val frontmatter = FrontmatterReader.parse(content)) {
            is skilllink.domain.source.FrontmatterOutcome.Rejected -> return SourceInspectionOutcome.Invalid.Frontmatter
            is skilllink.domain.source.FrontmatterOutcome.Parsed -> {
                if (bundleRoot.fileName.toString() != frontmatter.name) {
                    return SourceInspectionOutcome.Invalid.NamePolicy
                }
                when (SkillNamePolicy.validate(frontmatter.name)) {
                    is skilllink.domain.library.SkillNameOutcome.Rejected -> return SourceInspectionOutcome.Invalid.NamePolicy
                    is skilllink.domain.library.SkillNameOutcome.Accepted -> {
                        val layoutPaths = layout.resolve()
                        when (
                            BundleRootPolicy.validateRoot(
                                bundleRoot.toString(),
                                comparisonPath(layoutPaths.root).toString(),
                                agentRegistry.allAgentRoots().map(::comparisonPath).map { it.toString() }.toSet(),
                            )
                        ) {
                            is skilllink.domain.source.BundleValidationOutcome.Rejected -> {
                                return SourceInspectionOutcome.Invalid.BundleRoot
                            }
                            is skilllink.domain.source.BundleValidationOutcome.Accepted -> {
                                if (!validateTree(bundleRoot)) {
                                    return SourceInspectionOutcome.Invalid.UnsupportedEntry
                                }
                                return SourceInspectionOutcome.Valid(
                                    bundleRoot = bundleRoot,
                                    displayName = frontmatter.name,
                                    comparisonKey = frontmatter.name.lowercase(Locale.ROOT),
                                    sourceFingerprint = fingerprint(bundleRoot),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun sourceFingerprint(sourceRoot: Path): String = fingerprint(sourceRoot)

    private fun isInsideRepository(bundleRoot: Path): Boolean {
        var current: Path? = bundleRoot
        while (current != null) {
            if (Files.exists(current.resolve(".git"), LinkOption.NOFOLLOW_LINKS)) {
                return true
            }
            current = current.parent
        }
        return false
    }

    private fun validateTree(root: Path): Boolean {
        var valid = true
        Files.walkFileTree(
            root,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (Files.isSymbolicLink(dir)) {
                        valid = false
                        return FileVisitResult.TERMINATE
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (Files.isSymbolicLink(file) || attrs.isOther) {
                        valid = false
                        return FileVisitResult.TERMINATE
                    }
                    return FileVisitResult.CONTINUE
                }
            },
        )
        return valid
    }

    override fun copyBundleToStaging(bundleRoot: Path, stagingRoot: Path): CopyBundleOutcome {
        val resolvedBundleRoot = comparisonPath(bundleRoot)
        val resolvedStagingRoot = comparisonPath(stagingRoot)
        if (resolvedStagingRoot.startsWith(resolvedBundleRoot) ||
            resolvedBundleRoot.startsWith(resolvedStagingRoot)
        ) {
            return CopyBundleOutcome.Failed.Overlap
        }
        if (!validateTree(bundleRoot)) {
            return CopyBundleOutcome.Failed.UnsupportedEntry
        }
        val before = fingerprint(bundleRoot)
        try {
            Files.createDirectories(stagingRoot.parent)
            copyTree(bundleRoot, stagingRoot)
        } catch (_: Exception) {
            return CopyBundleOutcome.Failed.IoFailure
        }
        val after = fingerprint(bundleRoot)
        if (before != after) {
            removeRecursive(stagingRoot)
            return CopyBundleOutcome.Failed.SourceChanged
        }
        return CopyBundleOutcome.Copied(stagingRoot)
    }

    override fun publishCanonical(
        stagingRoot: Path,
        skillsRoot: Path,
        comparisonKey: String,
    ): PublishOutcome {
        val target = skillsRoot.resolve(comparisonKey)
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return PublishOutcome.Failed.NameOccupied
        }
        try {
            Files.createDirectories(skillsRoot)
            Files.move(stagingRoot, target)
            return PublishOutcome.Published(target)
        } catch (_: Exception) {
            return PublishOutcome.Failed.IoFailure
        }
    }

    override fun createOwnedLink(
        canonicalPath: Path,
        destination: Path,
        agent: AgentId,
    ): LinkOutcome {
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            return LinkOutcome.Failed.Occupied
        }
        try {
            Files.createDirectories(destination.parent)
            Files.createSymbolicLink(destination, canonicalPath)
            return LinkOutcome.Linked(destination)
        } catch (_: UnsupportedOperationException) {
            return LinkOutcome.Failed.CapabilityUnavailable
        } catch (_: Exception) {
            return LinkOutcome.Failed.IoFailure
        }
    }

    override fun removePathIfOwned(path: Path, expectedTarget: Path): Boolean =
        removePathIfOwned(path, expectedTarget, null)

    override fun removePathIfOwned(
        path: Path,
        expectedTarget: Path,
        expectedIdentity: String?,
    ): Boolean {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return true
        }
        try {
            if (expectedTarget == path) {
                if (expectedIdentity == null || pathIdentity(path) != expectedIdentity) {
                    return false
                }
                return quarantineAndRemove(path, expectedIdentity)
            }
            if (!Files.isSymbolicLink(path)) {
                return false
            }
            val target = Files.readSymbolicLink(path)
            if (path.parent.resolve(target).normalize() != expectedTarget.normalize()) {
                return false
            }
            Files.delete(path)
            return true
        } catch (_: Exception) {
            return false
        }
    }

    override fun pathIdentity(path: Path): String? =
        try {
            Files.readAttributes(
                path,
                BasicFileAttributes::class.java,
                LinkOption.NOFOLLOW_LINKS,
            ).fileKey()?.toString()
        } catch (_: Exception) {
            null
        }

    override fun observeLink(destination: Path, canonicalPath: Path): ObservedLinkCondition {
        if (!Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            return ObservedLinkCondition.Missing
        }
        if (!Files.isSymbolicLink(destination)) {
            return ObservedLinkCondition.Foreign
        }
        try {
            val target = Files.readSymbolicLink(destination).normalize()
            return if (target == canonicalPath.normalize()) {
                ObservedLinkCondition.Linked
            } else {
                ObservedLinkCondition.Foreign
            }
        } catch (_: Exception) {
            return ObservedLinkCondition.Unreadable
        }
    }

    override fun safeRemoveOriginal(sourceRoot: Path, fingerprint: String): SafeRemovalOutcome {
        if (!Files.exists(sourceRoot, LinkOption.NOFOLLOW_LINKS)) {
            return SafeRemovalOutcome.Unchanged
        }
        if (fingerprint(sourceRoot) != fingerprint) {
            return SafeRemovalOutcome.Blocked
        }
        val quarantine = sourceRoot.resolveSibling("${sourceRoot.fileName}.skilllink-removal-${UUID.randomUUID()}")
        try {
            Files.move(sourceRoot, quarantine)
            if (fingerprint(quarantine) != fingerprint) {
                Files.move(quarantine, sourceRoot)
                return SafeRemovalOutcome.Blocked
            }
            removeRecursive(quarantine)
            return SafeRemovalOutcome.Removed
        } catch (_: Exception) {
            if (Files.exists(quarantine, LinkOption.NOFOLLOW_LINKS) &&
                !Files.exists(sourceRoot, LinkOption.NOFOLLOW_LINKS)
            ) {
                try {
                    Files.move(quarantine, sourceRoot)
                } catch (_: Exception) {
                    return SafeRemovalOutcome.Blocked
                }
            }
            return SafeRemovalOutcome.Blocked
        }
    }

    private fun copyTree(source: Path, target: Path) {
        Files.walk(source).use { paths ->
            paths.forEach { path ->
                val relative = source.relativize(path)
                val dest = target.resolve(relative)
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectories(dest)
                } else {
                    Files.createDirectories(dest.parent)
                    Files.copy(path, dest, StandardCopyOption.COPY_ATTRIBUTES)
                }
            }
        }
    }

    private fun removeRecursive(path: Path) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return
        }
        Files.walk(path).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    private fun quarantineAndRemove(path: Path, expectedIdentity: String): Boolean {
        val quarantine = path.resolveSibling("${path.fileName}.skilllink-delete-${UUID.randomUUID()}")
        try {
            Files.move(path, quarantine)
            if (pathIdentity(quarantine) != expectedIdentity) {
                if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                    Files.move(quarantine, path)
                }
                return false
            }
            removeRecursive(quarantine)
            return true
        } catch (_: Exception) {
            if (Files.exists(quarantine, LinkOption.NOFOLLOW_LINKS) &&
                !Files.exists(path, LinkOption.NOFOLLOW_LINKS)
            ) {
                try {
                    Files.move(quarantine, path)
                } catch (_: Exception) {
                    return false
                }
            }
            return false
        }
    }

    private fun fingerprint(root: Path): String {
        val entries = mutableListOf<String>()
        Files.walk(root).use { paths ->
            paths.forEach { path ->
                val relative = root.relativize(path).toString()
                val attributes =
                    Files.readAttributes(
                        path,
                        BasicFileAttributes::class.java,
                        LinkOption.NOFOLLOW_LINKS,
                    )
                if (attributes.isDirectory) {
                    entries.add("d:$relative:${attributes.fileKey()}")
                } else if (attributes.isRegularFile) {
                    val hash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))
                    entries.add(
                        "f:$relative:${attributes.fileKey()}:${Files.isExecutable(path)}:" +
                            hash.joinToString("") { "%02x".format(it) },
                    )
                } else {
                    entries.add("u:$relative:${attributes.fileKey()}")
                }
            }
        }
        return entries.sorted().joinToString("|")
    }

    private fun comparisonPath(path: Path): Path {
        val absolute = path.toAbsolutePath().normalize()
        var existing = absolute
        val missing = mutableListOf<String>()
        while (!Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            val fileName = existing.fileName ?: return absolute
            missing += fileName.toString()
            existing = existing.parent ?: return absolute
        }
        var resolved = existing.toRealPath()
        for (part in missing.asReversed()) {
            resolved = resolved.resolve(part)
        }
        return resolved.normalize()
    }
}
