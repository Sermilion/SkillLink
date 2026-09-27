package skilllink.buildlogic

import com.diffplug.gradle.spotless.SpotlessExtension
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Project

internal fun Project.configureQualityChecks() {
    extensions.configure(SpotlessExtension::class.java) { spotless ->
        spotless.kotlin { kotlin ->
            kotlin.target("src/**/*.kt")
            kotlin.ktlint(catalogVersion("ktlint"))
        }
        spotless.kotlinGradle { scripts ->
            if (path == ":") {
                scripts.target(
                    fileTree(rootDir) { tree ->
                        tree.include("*.gradle.kts")
                        tree.exclude("**/build/**")
                    },
                )
            } else {
                scripts.target(buildFile)
            }
            scripts.ktlint(catalogVersion("ktlint"))
        }
    }
    extensions.configure(DetektExtension::class.java) { detekt ->
        detekt.config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        detekt.buildUponDefaultConfig = true
        detekt.toolVersion = catalogVersion("detekt")
    }
}
