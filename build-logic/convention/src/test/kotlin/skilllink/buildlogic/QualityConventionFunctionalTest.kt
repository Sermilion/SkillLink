package skilllink.buildlogic

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

class QualityConventionFunctionalTest {
    @TempDir
    lateinit var projectDir: Path

    @ParameterizedTest
    @ValueSource(strings = ["module", "build-logic-main", "build-logic-test"])
    fun rejectsFormattingViolation(scope: String) {
        val source = prepareSourceFixture(scope, "package fixture\nclass Bad{val value=1}\n")
        val original = source.toFile().readText()
        val runner =
            if (scope == "module") {
                projectDir.gradleRunner("spotlessKotlinCheck")
            } else {
                projectDir.gradleRunner("-p", "build-logic", ":convention:spotlessKotlinCheck", injectPlugins = false)
            }

        val result = runner.buildAndFail()

        val task = if (scope == "module") ":spotlessKotlinCheck" else ":convention:spotlessKotlinCheck"
        result.assertOutcome(task, TaskOutcome.FAILED)
        result.assertContains("Bad.kt")
        assertEquals(original, source.toFile().readText())
    }

    @ParameterizedTest
    @ValueSource(strings = ["module", "build-logic-main", "build-logic-test"])
    fun rejectsStaticAnalysisViolation(scope: String) {
        val source =
            prepareSourceFixture(
                scope,
                "package fixture\n\nimport java.time.*\n\nfun value() = Instant.EPOCH\n",
            )
        val original = source.toFile().readText()
        val runner =
            if (scope == "module") {
                projectDir.gradleRunner("detekt")
            } else {
                projectDir.gradleRunner("-p", "build-logic", ":convention:detekt", injectPlugins = false)
            }

        val result = runner.buildAndFail()

        val task = if (scope == "module") ":detekt" else ":convention:detekt"
        result.assertOutcome(task, TaskOutcome.FAILED)
        result.assertContains("WildcardImport")
        result.assertContains("Bad.kt")
        assertEquals(original, source.toFile().readText())
    }

    private fun prepareSourceFixture(
        scope: String,
        content: String,
    ): Path {
        val source =
            if (scope == "module") {
                projectDir.prepareFixture()
                projectDir.write("build.gradle.kts", "plugins { id(\"skilllink.quality\") }\n")
                "src/main/kotlin/fixture/Bad.kt"
            } else {
                projectDir.prepareRepositoryFixture()
                val sourceSet = if (scope == "build-logic-main") "main" else "test"
                "build-logic/convention/src/$sourceSet/kotlin/fixture/Bad.kt"
            }
        return projectDir.write(source, content)
    }
}
