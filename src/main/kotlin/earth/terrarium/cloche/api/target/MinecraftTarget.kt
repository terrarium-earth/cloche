package earth.terrarium.cloche.api.target

import earth.terrarium.cloche.ClochePlugin
import earth.terrarium.cloche.api.MappingsBuilder
import earth.terrarium.cloche.api.run.RunConfigurations
import earth.terrarium.cloche.api.target.compilation.TargetSecondarySourceSets
import groovy.lang.Closure
import groovy.lang.DelegatesTo
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import org.gradle.api.Action
import org.gradle.api.artifacts.dsl.Dependencies
import org.gradle.api.artifacts.dsl.DependencyCollector
import org.gradle.api.file.Directory
import org.gradle.api.plugins.jvm.PlatformDependencyModifiers
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal

@Suppress("UnstableApiUsage")
@JvmDefaultWithoutCompatibility
interface MinecraftTarget<TMetadata : Any> : ClocheTarget, TargetSecondarySourceSets, Dependencies, PlatformDependencyModifiers {
    val minecraftVersion: Property<String>
        @Input get

    val loaderVersion: Property<String>
        @Input get

    val metadataDirectory: Provider<Directory>
        @Internal
        get() = project.layout.buildDirectory.dir("generated").map { it.dir("metadata").dir(featureName) }

    val datagenDirectory: Provider<Directory>
        @Internal
        get() = project.layout.buildDirectory.dir("generated").map {
            it.dir("resources").dir(target.featureName)
        }

    val datagenClientDirectory: Provider<Directory>
        @Internal
        get() = project.layout.buildDirectory.dir("generated").map {
            it.dir("resources").dir(lowerCamelCaseGradleName(target.featureName, ClochePlugin.CLIENT_COMPILATION_NAME))
        }

    val metadata: TMetadata

    val include: DependencyCollector

    fun mappings(action: Action<MappingsBuilder>)

    fun metadata(action: Action<TMetadata>) =
        action.execute(metadata)

    fun runs(action: Action<RunConfigurations>)

    fun runs(@DelegatesTo(RunConfigurations::class) closure: Closure<*>) = runs(closure::call)
}
