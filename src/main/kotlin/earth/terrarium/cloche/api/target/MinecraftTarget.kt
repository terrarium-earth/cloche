package earth.terrarium.cloche.api.target

import earth.terrarium.cloche.api.MappingsBuilder
import earth.terrarium.cloche.api.run.RunConfigurations
import earth.terrarium.cloche.api.target.compilation.TargetSecondarySourceSets
import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Action
import org.gradle.api.file.Directory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal



@JvmDefaultWithoutCompatibility
interface MinecraftTarget<TMetadata : Any> : ClocheTarget, TargetSecondarySourceSets {
    val minecraftVersion: Property<String>
        @Input get

    val loaderVersion: Property<String>
        @Input get

    val metadataDirectory: Provider<Directory>
        @Internal
        get() = project.layout.buildDirectory.dir("generated").map { it.dir("metadata").dir(featureName) }

    val metadata: TMetadata

    fun mappings(action: Action<MappingsBuilder>)

    fun metadata(action: Action<TMetadata>) =
        action.execute(metadata)

    fun runs(action: Action<RunConfigurations>)

    fun runs(@DelegatesTo(RunConfigurations::class) closure: Closure<*>) = runs(closure::call)
}
