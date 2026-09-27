import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.application.CreateStartScripts
import org.gradle.jvm.tasks.Jar
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("skilllink.jvm-library")
    id("skilllink.quality")
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(project(":cli"))
    implementation(project(":desktop"))
    implementation(project(":infrastructure"))
    implementation(project(":application"))
}

val skillLinkCliMainClass = "skilllink.app.SkillLinkCliMainKt"

tasks.register<JavaExec>("runSkillLinkCli") {
    group = "application"
    description = "Run the skill-link CLI entry point"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(skillLinkCliMainClass)
}

tasks.register<CreateStartScripts>("skillLinkCliStartScripts") {
    group = "distribution"
    description = "Create skill-link and skill-link.bat launch scripts"
    applicationName = "skill-link"
    mainClass.set(skillLinkCliMainClass)
    classpath = files(tasks.named<Jar>("jar"), configurations.runtimeClasspath)
    dependsOn(tasks.named<Jar>("jar"))
    outputDir =
        layout.buildDirectory
            .dir("skill-link-cli/bin")
            .get()
            .asFile
    defaultJvmOpts = listOf("-Xmx256m")
}

tasks.register<Copy>("skillLinkCliDistribution") {
    group = "distribution"
    description = "Assemble the standalone skill-link CLI distribution"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    dependsOn("skillLinkCliStartScripts", tasks.named<Jar>("jar"))
    from(tasks.named("skillLinkCliStartScripts"))
    from(tasks.named<Jar>("jar")) {
        into("lib")
    }
    from(configurations.runtimeClasspath) {
        into("lib")
    }
    into(layout.buildDirectory.dir("skill-link-cli"))
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
