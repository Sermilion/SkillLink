package skilllink.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import skilllink.cli.exit.CliExitCodes
import java.nio.file.Files
import java.nio.file.Path

class SkillLinkCliSmokeTest {
  @TempDir
  lateinit var isolatedHome: Path

  @Test
  fun helpDoesNotCreateSkillLinkState() {
    val runtime = SkillLinkRuntimeFactory.create(isolatedHome)
    val rendered = runtime.cli.run(arrayOf("--help"))
    assertEquals(CliExitCodes.SUCCESS, rendered.exitCode)
    assertFalse(Files.exists(isolatedHome.resolve(".skilllink")))
  }

  @Test
  fun versionDoesNotCreateSkillLinkState() {
    val runtime = SkillLinkRuntimeFactory.create(isolatedHome)
    val rendered = runtime.cli.run(arrayOf("--version"))
    assertEquals(CliExitCodes.SUCCESS, rendered.exitCode)
    assertFalse(Files.exists(isolatedHome.resolve(".skilllink")))
  }

  @Test
  fun listDoesNotInitializeAnAbsentLibrary() {
    val runtime = SkillLinkRuntimeFactory.create(isolatedHome)
    val rendered = runtime.cli.run(arrayOf("list"))

    assertEquals(CliExitCodes.SUCCESS, rendered.exitCode)
    assertFalse(Files.exists(isolatedHome.resolve(".skilllink")))
  }
}
