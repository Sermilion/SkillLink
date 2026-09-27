package skilllink.buildlogic

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.jar.JarFile

class JvmConventionFunctionalTest {
    @TempDir
    lateinit var projectDir: Path

    @Test
    fun compilesJavaAndKotlinForJava21AndRunsJUnit() {
        prepareCompilationFixture()

        val result = projectDir.runGradle("test", "sourcesJar", "conventionEvidence")

        listOf(":compileJava", ":compileKotlin", ":test", ":sourcesJar").forEach {
            result.assertOutcome(it, TaskOutcome.SUCCESS)
        }
        val kotlinClass = projectDir.resolve("build/classes/kotlin/main/fixture/KotlinFixture.class")
        val javaClass = projectDir.resolve("build/classes/java/main/fixture/JavaFixture.class")
        assertEquals(65, kotlinClass.toFile().readBytes().majorVersion())
        assertEquals(65, javaClass.toFile().readBytes().majorVersion())
        val report = projectDir.resolve("build/test-results/test/TEST-fixture.FixtureTest.xml").toFile().readText()
        assertTrue(report.contains("name=\"discovered()\""), report)
        assertTrue(report.contains("tests=\"1\""), report)
        assertTrue(report.contains("failures=\"0\""), report)
        result.assertContains("testForks=1")
        result.assertContains("-Xjsr305=strict")
        result.assertContains("javaCompiler=21")
        result.assertContains("testLauncher=21")
        result.assertContains("failureCauses=true")
        result.assertContains("failureStacks=true")
        JarFile(projectDir.resolve("build/libs/fixture-sources.jar").toFile()).use { jar ->
            assertTrue(jar.getEntry("fixture/KotlinFixture.kt") != null)
            assertTrue(jar.getEntry("fixture/JavaFixture.java") != null)
        }
    }

    @Test
    fun rejectsCompilerWarning() {
        projectDir.prepareFixture()
        projectDir.write("build.gradle.kts", "plugins { id(\"skilllink.jvm-library\") }\n")
        projectDir.write(
            "src/main/kotlin/fixture/Warning.kt",
            """package fixture
                |@Deprecated("compiler warning fixture")
                |fun oldName() = Unit
                |fun caller() = oldName()
                |
            """.trimMargin(),
        )

        val result = projectDir.gradleRunner("compileKotlin").buildAndFail()

        result.assertOutcome(":compileKotlin", TaskOutcome.FAILED)
        result.assertContains("compiler warning fixture")
        result.assertContains("-Werror")
    }

    @Test
    fun failsOnJUnitFailure() {
        projectDir.prepareFixture()
        projectDir.write("build.gradle.kts", "plugins { id(\"skilllink.jvm-library\") }\n")
        projectDir.write(
            "src/test/kotlin/fixture/FailingTest.kt",
            """package fixture
                |import org.junit.jupiter.api.Assertions.assertEquals
                |import org.junit.jupiter.api.Test
                |class FailingTest {
                |    @Test fun fails() { assertEquals("expected", "actual", "expected marker") }
                |}
                |
            """.trimMargin(),
        )

        val result = projectDir.gradleRunner("test").buildAndFail()

        result.assertOutcome(":test", TaskOutcome.FAILED)
        result.assertContains("expected marker")
        val report = projectDir.resolve("build/test-results/test/TEST-fixture.FailingTest.xml").toFile().readText()
        assertTrue(report.contains("failures=\"1\""), report)
        assertTrue(report.contains("fixture.FailingTest.fails"), report)
    }

    private fun prepareCompilationFixture() {
        projectDir.prepareFixture()
        projectDir.write(
            "src/main/java/fixture/JavaFixture.java",
            "package fixture; public class JavaFixture { public static String message() { return \"java\"; } }\n",
        )
        projectDir.write(
            "src/main/kotlin/fixture/KotlinFixture.kt",
            "package fixture\nclass KotlinFixture { fun message() = JavaFixture.message() }\n",
        )
        projectDir.write(
            "src/test/kotlin/fixture/FixtureTest.kt",
            """package fixture
                |import org.junit.jupiter.api.Assertions.assertEquals
                |import org.junit.jupiter.api.Test
                |class FixtureTest {
                |    @Test fun discovered() { assertEquals("java", KotlinFixture().message()) }
                |}
                |
            """.trimMargin(),
        )
        projectDir.write(
            "build.gradle.kts",
            """import org.gradle.api.tasks.compile.JavaCompile
                |import org.gradle.api.tasks.testing.Test
                |import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
                |
                |plugins { id("skilllink.jvm-library") }
                |tasks.register("conventionEvidence") {
                |    doLast {
                |        val testTask = tasks.named<Test>("test").get()
                |        val compileTask = tasks.named<KotlinCompile>("compileKotlin").get()
                |        val javaTask = tasks.named<JavaCompile>("compileJava").get()
                |        println("testForks=${'$'}{testTask.maxParallelForks}")
                |        println("compilerArgs=${'$'}{compileTask.compilerOptions.freeCompilerArgs.get()}")
                |        println("javaCompiler=${'$'}{javaTask.javaCompiler.get().metadata.languageVersion}")
                |        println("testLauncher=${'$'}{testTask.javaLauncher.get().metadata.languageVersion}")
                |        println("failureCauses=${'$'}{testTask.testLogging.showCauses}")
                |        println("failureStacks=${'$'}{testTask.testLogging.showStackTraces}")
                |    }
                |}
                |
            """.trimMargin(),
        )
    }

    private fun ByteArray.majorVersion(): Int = ((this[6].toInt() and 0xff) shl 8) or (this[7].toInt() and 0xff)
}
