package skilllink.buildlogic

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties

internal val productionModules = listOf("domain", "application", "infrastructure", "desktop", "cli", "app")

internal fun Path.write(
    relative: String,
    content: String,
): Path {
    val destination = resolve(relative)
    Files.createDirectories(destination.parent)
    return Files.writeString(destination, content)
}

internal fun Path.prepareFixture() {
    copyRepositoryFile("gradle/libs.versions.toml")
    copyRepositoryFile("config/detekt/detekt.yml")
    write(
        "settings.gradle.kts",
        """rootProject.name = "fixture"
            |dependencyResolutionManagement {
            |    repositories {
            |        mavenCentral()
            |    }
            |}
            |
        """.trimMargin(),
    )
}

internal fun Path.includeModules(vararg names: String) {
    val settings = resolve("settings.gradle.kts").toFile()
    val modules = names.joinToString(", ") { "\"$it\"" }
    settings.appendText("\ninclude($modules)\n")
}

internal fun Path.copyRepositoryFile(relative: String) {
    write(relative, Files.readString(repositoryRoot().resolve(relative)))
}

internal fun Path.prepareRepositoryFixture() {
    listOf(
        "settings.gradle.kts",
        "build.gradle.kts",
        "gradle.properties",
        "gradle/libs.versions.toml",
        "config/detekt/detekt.yml",
        "build-logic/settings.gradle.kts",
        "build-logic/build.gradle.kts",
        "build-logic/convention/build.gradle.kts",
    ).forEach { copyRepositoryFile(it) }
    productionModules.forEach { copyRepositoryFile("$it/build.gradle.kts") }
    val repository = repositoryRoot()
    Files.walk(repository.resolve("build-logic/convention/src/main")).use { paths ->
        paths.filter { Files.isRegularFile(it) }.forEach { source ->
            copyRepositoryFile(repository.relativize(source).toString())
        }
    }
}

internal fun repositoryRoot(): Path {
    var repository = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
    while (!Files.exists(repository.resolve("gradle/libs.versions.toml")) && repository.parent != null) {
        repository = repository.parent
    }
    return repository
}

internal fun Path.gradleRunner(
    vararg arguments: String,
    injectPlugins: Boolean = true,
): GradleRunner {
    val wrapper = Properties()
    Files.newInputStream(repositoryRoot().resolve("gradle/wrapper/gradle-wrapper.properties")).use(wrapper::load)
    val version = wrapper.getProperty("distributionUrl").substringAfter("gradle-").substringBefore("-bin.zip")
    val runner =
        GradleRunner
            .create()
            .withProjectDir(toFile())
            .withGradleVersion(version)
            .withArguments(*arguments, "--stacktrace", "--max-workers=2", "--no-parallel", "--no-configuration-cache")
    return if (injectPlugins) runner.withPluginClasspath() else runner
}

internal fun Path.runGradle(vararg arguments: String): BuildResult = gradleRunner(*arguments).build()

internal fun BuildResult.assertOutcome(
    taskPath: String,
    outcome: TaskOutcome,
) {
    assertEquals(outcome, task(taskPath)?.outcome, output)
}

internal fun BuildResult.assertContains(fragment: String) {
    assertTrue(output.contains(fragment), "Expected output to contain '$fragment'. Output was:\n$output")
}
