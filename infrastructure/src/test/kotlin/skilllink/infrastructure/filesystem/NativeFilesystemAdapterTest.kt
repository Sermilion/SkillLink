package skilllink.infrastructure.filesystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import skilllink.application.ports.CopyBundleOutcome
import skilllink.application.ports.SourceInspectionOutcome
import java.nio.file.Files
import java.nio.file.Path

class NativeFilesystemAdapterTest {
  @TempDir
  lateinit var temp: Path

  @Test
  fun copiesNestedBundleBytesIntoStaging() {
    val bundle = temp.resolve("source/demo-skill")
    val skillFile = bundle.resolve("SKILL.md")
    Files.createDirectories(bundle)
    Files.writeString(skillFile, "---\nname: demo-skill\ndescription: demo\n---\n")
    Files.createDirectories(bundle.resolve("nested"))
    Files.writeString(bundle.resolve("nested/note.txt"), "payload")
    Files.writeString(bundle.resolve(".hidden"), "dot")
    Files.createDirectories(bundle.resolve("empty"))
    Files.writeString(bundle.resolve("run.sh"), "printf demo")
    bundle.resolve("run.sh").toFile().setExecutable(true)
    val adapter = NativeFilesystemAdapter()
    val inspected = adapter.inspectSource(skillFile)
    assertTrue(inspected is SourceInspectionOutcome.Valid)
    val staging = temp.resolve("staging")
    val copied = adapter.copyBundleToStaging(bundle, staging)
    assertTrue(copied is CopyBundleOutcome.Copied)
    assertEquals("payload", Files.readString(staging.resolve("nested/note.txt")))
    assertEquals("dot", Files.readString(staging.resolve(".hidden")))
    assertTrue(Files.isDirectory(staging.resolve("empty")))
    assertEquals(Files.isExecutable(bundle.resolve("run.sh")), Files.isExecutable(staging.resolve("run.sh")))
  }

  @Test
  fun rejectsMalformedFrontmatter() {
    val bundle = temp.resolve("source/bad")
    Files.createDirectories(bundle)
    val skillFile = bundle.resolve("SKILL.md")
    Files.writeString(skillFile, "no frontmatter")
    val adapter = NativeFilesystemAdapter()
    val inspected = adapter.inspectSource(skillFile)
    assertTrue(inspected is SourceInspectionOutcome.Invalid.Frontmatter)
  }

  @Test
  fun acceptsSkillFromRepositoryDirectory() {
    val bundle = temp.resolve("source/repository")
    Files.createDirectories(bundle.resolve(".git"))
    val skillFile = bundle.resolve("SKILL.md")
    Files.writeString(skillFile, "---\nname: demo\ndescription: demo\n---\n")
    val adapter = NativeFilesystemAdapter()

    val inspected = adapter.inspectSource(skillFile)
    assertTrue(inspected is SourceInspectionOutcome.Valid)
    assertEquals("demo", (inspected as SourceInspectionOutcome.Valid).displayName)
  }

  @Test
  fun retainsChangedOriginalDuringSafeRemoval() {
    val bundle = temp.resolve("source/demo")
    Files.createDirectories(bundle)
    Files.writeString(bundle.resolve("SKILL.md"), "original")
    val adapter = NativeFilesystemAdapter()
    val fingerprint = adapter.sourceFingerprint(bundle)
    Files.writeString(bundle.resolve("SKILL.md"), "changed")

    val outcome = adapter.safeRemoveOriginal(bundle, fingerprint)

    assertTrue(outcome is skilllink.application.ports.SafeRemovalOutcome.Blocked)
    assertTrue(Files.exists(bundle.resolve("SKILL.md")))
    Files.list(bundle.parent).use { paths ->
      assertFalse(paths.anyMatch { it.fileName.toString().startsWith("demo.skilllink-removal-") })
    }
  }

  @Test
  fun acceptsSkillFromBundleWithSymlinks() {
    val bundle = temp.resolve("source/linked")
    val outside = temp.resolve("outside.txt")
    Files.createDirectories(bundle)
    Files.writeString(outside, "outside")
    Files.writeString(bundle.resolve("SKILL.md"), "---\nname: linked\ndescription: demo\n---\n")
    try {
      Files.createSymbolicLink(bundle.resolve("outside-link"), outside)
    } catch (_: Exception) {
      assumeTrue(false)
      return
    }
    val adapter = NativeFilesystemAdapter()

    val inspected = adapter.inspectSource(bundle.resolve("SKILL.md"))
    assertTrue(inspected is SourceInspectionOutcome.Valid)
    assertEquals("linked", (inspected as SourceInspectionOutcome.Valid).displayName)
  }

  @Test
  fun doesNotDeleteForeignReplacementAtRecordedDestination() {
    val canonical = temp.resolve("canonical")
    val destination = temp.resolve("agent/demo")
    Files.createDirectories(canonical)
    Files.createDirectories(destination.parent)
    Files.writeString(destination, "foreign")
    val adapter = NativeFilesystemAdapter()

    val removed = adapter.removePathIfOwned(destination, canonical, null)

    assertFalse(removed)
    assertTrue(Files.exists(destination))
    assertEquals("foreign", Files.readString(destination))
  }
}
