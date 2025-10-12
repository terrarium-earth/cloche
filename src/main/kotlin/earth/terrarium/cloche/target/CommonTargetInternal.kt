package earth.terrarium.cloche.target

import earth.terrarium.cloche.ClocheExtension
import earth.terrarium.cloche.ClochePlugin
import earth.terrarium.cloche.api.metadata.Metadata
import earth.terrarium.cloche.api.target.ClocheTarget
import earth.terrarium.cloche.api.target.compilation.ClocheDependencyHandler
import earth.terrarium.cloche.api.target.CommonTarget
import earth.terrarium.cloche.api.target.MinecraftTarget
import net.msrandom.minecraftcodev.core.utils.extension
import org.gradle.api.Action
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Project
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import javax.inject.Inject

private fun collectTargetDependencies(target: ClocheTarget): Set<CommonTarget> =
    (target.dependsOn + target.dependsOn.flatMap { collectTargetDependencies(it) }).toSet()

private fun <T> Iterable<T>.onlyValue(): T? {
    val iterator = iterator()

    if (!iterator.hasNext()) {
        return null
    }

    val value = iterator.next()

    return if (iterator.asSequence().all { it == value }) {
        // All the same value, safe to return
        value
    } else {
        // Not all values are the same
        null
    }
}

internal abstract class CommonTargetInternal @Inject constructor(
    private val name: String,
    private val project: Project,
) : CommonTarget,
    CommonSecondarySourceSetsInternal, ClocheTargetInternal {
    val main: CommonTopLevelCompilation = run {
        project.objects.newInstance(CommonTopLevelCompilation::class.java, SourceSet.MAIN_SOURCE_SET_NAME, this)
    }

    override val mixins get() = main.mixins
    override val accessWideners get() = main.accessWideners
    override val sourceSet get() = main.sourceSet
    override val data get() = main.data
    override val test get() = main.test
    override val target = this

    override val client: LazyConfigurableInternal<CommonTopLevelCompilation> = project.lazyConfigurable {
        project.objects.newInstance(
            CommonTopLevelCompilation::class.java,
            ClochePlugin.CLIENT_COMPILATION_NAME,
            this,
        )
    }

    // Not lazy as it has to happen once at configuration time
    var publish = false

    override val hasSeparateClient = client.isConfigured
    @Suppress("UNCHECKED_CAST")
    override val metadataActions: DomainObjectCollection<Action<Metadata>> =
        project.objects.domainObjectSet(Action::class.java) as DomainObjectCollection<Action<Metadata>>

    override val dependsOn: DomainObjectCollection<CommonTarget> =
        project.objects.domainObjectSet(CommonTarget::class.java)

    override val dependents: Provider<List<MinecraftTarget>> = run {
        val cloche = project.extension<ClocheExtension>()

        project.provider {
            // Evaluate targets
            cloche.targets.toList()
        }.map {
            it.filter {
                this@CommonTargetInternal in collectTargetDependencies(it)
            }
        }
    }

    override val minecraftVersions: Provider<Set<String>> = run {
        val objects = project.objects

        dependents.flatMap {
            val versions = objects.setProperty(String::class.java)

            for (dependant in it) {
                versions.add(dependant.minecraftVersion)
            }

            versions
        }
    }

    override val minecraftVersion: Provider<String> = minecraftVersions.map { versions ->
        @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        versions.onlyValue()
    }

    val commonType: Provider<String> = dependents.map { dependants ->
        @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        dependants.map { (it as MinecraftTargetInternal).commonType }.onlyValue()
    }

    init {
        dependsOn.configureEach { dependency ->
            dependency.metadataActions.forEach { metadataAction ->
                metadataActions.add(metadataAction)
            }
        }
    }

    override fun getName() = name

    override fun withJavadocJar() = main.withJavadocJar()
    override fun withSourcesJar() = main.withSourcesJar()

    override fun dependencies(action: Action<ClocheDependencyHandler>) = main.dependencies(action)
    override fun attributes(action: Action<AttributeContainer>) = main.attributes(action)

    override fun withPublication() {
        publish = true
    }

    override fun toString() = name
}
