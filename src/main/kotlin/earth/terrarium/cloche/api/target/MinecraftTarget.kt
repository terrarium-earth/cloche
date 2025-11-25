package earth.terrarium.cloche.api.target

import earth.terrarium.cloche.api.MappingsBuilder
import earth.terrarium.cloche.api.metadata.CommonMetadata
import earth.terrarium.cloche.api.run.RunConfigurations
import earth.terrarium.cloche.api.target.compilation.CommonSecondarySourceSets
import earth.terrarium.cloche.loader
import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Action
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.plugins.jvm.PlatformDependencyModifiers
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.bundling.Jar

@Suppress("UnstableApiUsage")
@JvmDefaultWithoutCompatibility
interface MinecraftTarget : ClocheTarget, CommonSecondarySourceSets, PlatformDependencyModifiers {
    override val minecraftVersion: Property<String>
        @Input get

    val loaderVersion: Property<String>
        @Input get

    override val loaderName
        get() = loader(javaClass).name

    val metadata: CommonMetadata

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
        val owner = this@MinecraftTarget

        closure.rehydrate(this, owner, owner).call()
    }
}
