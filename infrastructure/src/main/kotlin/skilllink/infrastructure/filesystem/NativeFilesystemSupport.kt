package skilllink.infrastructure.filesystem

import skilllink.application.ports.SourceInspectionOutcome
import skilllink.domain.library.SkillNameOutcome
import skilllink.domain.library.SkillNamePolicy
import skilllink.domain.source.FrontmatterOutcome
import skilllink.domain.source.FrontmatterReader
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.UUID

internal object NativeFilesystemSupport {
  fun inspectSource(skillFile: Path): SourceInspectionOutcome {
    val resolvedSkillFile = resolveSkillFile(skillFile)
    val bundleRoot = resolvedSkillFile?.parent
    return when {
      bundleRoot == null -> {
        SourceInspectionOutcome.Invalid.NotSkillFile
      }

      else -> {
        NativeFilesystemInspectionSupport.inspectBundle(
          bundleRoot,
          resolvedSkillFile,
        )
      }
    }
  }

  fun isInsideRepository(bundleRoot: Path): Boolean {
    var current: Path? = bundleRoot
    while (current != null) {
      if (Files.exists(current.resolve(".git"), LinkOption.NOFOLLOW_LINKS)) {
        return true
      }
      current = current.parent
    }
    return false
  }

  fun validateTree(root: Path): Boolean {
    var valid = true
    Files.walkFileTree(
      root,
      object : SimpleFileVisitor<Path>() {
        override fun preVisitDirectory(
          dir: Path,
          attrs: BasicFileAttributes,
        ): FileVisitResult {
          if (Files.isSymbolicLink(dir)) {
            valid = false
            return FileVisitResult.TERMINATE
          }
          return FileVisitResult.CONTINUE
        }

        override fun visitFile(
          file: Path,
          attrs: BasicFileAttributes,
        ): FileVisitResult {
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

  fun copyTree(
    source: Path,
    target: Path,
  ) {
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

  fun removePathIfOwned(
    path: Path,
    expectedTarget: Path,
    expectedIdentity: String?,
  ): Boolean =
    when {
      !Files.exists(path, LinkOption.NOFOLLOW_LINKS) -> {
        true
      }

      else -> {
        runCatching {
          if (expectedTarget == path) {
            expectedIdentity != null &&
              pathIdentity(path) == expectedIdentity &&
              quarantineAndRemove(path, expectedIdentity)
          } else if (!Files.isSymbolicLink(path)) {
            false
          } else {
            val target = Files.readSymbolicLink(path)
            if (path.parent.resolve(target).normalize() == expectedTarget.normalize()) {
              Files.delete(path)
              true
            } else {
              false
            }
          }
        }.getOrDefault(false)
      }
    }

  fun removeRecursive(path: Path) {
    if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      return
    }
    Files.walk(path).use { paths ->
      paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
    }
  }

  fun quarantineAndRemove(
    path: Path,
    expectedIdentity: String,
  ): Boolean {
    val quarantine = path.resolveSibling("${path.fileName}.skilllink-delete-${UUID.randomUUID()}")
    return try {
      Files.move(path, quarantine)
      if (pathIdentity(quarantine) != expectedIdentity) {
        restoreIfNeeded(path, quarantine)
        false
      } else {
        removeRecursive(quarantine)
        true
      }
    } catch (_: Exception) {
      restoreIfNeeded(path, quarantine)
      false
    }
  }

  fun fingerprint(root: Path): String {
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

  fun pathIdentity(path: Path): String? =
    try {
      Files
        .readAttributes(
          path,
          BasicFileAttributes::class.java,
          LinkOption.NOFOLLOW_LINKS,
        ).fileKey()
        ?.toString()
    } catch (_: Exception) {
      null
    }

  fun comparisonPath(path: Path): Path {
    val absolute = path.toAbsolutePath().normalize()
    var existing = absolute
    val missing = mutableListOf<String>()
    while (!Files.exists(existing, LinkOption.NOFOLLOW_LINKS) &&
      existing.fileName != null &&
      existing.parent != null
    ) {
      missing += existing.fileName.toString()
      existing = existing.parent!!
    }
    if (!Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
      return absolute
    }
    var resolved = existing.toRealPath()
    for (part in missing.asReversed()) {
      resolved = resolved.resolve(part)
    }
    return resolved.normalize()
  }
}

private object NativeFilesystemInspectionSupport {
  fun inspectBundle(
    bundleRoot: Path,
    skillFile: Path,
  ): SourceInspectionOutcome {
    val content = runCatching { Files.readString(skillFile) }.getOrNull()
    val frontmatter = content?.let(FrontmatterReader::parse)
    return when {
      frontmatter !is FrontmatterOutcome.Parsed -> {
        SourceInspectionOutcome.Invalid.Frontmatter
      }

      SkillNamePolicy.validate(frontmatter.name) is SkillNameOutcome.Rejected -> {
        SourceInspectionOutcome.Invalid.NamePolicy
      }

      else -> {
        val isBundle =
          bundleRoot.fileName.toString() == frontmatter.name
        SourceInspectionOutcome.Valid(
          bundleRoot = if (isBundle) bundleRoot else skillFile,
          displayName = frontmatter.name,
          comparisonKey = frontmatter.name.lowercase(java.util.Locale.ROOT),
          sourceFingerprint =
            if (isBundle) {
              NativeFilesystemSupport.fingerprint(bundleRoot)
            } else {
              val hash =
                MessageDigest
                  .getInstance("SHA-256")
                  .digest(Files.readAllBytes(skillFile))
              "f:${skillFile.fileName}:${hash.joinToString("") { "%02x".format(it) }}"
            },
        )
      }
    }
  }
}

private fun resolveSkillFile(skillFile: Path): Path? =
  if (Files.isRegularFile(skillFile, LinkOption.NOFOLLOW_LINKS) &&
    skillFile.fileName.toString() == "SKILL.md"
  ) {
    runCatching { skillFile.toRealPath() }.getOrNull()
  } else {
    null
  }

private fun restoreIfNeeded(
  path: Path,
  quarantine: Path,
) {
  if (Files.exists(quarantine, LinkOption.NOFOLLOW_LINKS) &&
    !Files.exists(path, LinkOption.NOFOLLOW_LINKS)
  ) {
    runCatching { Files.move(quarantine, path) }
  }
}
