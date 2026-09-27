import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("skilllink.jvm-library")
    id("skilllink.quality")
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(project(":desktop"))
    implementation(project(":infrastructure"))
    implementation(project(":application"))
}

compose.desktop {
    application {
        mainClass = "skilllink.app.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "SkillLink"
            packageVersion = "1.0.0"
            description = "One skill library for your AI agents"
            vendor = "SkillLink"
            modules("java.sql")
            linux {
                packageName = "skilllink"
            }
            windows {
                menuGroup = "SkillLink"
                shortcut = true
                upgradeUuid = "28110ab5-c35d-4e0b-a05c-f1d277b5a76e"
            }
            macOS {
                bundleID = "dev.skilllink.desktop"
            }
        }
    }
}
