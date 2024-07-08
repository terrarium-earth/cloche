package earth.terrarium.cloche.target

import net.msrandom.minecraftcodev.core.task.DownloadMinecraftMappings
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency

typealias MappingDependencyProvider = (minecraftVersion: String) -> Dependency

class MappingsBuilder(private val project: Project, private val dependencies: MutableList<MappingDependencyProvider>) {
    fun official(minecraftVersion: String? = null) {
        dependencies.add {
            val version = minecraftVersion ?: it

            val task = project.tasks.maybeCreate("download${version}ClientMappings", DownloadMinecraftMappings::class.java).apply {
                server.set(false)
                this.version.set(version)
            }

            project.dependencies.create(project.files(task.output))
        }
    }

    fun parchment(version: String, minecraftVersion: String? = null) {
        dependencies.add { project.dependencies.create("org.parchmentmc.data:parchment-${minecraftVersion ?: it}:$version") }
    }

    fun custom(dependency: Dependency) {
        dependencies.add { dependency }
    }

    fun custom(dependency: MappingDependencyProvider) {
        dependencies.add(dependency)
    }
}
