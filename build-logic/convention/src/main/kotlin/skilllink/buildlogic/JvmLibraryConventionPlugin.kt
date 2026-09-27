package skilllink.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class JvmLibraryConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.pluginManager.apply("org.jetbrains.kotlin.jvm")
    target.pluginManager.apply(JavaLibraryPlugin::class.java)
    target.pluginManager.apply("skilllink.quality")

    target.extensions.configure(JavaPluginExtension::class.java) {
      it.toolchain.languageVersion.set(JavaLanguageVersion.of(JVM_VERSION))
      it.withSourcesJar()
    }
    target.tasks.withType(KotlinCompile::class.java).configureEach {
      it.compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
      it.compilerOptions.allWarningsAsErrors.set(true)
      it.compilerOptions.freeCompilerArgs.add("-Xjsr305=strict")
    }
    target.tasks.withType(JavaCompile::class.java).configureEach {
      it.options.release.set(JVM_VERSION)
    }
    target.dependencies.add(
      "testImplementation",
      target.dependencies.platform("org.junit:junit-bom:${target.catalogVersion("junit")}"),
    )
    target.dependencies.add("testImplementation", "org.junit.jupiter:junit-jupiter")
    target.dependencies.add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
    target.tasks.withType(Test::class.java).configureEach {
      it.useJUnitPlatform()
      it.maxParallelForks = 1
      it.testLogging.showExceptions = true
      it.testLogging.showCauses = true
      it.testLogging.showStackTraces = true
      it.testLogging.exceptionFormat = TestExceptionFormat.FULL
    }

    if (target.name in ProductionDependencyRules.allowed.keys) {
      target.tasks.register("verifyProductionDependencies", VerifyProductionDependencies::class.java) {
        it.group = "verification"
        it.description = "Checks direct production project dependencies against the allowed module graph."
      }
      target.tasks.named("check").configure {
        it.dependsOn("verifyProductionDependencies")
      }
    }
  }
}

internal fun Project.catalogVersion(name: String): String =
  extensions
    .getByType(VersionCatalogsExtension::class.java)
    .named("libs")
    .findVersion(name)
    .get()
    .requiredVersion
