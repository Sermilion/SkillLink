package skilllink.buildlogic

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path

class QualityScriptCoverageFunctionalTest {
  @TempDir
  lateinit var projectDir: Path

  @ParameterizedTest
  @CsvSource(
    "build.gradle.kts, :spotlessKotlinGradleCheck",
    "settings.gradle.kts, :spotlessKotlinGradleCheck",
    "domain/build.gradle.kts, :domain:spotlessKotlinGradleCheck",
    "build-logic/build.gradle.kts, :spotlessKotlinGradleCheck",
    "build-logic/settings.gradle.kts, :spotlessKotlinGradleCheck",
    "build-logic/convention/build.gradle.kts, :convention:spotlessKotlinGradleCheck",
  )
  fun rejectsFormattingViolationsInBuildScripts(
    relative: String,
    task: String,
  ) {
    projectDir.prepareRepositoryFixture()
    val source = projectDir.resolve(relative).toFile()
    source.appendText("\nval formattingProbe=\"formatting\"\n")
    val original = source.readText()
    val runner =
      if (relative.startsWith("build-logic/")) {
        projectDir.gradleRunner("-p", "build-logic", task, injectPlugins = false)
      } else {
        projectDir.gradleRunner(task, injectPlugins = false)
      }

    val result = runner.buildAndFail()

    result.assertOutcome(task, TaskOutcome.FAILED)
    result.assertContains(source.name)
    result.assertContains("formattingProbe")
    assertEquals(original, source.readText())
  }
}
