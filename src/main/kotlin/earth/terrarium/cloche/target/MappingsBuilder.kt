package earth.terrarium.cloche.target

import net.msrandom.minecraftcodev.core.task.ResolveMinecraftMappings
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency

typealias MappingDependencyProvider = (minecraftVersion: String, targetName: String) -> Dependency

class MappingsBuilder(private val project: Project, private val dependencies: MutableList<MappingDependencyProvider>) {
    fun official(minecraftVersion: String? = null) {
        dependencies.add { resolvedMinecraftVersion, targetName ->
            val version = minecraftVersion ?: resolvedMinecraftVersion

            val taskName = lowerCamelCaseGradleName("resolve", targetName, "clientMappings")

            val task = project.tasks.withType(ResolveMinecraftMappings::class.java).findByName(taskName)
                ?: project.tasks.create(taskName, ResolveMinecraftMappings::class.java) {
                    it.server.set(false)
                    it.version.set(version)
                }

            project.dependencies.create(project.files(task.output))
        }
    }

    fun parchment(version: String, minecraftVersion: String? = null) {
        dependencies.add { resolvedMinecraftVersion, _ ->
            project.dependencies.create("org.parchmentmc.data:parchment-${minecraftVersion ?: resolvedMinecraftVersion}:$version")
        }
    }

    fun custom(dependency: Dependency) {
        dependencies.add { _, _ -> dependency }
    }

    fun custom(dependency: MappingDependencyProvider) {
        dependencies.add(dependency)
    }
}
