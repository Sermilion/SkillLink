import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.kotlin.jvm)
  `java-gradle-plugin`
  alias(libs.plugins.spotless)
  alias(libs.plugins.detekt)
}

dependencies {
  implementation(libs.kotlin.gradle.plugin)
  implementation(libs.spotless.gradle.plugin)
  implementation(libs.detekt.gradle.plugin)
  testImplementation(gradleTestKit())
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testImplementation("org.junit.jupiter:junit-jupiter-params")
  testRuntimeOnly(libs.junit.platform.launcher)
}

gradlePlugin {
  plugins {
    register("jvmLibrary") {
      id = "skilllink.jvm-library"
      implementationClass = "skilllink.buildlogic.JvmLibraryConventionPlugin"
    }
    register("quality") {
      id = "skilllink.quality"
      implementationClass = "skilllink.buildlogic.QualityConventionPlugin"
    }
  }
}

kotlin {
  jvmToolchain(21)
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(21))
  withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
  options.release.set(21)
}

tasks.withType<KotlinCompile>().configureEach {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_21)
    allWarningsAsErrors.set(true)
    freeCompilerArgs.add("-Xjsr305=strict")
  }
}

tasks.withType<Test>().configureEach {
  inputs
    .files(
      fileTree("../..") {
        include("**/*.gradle.kts")
        include("gradle/libs.versions.toml", "gradle/wrapper/gradle-wrapper.properties")
        include("config/detekt/detekt.yml")
        include("build-logic/convention/src/main/**/*.kt")
        exclude("**/build/**", "**/.gradle/**")
      },
    ).withPropertyName("fixtureSources")
  useJUnitPlatform()
  maxParallelForks = 1
  testLogging {
    showExceptions = true
    showCauses = true
    showStackTraces = true
    exceptionFormat = TestExceptionFormat.FULL
  }
}

spotless {
  kotlin {
    target("src/**/*.kt")
    ktlint(libs.versions.ktlint.get())
  }
  kotlinGradle {
    target("build.gradle.kts")
    ktlint(libs.versions.ktlint.get())
  }
}

detekt {
  config.setFrom(files("../../config/detekt/detekt.yml"))
  buildUponDefaultConfig = true
  toolVersion = libs.versions.detekt.get()
}

tasks.validatePlugins {
  enableStricterValidation.set(true)
}

tasks.named("check") {
  dependsOn(tasks.validatePlugins)
}
