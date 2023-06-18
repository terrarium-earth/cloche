package earth.terrarium.cloche.target

import net.msrandom.minecraftcodev.core.MinecraftCodevExtension
import net.msrandom.minecraftcodev.core.MinecraftType
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency

typealias MappingDependencyProvider = (minecraftVersion: String) -> Dependency;

class MappingsBuilder(private val project: Project, private val dependencies: MutableList<MappingDependencyProvider>) {
    fun official() {
        val codev = project.extensions.getByType(MinecraftCodevExtension::class.java)

        dependencies.add { codev(MinecraftType.ServerMappings, it) }
        dependencies.add { codev(MinecraftType.ClientMappings, it) }
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
