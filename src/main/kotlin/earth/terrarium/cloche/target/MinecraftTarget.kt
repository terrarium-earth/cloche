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
interface MinecraftTarget : ClocheTarget, Compilation {
    val minecraftVersion: Property<String>
        @Input
        get

    val loaderVersion: Property<String>
        @Input get

    val datagenDirectory: Provider<Directory>
        @Internal
        get() = project.layout.buildDirectory.dir("generatedResources/${name}")

    val main: Compilation
    val data: RunnableCompilation?
    val server: Runnable?
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

    fun data() = data(null)
    fun data(action: Action<RunnableCompilation>?)

    fun server() = server(null)
    fun server(action: Action<Runnable>?)

    fun mappings(action: Action<MappingsBuilder>)
}

internal interface MinecraftTargetInternal : MinecraftTarget {
    override val main: TargetCompilation
    override var data: RunnableTargetCompilation?
    override var server: SimpleRunnable?
    override val client: RunnableInternal?

    val loaderAttributeName: String
    val commonType: String

    val compilations: DomainObjectCollection<TargetCompilation>
    val runnables: DomainObjectCollection<RunnableInternal>

    val remapNamespace: Provider<String>
        @Internal get

    fun createData(): RunnableTargetCompilation

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
