package skilllink.buildlogic

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path

class DependencyGuardFunctionalTest {
    @TempDir
    lateinit var projectDir: Path

    @Test
    fun acceptsSpecifiedGraph() {
        projectDir.prepareFixture()
        projectDir.includeModules(*productionModules.toTypedArray())
        productionModules.forEach { module ->
            projectDir.copyRepositoryFile("$module/build.gradle.kts")
            val script = projectDir.resolve("$module/build.gradle.kts").toFile()
            script.writeText("import org.gradle.api.artifacts.ProjectDependency\n\n" + script.readText())
            script.appendText(
                """
                    |tasks.named("verifyProductionDependencies") {
                    |    doLast {
                    |        listOf("implementation", "api", "compileOnly", "runtimeOnly").forEach { name ->
                    |            val edges = configurations.getByName(name).dependencies
                    |                .withType(ProjectDependency::class.java)
                    |                .map { it.dependencyProject.path }.sorted()
                    |            println("$module:${'$'}name=${'$'}edges")
                    |        }
                    |    }
                    |}
                    |
                """.trimMargin(),
            )
        }

        val result =
            projectDir.runGradle(
                *productionModules.map { ":$it:verifyProductionDependencies" }.toTypedArray(),
            )

        productionModules.forEach {
            result.assertOutcome(":$it:verifyProductionDependencies", TaskOutcome.SUCCESS)
            listOf("api", "compileOnly", "runtimeOnly").forEach { configuration ->
                result.assertContains("$it:$configuration=[]")
            }
        }
        result.assertContains("domain:implementation=[]")
        result.assertContains("application:implementation=[:domain]")
        result.assertContains("infrastructure:implementation=[:application, :domain]")
        result.assertContains("desktop:implementation=[:application, :domain]")
        result.assertContains("app:implementation=[:application, :desktop, :infrastructure]")
    }

    @ParameterizedTest
    @CsvSource(
        "domain, application, implementation",
        "domain, desktop, api",
        "desktop, infrastructure, compileOnly",
        "desktop, infrastructure, runtimeOnly",
        "desktop, infrastructure, customProduction",
    )
    fun rejectsForbiddenDirectEdgesAcrossProductionConfigurations(
        source: String,
        target: String,
        configuration: String,
    ) {
        projectDir.prepareFixture()
        projectDir.includeModules(source, target)
        val customConfiguration =
            if (configuration == "customProduction") {
                "val customProduction = configurations.create(\"customProduction\")\n" +
                    "configurations.named(\"runtimeClasspath\") { extendsFrom(customProduction) }"
            } else {
                ""
            }
        projectDir.write(
            "$source/build.gradle.kts",
            """plugins { id("skilllink.jvm-library") }
                |$customConfiguration
                |dependencies { add("$configuration", project(":$target")) }
                |
            """.trimMargin(),
        )
        projectDir.write("$target/build.gradle.kts", "plugins { id(\"skilllink.jvm-library\") }\n")

        val result = projectDir.gradleRunner(":$source:verifyProductionDependencies").buildAndFail()

        result.assertOutcome(":$source:verifyProductionDependencies", TaskOutcome.FAILED)
        result.assertContains("$source -> $target through $configuration")
    }

    @Test
    fun ignoresTestOnlyProjectDependencies() {
        projectDir.prepareFixture()
        projectDir.includeModules("domain", "application")
        projectDir.write(
            "domain/build.gradle.kts",
            "plugins { id(\"skilllink.jvm-library\") }\n" +
                "dependencies { testImplementation(project(\":application\")) }\n",
        )
        projectDir.write("application/build.gradle.kts", "plugins { id(\"skilllink.jvm-library\") }\n")

        val result = projectDir.runGradle(":domain:verifyProductionDependencies")

        result.assertOutcome(":domain:verifyProductionDependencies", TaskOutcome.SUCCESS)
    }
}
