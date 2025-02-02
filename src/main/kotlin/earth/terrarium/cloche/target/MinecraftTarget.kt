package earth.terrarium.cloche.target

import earth.terrarium.cloche.ClocheDependencyHandler
import net.msrandom.minecraftcodev.remapper.task.LoadMappings
import org.gradle.api.Action
import org.gradle.api.DomainObjectCollection
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.Directory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.language.jvm.tasks.ProcessResources

@JvmDefaultWithoutCompatibility
interface MinecraftTarget<TMetadata : Any> : ClocheTarget, Compilation {
    val minecraftVersion: Property<String>
        @Input
        get

    val loaderVersion: Property<String>
        @Input get

    val datagenDirectory: Provider<Directory>
        @Internal
        get() = project.layout.buildDirectory.dir("generated").map { it.dir("resources").dir(featureName) }

    val metadataDirectory: Provider<Directory>
        @Internal
        get() = project.layout.buildDirectory.dir("generated").map { it.dir("metadata").dir(featureName) }

    val loaderName: String

    val main: Compilation
    val data: RunnableCompilation?

    val server: Runnable?

    /**
     * null if neither runnable nor a source set
     * Runnable if only runnable but not a source set
     * RunnableCompilation if runnable and a source set
     */
    val client: Runnable?

    override val accessWideners get() = main.accessWideners
    override val mixins get() = main.mixins
    override val sourceSet get() = main.sourceSet

    override fun withJavadocJar() = main.withJavadocJar()
    override fun withSourcesJar() = main.withSourcesJar()

    override fun dependencies(action: Action<ClocheDependencyHandler>) =
        main.dependencies(action)

    override fun attributes(action: Action<AttributeContainer>) =
        main.attributes(action)

    val metadata: TMetadata

    fun data() = data(null)
    fun data(action: Action<RunnableCompilation>?)

    fun server() = server(null)
    fun server(action: Action<Runnable>?)

    fun mappings(action: Action<MappingsBuilder>)

    fun metadata(action: Action<TMetadata>) {
        action.execute(metadata)
    }
}

internal interface MinecraftTargetInternal<TMetadata : Any> : MinecraftTarget<TMetadata> {
    override val main: TargetCompilation
    override var data: RunnableTargetCompilation?
    override var server: SimpleRunnable?
    override val client: RunnableInternal?

    val hasIncludedClient
        get() = client is SimpleRunnable

    val commonType: String

    val compilations: DomainObjectCollection<TargetCompilation>
    val runnables: DomainObjectCollection<RunnableInternal>

    val remapNamespace: Provider<String>
        @Internal get

    val loadMappings: Provider<LoadMappings>
        @Internal get

    val mappingsBuildDependenciesHolder: Configuration

    fun registerAccessWidenerMergeTask(compilation: CompilationInternal)

    fun addJarInjects(compilation: CompilationInternal) {}

    fun createData(): RunnableTargetCompilation

    override fun data(action: Action<RunnableCompilation>?) {
        if (data == null) {
            val data = createData()

            data.runConfiguration { datagen ->
                runnables.all {
                    if (it.name != data.name) {
                        it.runConfiguration {
                            it.dependsOn(datagen)
                        }
                    }
                }

                compilations.all {
                    if (it.name != data.name) {
                        project.tasks.named(it.sourceSet.processResourcesTaskName, ProcessResources::class.java) {
                            it.from(datagenDirectory)
                        }
                    }
                }
            }

            compilations.add(data)
            runnables.add(data)

            this.data = data
        }

        action?.execute(data!!)
    }

    fun initialize(isSingleTarget: Boolean)
}
