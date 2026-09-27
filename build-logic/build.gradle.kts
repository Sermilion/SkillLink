plugins {
    base
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

spotless {
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
