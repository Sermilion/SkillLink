import com.diffplug.spotless.LineEnding

plugins {
  base
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.spotless)
  alias(libs.plugins.detekt)
}

spotless {
  lineEndings = LineEnding.UNIX
  kotlinGradle {
    target(
      fileTree(rootDir) {
        include("build.gradle.kts", "settings.gradle.kts")
      },
    )
    ktlint(libs.versions.ktlint.get())
  }
}

detekt {
  config.setFrom(files("../config/detekt/detekt.yml"))
  buildUponDefaultConfig = true
  toolVersion = libs.versions.detekt.get()
}

tasks.named("check") {
  dependsOn(":convention:check")
}
