@file:Suppress("UnstableApiUsage")

package earth.terrarium.cloche.target

import earth.terrarium.cloche.ClochePlugin
import earth.terrarium.cloche.api.MappingsBuilder
import earth.terrarium.cloche.api.attributes.TargetAttributes
import earth.terrarium.cloche.api.officialMappingsDependency
import earth.terrarium.cloche.api.run.RunConfigurations
import earth.terrarium.cloche.api.target.CommonTarget
import earth.terrarium.cloche.api.target.MinecraftTarget
import earth.terrarium.cloche.api.target.compilation.ClocheDependencyHandler
import earth.terrarium.cloche.javaExecutableFor
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.includes.IncludesJar
import net.msrandom.minecraftcodev.remapper.mappingsConfigurationName
import net.msrandom.minecraftcodev.remapper.task.LoadMappings
import org.gradle.api.Action
import org.gradle.api.DomainObjectCollection
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.dsl.DependencyCollector
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskProvider

internal fun Configuration.addCollectedDependencies(collector: DependencyCollector) {
    dependencies.addAllLater(collector.dependencies)
    dependencyConstraints.addAllLater(collector.dependencyConstraints)
}

internal abstract class MinecraftTargetInternal(
    private val name: String,
) : MinecraftTarget, ClocheTargetInternal {
    abstract val main: TargetCompilation
    abstract override val data: LazyConfigurableInternal<TargetCompilation>
    abstract override val test: LazyConfigurableInternal<TargetCompilation>

    abstract val commonType: String

    override val dependsOn: DomainObjectCollection<CommonTarget> =
        project.objects.domainObjectSet(CommonTarget::class.java)

    open val modRemapNamespace: Provider<String>
        @Internal get() = minecraftRemapNamespace

    abstract val minecraftRemapNamespace: Provider<String>
        @Internal get

    abstract val runs: RunConfigurations

    val loadMappingsTask: TaskProvider<LoadMappings> =
        project.tasks.register(lowerCamelCaseGradleName("load", name, "mappings"), LoadMappings::class.java) {
            it.mappings.from(project.configurations.named(sourceSet.mappingsConfigurationName))

            it.javaExecutable.set(project.javaExecutableFor(minecraftVersion, it.cacheParameters))
        }

    val mappingsBuildDependenciesHolder: Configuration =
        project.configurations.detachedConfiguration(
            project.dependencies.create(
                project.files().builtBy(loadMappingsTask)
            )
        )

    override val accessWideners get() = main.accessWideners
    override val mixins get() = main.mixins
    override val sourceSet get() = main.sourceSet

    override val target get() = this

    val outputDirectory: Provider<Directory> =
        project.layout.buildDirectory.dir("minecraft").map { it.dir(capabilitySuffix) }

    protected val mappings = MappingsBuilder(this, project)

    @Suppress("UNCHECKED_CAST")
    private val mappingActions =
        project.objects.domainObjectSet(Action::class.java) as DomainObjectCollection<Action<MappingsBuilder>>

    init {
        datagenDirectory.convention(project.layout.buildDirectory.dir("generated").map {
            it.dir("resources").dir(target.featureName)
        })

        datagenClientDirectory.convention(project.layout.buildDirectory.dir("generated").map {
            it.dir("resources").dir(lowerCamelCaseGradleName(target.featureName, ClochePlugin.CLIENT_COMPILATION_NAME))
        })

        withMixinAgent.convention(false)

        project.afterEvaluate {
            if (!loaderVersion.isPresent) {
                throw InvalidUserCodeException("loaderVersion not set for target '$name'")
            }
        }
    }

    override fun withJavadocJar() = main.withJavadocJar()
    override fun withSourcesJar() = main.withSourcesJar()

    override fun dependencies(action: Action<ClocheDependencyHandler>) = main.dependencies(action)
    override fun attributes(action: Action<AttributeContainer>) = main.attributes(action)

    fun resolvableAttributes(action: Action<AttributeContainer>) = main.resolvableAttributes(action)

    abstract fun registerAccessWidenerMergeTask(compilation: CompilationInternal)

    // TODO This is temporarily unused
    abstract fun addAnnotationProcessors(compilation: CompilationInternal)

    open fun addJarInjects(compilation: CompilationInternal) {}

    override fun mappings(action: Action<MappingsBuilder>) {
        mappingActions.add(action)
    }

    fun attributes(attributes: AttributeContainer) {
        attributes
            .attribute(TargetAttributes.MOD_LOADER, target.loaderName)
            .attributeProvider(TargetAttributes.MINECRAFT_VERSION, target.minecraftVersion)
    }

    protected fun registerMappings() {
        mappingActions.all { action ->
            action.execute(mappings)
        }

        project.configurations.named(sourceSet.mappingsConfigurationName) {
            it.dependencies.addAllLater(mappings.isConfigured.map {
                if (it) {
                    emptyList()
                } else {
                    listOf(project.dependencies.create(officialMappingsDependency(project, this)))
                }
            })
        }
    }

    abstract fun onClientIncluded(action: () -> Unit)

    abstract fun initialize(isSingleTarget: Boolean)

    override fun runs(action: Action<RunConfigurations>) {
        action.execute(runs)
    }

    override fun getName() = name

    override fun toString() = name
}
