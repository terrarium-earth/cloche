package earth.terrarium.cloche.api

import earth.terrarium.cloche.api.target.MinecraftTarget
import earth.terrarium.cloche.api.attributes.DependencyNamespaceAttribute
import earth.terrarium.cloche.target.MinecraftTargetInternal
import earth.terrarium.cloche.util.maybeRegister
import net.msrandom.minecraftcodev.core.task.ResolveMinecraftMappings
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.fabric.MinecraftCodevFabricPlugin
import net.msrandom.minecraftcodev.remapper.mappingsConfigurationName
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.domainObjectSet
import org.gradle.kotlin.dsl.property

internal fun officialMappingsDependency(project: Project, target: MinecraftTarget): FileCollection {
    val taskName = lowerCamelCaseGradleName("resolve", target.featureName, "clientMappings")

    val task = project.tasks.maybeRegister<ResolveMinecraftMappings>(taskName) {
        group = "minecraft-resolution"

        server.set(false)

        minecraftVersion.set(target.minecraftVersion)
    }

    return project.files(task.flatMap(ResolveMinecraftMappings::output))
}

class MappingsBuilder internal constructor(
    private val target: MinecraftTargetInternal,
    private val project: Project,
) {
    private val _isOfficialCompatible: Property<Boolean> =
        project.objects.property<Boolean>().apply {
            convention(true)
        }

    private val _isDefault: Property<Boolean> =
        project.objects.property<Boolean>().apply {
            convention(true)
        }

    private val _isConfigured: Property<Boolean> =
        project.objects.property<Boolean>().apply {
            convention(false)
        }

    private val _sourceNamespaces: DomainObjectCollection<String> =
        project.objects.domainObjectSet(String::class)

    internal val isOfficialCompatible: Provider<Boolean>
        get() = _isOfficialCompatible

    internal val isDefault: Provider<Boolean>
        get() = _isDefault

    internal val isConfigured: Provider<Boolean>
        get() = _isConfigured

    internal val sourceNamespaces: DomainObjectCollection<String>
        get() = _sourceNamespaces

    private val configurationName
        get() = target.sourceSet.mappingsConfigurationName

    fun official() {
        configure()
        sourceNamespace(DependencyNamespaceAttribute.OBF)

        project.dependencies.add(configurationName, officialMappingsDependency(project, target))
    }

    fun parchment(version: String) {
        officialCompatible()

        project.dependencies.addProvider(configurationName, target.minecraftVersion.map {
            parchmentDependency(it, version)
        })
    }

    fun parchment(version: Provider<String>) {
        officialCompatible()

        project.dependencies.addProvider(
            configurationName,
            target.minecraftVersion.zip(version) { minecraftVersion, version ->
                parchmentDependency(minecraftVersion, version)
            },
        )
    }

    fun parchment(version: String, minecraftVersion: String) {
        officialCompatible()

        project.dependencies.add(configurationName, parchmentDependency(minecraftVersion, version))
    }

    fun parchment(version: Provider<String>, minecraftVersion: String) {
        officialCompatible()

        project.dependencies.addProvider(configurationName, version.map {
            parchmentDependency(minecraftVersion, it)
        })
    }

    fun parchment(version: String, minecraftVersion: Provider<String>) {
        officialCompatible()

        project.dependencies.addProvider(configurationName, minecraftVersion.orElse(target.minecraftVersion).map {
            parchmentDependency(it, version)
        })
    }

    fun parchment(version: Provider<String>, minecraftVersion: Provider<String>) {
        officialCompatible()

        project.dependencies.addProvider(
            configurationName,
            minecraftVersion.orElse(target.minecraftVersion).zip(version) { minecraftVersion, version ->
                parchmentDependency(minecraftVersion, version)
            },
        )
    }

    fun yarn(version: String) {
        officialIncompatible()

        project.dependencies.addProvider(
            configurationName,
            target.minecraftVersion.map { minecraftVersion ->
                yarnDependency(minecraftVersion, version)
            },
        )
    }

    fun yarn(version: Provider<String>) {
        officialIncompatible()

        project.dependencies.addProvider(
            configurationName,
            target.minecraftVersion.zip(version) { version, minecraftVersion ->
                yarnDependency(minecraftVersion, version)
            },
        )
    }

    fun fabricIntermediary() {
        sourceNamespaces(
            DependencyNamespaceAttribute.OBF,
            DependencyNamespaceAttribute.INTERMEDIARY,
        )

        project.dependencies.addProvider(
            configurationName,
            target.minecraftVersion.map {
                "net.fabricmc:${MinecraftCodevFabricPlugin.INTERMEDIARY_MAPPINGS_NAMESPACE}:$it:v2"
            }
        )
    }

    fun mcpSearge() {
        seargeNamespaces()

        project.dependencies.addProvider(
            configurationName,
            target.minecraftVersion.map {
                "de.oceanlabs.mcp:mcp_config:$it"
            }
        )
    }

    internal fun legacyMcpSearge() {
        seargeNamespaces()

        project.dependencies.addProvider(
            configurationName,
            target.minecraftVersion.map {
                "de.oceanlabs.mcp:mcp:$it:srg"
            }
        )
    }

    /**
     * @param timestamp The timestamp of the NeoForm. Get from https://projects.neoforged.net/neoforged/neoform
     */
    fun neoforgeSearge(timestamp: String) {
        seargeNamespaces()

        project.dependencies.addProvider(
            configurationName,
            target.minecraftVersion.map {
                "net.neoforged:neoform:$it-$timestamp@zip"
            }
        )
    }

    fun custom(dependency: Dependency) {
        officialIncompatible()

        project.dependencies.add(configurationName, dependency)
    }

    fun custom(dependency: Provider<Dependency>) {
        officialIncompatible()

        project.dependencies.addProvider(configurationName, dependency)
    }

    private fun officialIncompatible() {
        officialCompatible()

        _isOfficialCompatible.set(false)
    }

    private fun officialCompatible() {
        configure()

        _isDefault.set(false)
    }

    private fun configure() {
        _isConfigured.set(true)
    }

    private fun seargeNamespaces() {
        sourceNamespaces(
            DependencyNamespaceAttribute.OBF,
            DependencyNamespaceAttribute.SEARGE,
        )
    }

    private fun sourceNamespace(namespace: String) {
        _sourceNamespaces.add(namespace)
    }

    private fun sourceNamespaces(vararg namespaces: String) {
        namespaces.forEach(_sourceNamespaces::add)
    }

    private fun parchmentDependency(minecraftVersion: String, version: String) =
        project.dependencies.create("org.parchmentmc.data:parchment-$minecraftVersion:$version")

    private fun yarnDependency(minecraftVersion: String, version: String) =
        project.dependencies.create("net.fabricmc:yarn:$minecraftVersion+$version")
}
