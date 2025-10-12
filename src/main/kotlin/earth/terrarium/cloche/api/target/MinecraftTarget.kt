package earth.terrarium.cloche.api.target

import earth.terrarium.cloche.api.MappingsBuilder
import earth.terrarium.cloche.api.metadata.Metadata
import earth.terrarium.cloche.api.run.RunConfigurations
import earth.terrarium.cloche.api.target.compilation.TargetSecondarySourceSets
import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.artifacts.dsl.Dependencies
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.plugins.jvm.PlatformDependencyModifiers
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.bundling.Jar

@Suppress("UnstableApiUsage")
@JvmDefaultWithoutCompatibility
interface MinecraftTarget : ClocheTarget, TargetSecondarySourceSets, Dependencies, PlatformDependencyModifiers {
    override val minecraftVersion: Property<String>
        @Input get

    val loaderVersion: Property<String>
        @Input get

    val metadata: Metadata

    val metadataDirectory: Provider<Directory>
        @Internal
        get() = project.layout.buildDirectory.dir("generated").map { it.dir("metadata").dir(featureName) }

    val datagenDirectory: DirectoryProperty
        @Optional
        @Input
        get

    val datagenClientDirectory: DirectoryProperty
        @Optional
        @Input
        get

    val finalJar: Provider<out Jar>

    val withMixinAgent: Property<Boolean>
        @Optional
        @Input
        get

    fun mappings(action: Action<MappingsBuilder>)

    fun runs(action: Action<RunConfigurations>)

    fun runs(@DelegatesTo(RunConfigurations::class) closure: Closure<*>) = runs {
        closure.rehydrate(it, this, this).call()
    }
}
