package earth.terrarium.cloche.target

import earth.terrarium.cloche.ClocheDependencyHandler
import net.msrandom.minecraftcodev.runs.MinecraftRunConfigurationBuilder
import org.gradle.api.Action
import org.gradle.api.DomainObjectCollection
import org.gradle.api.NamedDomainObjectCollection
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
    val data: RunnableCompilation?
    val client: Runnable?

    override val accessWideners get() =
        main.accessWideners

    override val mixins get() =
        main.mixins

    override fun withJavadocJar() = main.withJavadocJar()
    override fun withSourcesJar() = main.withSourcesJar()

    override fun dependencies(action: Action<ClocheDependencyHandler>) =
        main.dependencies(action)

    override fun attributes(action: Action<AttributeContainer>) =
        main.attributes(action)

    override fun runConfiguration(action: Action<MinecraftRunConfigurationBuilder>) =
        main.runConfiguration(action)

    fun data() = data(null)

    fun data(action: Action<RunnableCompilation>?)

    fun mappings(action: Action<MappingsBuilder>)
}

internal interface MinecraftTargetInternal : MinecraftTarget {
    override val main: RunnableCompilationInternal
    override var data: RunnableCompilationInternal?

    val loaderAttributeName: String
    val commonType: String

    val compilations: DomainObjectCollection<RunnableCompilationInternal>
    val runnables: DomainObjectCollection<RunnableInternal>

    val remapNamespace: Provider<String>
        @Internal get

    fun createData(): RunnableCompilationInternal

    override fun data(action: Action<RunnableCompilation>?) {
        if (data == null) {
            data = createData()

            compilations.add(data)
            runnables.add(data)
        }

        action?.execute(data!!)
    }

    fun initialize(isSingleTarget: Boolean)
}
