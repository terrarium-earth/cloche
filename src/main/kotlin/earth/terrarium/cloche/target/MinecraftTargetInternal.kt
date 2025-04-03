package earth.terrarium.cloche.target

import earth.terrarium.cloche.api.MappingDependencyProvider
import earth.terrarium.cloche.api.MappingsBuilder
import earth.terrarium.cloche.api.run.RunConfigurations
import earth.terrarium.cloche.api.target.CommonTarget
import earth.terrarium.cloche.api.target.MinecraftTarget
import earth.terrarium.cloche.api.target.compilation.ClocheDependencyHandler
import earth.terrarium.cloche.javaExecutableFor
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.remapper.mappingsConfigurationName
import net.msrandom.minecraftcodev.remapper.task.LoadMappings
import org.gradle.api.Action
import org.gradle.api.DomainObjectCollection
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.dsl.DependencyCollector
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar

internal fun Configuration.addCollectedDependencies(collector: DependencyCollector) {
    dependencies.addAllLater(collector.dependencies)
    dependencyConstraints.addAllLater(collector.dependencyConstraints)
}

internal abstract class MinecraftTargetInternal<TMetadata : Any>(private val name: String) : MinecraftTarget<TMetadata> {
    abstract val main: TargetCompilation
    abstract override val data: LazyConfigurableInternal<TargetCompilation>
    abstract override val test: LazyConfigurableInternal<TargetCompilation>

    abstract val commonType: String

    override val dependsOn: DomainObjectCollection<CommonTarget> =
        project.objects.domainObjectSet(CommonTarget::class.java)

    abstract val remapNamespace: Provider<String>
        @Internal get

    abstract val runs: RunConfigurations

    protected val hasMappings: Property<Boolean> = project.objects.property(Boolean::class.javaObjectType).convention(false)

    val loadMappingsTask: TaskProvider<LoadMappings> = project.tasks.register(lowerCamelCaseGradleName("load", name, "mappings"), LoadMappings::class.java) {
        it.mappings.from(project.configurations.named(sourceSet.mappingsConfigurationName))

        it.javaExecutable.set(project.javaExecutableFor(minecraftVersion, it.cacheParameters))
    }

    abstract val includeJarTask: TaskProvider<out Jar>

    val includeConfiguration: Configuration = project.configurations.create(lowerCamelCaseGradleName(target.featureName, "include")) {
        it.addCollectedDependencies(include)

        it.isCanBeConsumed = false
        it.isTransitive = false
    }

    val mappingsBuildDependenciesHolder: Configuration =
        project.configurations.detachedConfiguration(project.dependencies.create(project.files().builtBy(loadMappingsTask)))

    override val accessWideners get() = main.accessWideners
    override val mixins get() = main.mixins
    override val sourceSet get() = main.sourceSet

    override val target: MinecraftTarget<TMetadata> get() = this

    abstract val mappingProviders: ListProperty<MappingDependencyProvider>
        @Internal get

    override fun withJavadocJar() = main.withJavadocJar()
    override fun withSourcesJar() = main.withSourcesJar()

    override fun dependencies(action: Action<ClocheDependencyHandler>) = main.dependencies(action)
    override fun attributes(action: Action<AttributeContainer>) = main.attributes(action)

    abstract fun registerAccessWidenerMergeTask(compilation: CompilationInternal)

    open fun addJarInjects(compilation: CompilationInternal) {}

    override fun mappings(action: Action<MappingsBuilder>) {
        hasMappings.set(true)

        val mappings = mutableListOf<MappingDependencyProvider>()

        action.execute(MappingsBuilder(project, mappings))

        addMappings(mappings)
    }

    private fun addMappings(providers: List<MappingDependencyProvider>) {
        mappingProviders.addAll(providers)
    }

    abstract fun onClientIncluded(action: () -> Unit)

    open fun initialize(isSingleTarget: Boolean) {
        mappingProviders.addAll(hasMappings.map {
            if (it) {
                emptyList()
            } else {
                buildList { MappingsBuilder(project, this).official() }
            }
        })
    }

    override fun runs(action: Action<RunConfigurations>) {
        action.execute(runs)
    }

    override fun getName() = name

    override fun toString() = name
}
