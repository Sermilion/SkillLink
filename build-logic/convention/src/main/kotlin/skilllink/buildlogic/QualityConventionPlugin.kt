package skilllink.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

class QualityConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.pluginManager.apply("com.diffplug.spotless")
    target.pluginManager.apply("io.gitlab.arturbosch.detekt")
    target.configureQualityChecks()
  }
}
