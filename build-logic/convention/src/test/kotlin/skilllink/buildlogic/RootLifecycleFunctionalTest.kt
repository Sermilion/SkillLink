package skilllink.buildlogic

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class RootLifecycleFunctionalTest {
    @TempDir
    lateinit var projectDir: Path

    @Test
    fun propagatesIncludedBuildTestFailure() {
        prepareRootFixture(failingConventionTest = true)

        val result = projectDir.gradleRunner("check", injectPlugins = false).buildAndFail()

        result.assertOutcome(":build-logic:convention:test", TaskOutcome.FAILED)
        val report = conventionTestReport()
        assertTrue(report.contains("included convention test failed"), report)
        assertTrue(report.contains("failures=\"1\""), report)
    }

    @Test
    fun rootCheckReachesModulesTestsPluginValidationAndQualityTasks() {
        prepareRootFixture(failingConventionTest = false)

        val result = projectDir.gradleRunner("check", injectPlugins = false).build()

        productionModules.forEach { module ->
            assertNotNull(result.task(":$module:check"), result.output)
            result.assertOutcome(":$module:verifyProductionDependencies", TaskOutcome.SUCCESS)
            result.assertOutcome(":$module:spotlessKotlinGradleCheck", TaskOutcome.SUCCESS)
        }
        listOf(
            ":spotlessKotlinGradleCheck",
            ":build-logic:spotlessKotlinGradleCheck",
            ":build-logic:convention:test",
            ":build-logic:convention:validatePlugins",
            ":build-logic:convention:spotlessKotlinCheck",
            ":build-logic:convention:spotlessKotlinGradleCheck",
            ":build-logic:convention:detekt",
        ).forEach { result.assertOutcome(it, TaskOutcome.SUCCESS) }
        result.assertContains("strictPluginValidation=true")
        val report = conventionTestReport()
        assertTrue(report.contains("tests=\"1\""), report)
        assertTrue(report.contains("failures=\"0\""), report)
    }

    private fun prepareRootFixture(failingConventionTest: Boolean) {
        projectDir.prepareRepositoryFixture()
        val actual = if (failingConventionTest) "unexpected" else "expected"
        projectDir.write(
            "build-logic/convention/src/test/kotlin/fixture/IncludedTest.kt",
            """package fixture
                |
                |import org.junit.jupiter.api.Assertions.assertEquals
                |import org.junit.jupiter.api.Test
                |
                |class IncludedTest {
                |    @Test
                |    fun executesAssertion() {
                |        assertEquals("expected", "$actual", "included convention test failed")
                |    }
                |}
                |
            """.trimMargin(),
        )
        projectDir.resolve("build-logic/convention/build.gradle.kts").toFile().appendText(
            """
                |
                |tasks.validatePlugins {
                |    doLast {
                |        println("strictPluginValidation=${'$'}{enableStricterValidation.get()}")
                |    }
                |}
                |
            """.trimMargin(),
        )
    }

    private fun conventionTestReport(): String =
        projectDir
            .resolve(
                "build-logic/convention/build/test-results/test/TEST-fixture.IncludedTest.xml",
            ).toFile()
            .readText()
}
