package skilllink.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.SourceSetContainer

internal const val JVM_VERSION = 21

internal fun Project.mainSourceSetConfigurations(): List<Configuration> {
  val sourceSets = extensions.getByType(SourceSetContainer::class.java)
  val main = sourceSets.getByName("main")
  return listOf(main.compileClasspathConfigurationName, main.runtimeClasspathConfigurationName)
    .mapNotNull(configurations::findByName)
}
