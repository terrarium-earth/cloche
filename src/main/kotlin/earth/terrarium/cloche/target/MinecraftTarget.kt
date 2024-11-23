package earth.terrarium.cloche.target

import earth.terrarium.cloche.ClocheDependencyHandler
import net.msrandom.minecraftcodev.runs.MinecraftRunConfigurationBuilder
import org.gradle.api.Action
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.Directory
import org.gradle.api.plugins.FeatureSpec
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal

@JvmDefaultWithoutCompatibility
interface MinecraftTarget : ClocheTarget, RunnableCompilation, Compilation {
    val minecraftVersion: Property<String>
        @Input
        get

    val loaderVersion: Property<String>
        @Input get

    val datagenDirectory: Provider<Directory>
        @Internal
        get() = project.layout.buildDirectory.dir("generatedResources/${name}")

    val main: RunnableCompilation
    val data: RunnableCompilation
    val client: Runnable

    override val accessWideners get() =
        main.accessWideners

    override val mixins get() =
        main.mixins

    override fun dependencies(action: Action<ClocheDependencyHandler>) =
        main.dependencies(action)

    override fun java(action: Action<FeatureSpec>) =
        main.java(action)

    override fun attributes(action: Action<AttributeContainer>) =
        main.attributes(action)

    override fun runConfiguration(action: Action<MinecraftRunConfigurationBuilder>) =
        main.runConfiguration(action)

    fun data() = data(null)

    fun data(action: Action<RunnableCompilation>?) {
        action?.execute(data)
    }

    fun mappings(action: Action<MappingsBuilder>)
}

internal interface MinecraftTargetInternal : MinecraftTarget {
    override val main: RunnableCompilationInternal
    override val data: RunnableCompilationInternal

    val loaderAttributeName: String
    val commonType: String

    val compilations: List<RunnableCompilationInternal>
    val runnables: List<RunnableInternal>

    fun initialize(isSingleTarget: Boolean)
}
