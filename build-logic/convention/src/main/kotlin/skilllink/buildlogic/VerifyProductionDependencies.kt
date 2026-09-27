package skilllink.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Checks the current project's dependency declarations on each invocation")
abstract class VerifyProductionDependencies : DefaultTask() {
    @TaskAction
    fun verify() {
        val source = project.name
        val allowed = ProductionDependencyRules.allowed.getValue(source)
        val visited = mutableSetOf<String>()
        val violations = mutableListOf<String>()

        fun inspect(configuration: Configuration) {
            if (!visited.add(configuration.name)) return
            configuration.dependencies.withType(ProjectDependency::class.java).forEach { dependency ->
                val target = dependency.path.substringAfterLast(':')
                if (target !in allowed) {
                    violations += "$source -> $target through ${configuration.name}"
                }
            }
            configuration.extendsFrom.forEach(::inspect)
        }
        project.mainSourceSetConfigurations().forEach(::inspect)
        if (violations.isNotEmpty()) {
            val message = violations.distinct().joinToString("\n")
            throw GradleException("Forbidden production project dependencies:\n$message")
        }
    }
}
