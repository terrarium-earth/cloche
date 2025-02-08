package earth.terrarium.cloche.api

import net.msrandom.minecraftcodev.core.task.ResolveMinecraftMappings
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency

typealias MappingDependencyProvider = (minecraftVersion: String, targetFeatureName: String) -> Dependency

class MappingsBuilder(private val project: Project, private val dependencies: MutableList<MappingDependencyProvider>) {
    fun official(minecraftVersion: String? = null) {
        dependencies.add { resolvedMinecraftVersion, targetFeatureName ->
            val version = minecraftVersion ?: resolvedMinecraftVersion

            // TODO Should always be registered, but just unused if mappings are configured
            val taskName = lowerCamelCaseGradleName("resolve", targetFeatureName, "clientMappings")

            val task = if (taskName in project.tasks.names) {
                project.tasks.named(taskName, ResolveMinecraftMappings::class.java)
            } else {
                project.tasks.register(taskName, ResolveMinecraftMappings::class.java) {
                    it.group = "minecraft-resolution"

                    it.server.set(false)
                    it.version.set(version)
                }
            }

            project.dependencies.create(project.files(task.flatMap(ResolveMinecraftMappings::output)))
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
