package earth.terrarium.cloche.target

import earth.terrarium.cloche.COMMON
import earth.terrarium.cloche.ClocheDependencyHandler
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.mixins.mixinsConfigurationName
import net.msrandom.minecraftcodev.runs.MinecraftRunConfiguration
import org.gradle.api.Action
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.SourceSet
import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources

interface Compilation : Named {
    val accessWideners: ConfigurableFileCollection
        @InputFiles get

    val mixins: ConfigurableFileCollection
        @InputFiles get

    val sourceSet: SourceSet
        @Internal get

    fun withJavadocJar()
    fun withSourcesJar()

    fun dependencies(action: Action<ClocheDependencyHandler>)
    fun attributes(action: Action<AttributeContainer>)
}

@JvmDefaultWithoutCompatibility
internal interface CompilationInternal : Compilation {
    val dependencySetupActions: DomainObjectCollection<Action<ClocheDependencyHandler>>
    val attributeActions: DomainObjectCollection<Action<AttributeContainer>>

    var withJavadoc: Boolean
    var withSources: Boolean

    override fun withJavadocJar() {
        withJavadoc = true
    }

    override fun withSourcesJar() {
        withSources = true
    }

    override fun dependencies(action: Action<ClocheDependencyHandler>) {
        dependencySetupActions.add(action)
    }

    override fun attributes(action: Action<AttributeContainer>) {
        attributeActions.add(action)
    }

    fun attributes(attributes: AttributeContainer) {
        attributeActions.all {
            it.execute(attributes)
        }
    }
}

interface Runnable : Named {
    fun runConfiguration(action: Action<MinecraftRunConfiguration>)
}

internal interface RunnableInternal : Runnable {
    val runSetupActions: DomainObjectCollection<Action<MinecraftRunConfiguration>>
}

interface RunnableCompilation : Runnable, Compilation

internal fun sourceSetName(compilation: Compilation, target: ClocheTarget) = when {
    target.name == COMMON -> compilation.name
    compilation.name == SourceSet.MAIN_SOURCE_SET_NAME -> target.featureName
    else -> lowerCamelCaseGradleName(target.featureName, compilation.name)
}

fun ConfigurationContainer.withName(name: String, action: Action<Configuration>) = named(name::equals).all(action)

internal fun Project.configureSourceSet(sourceSet: SourceSet, target: ClocheTarget, compilation: Compilation, singleTarget: Boolean) {
/*    if (sourceSet.name != SourceSet.MAIN_SOURCE_SET_NAME) {
        val main = project.extension<SourceSetContainer>().getByName(SourceSet.MAIN_SOURCE_SET_NAME)

        val configurationNames = listOf(
            SourceSet::getCompileClasspathConfigurationName,
            SourceSet::getRuntimeClasspathConfigurationName,
            SourceSet::getApiElementsConfigurationName,
            SourceSet::getRuntimeElementsConfigurationName,
            SourceSet::getJavadocElementsConfigurationName,
            SourceSet::getSourcesElementsConfigurationName,
        )

        for (name in configurationNames) {
            project.configurations.withName(name(main)) { mainConfiguration ->
                project.configurations.withName(name(sourceSet)) { configuration ->
                    mainConfiguration.attributes { mainAttributes ->
                        configuration.attributes { attributes ->
                            for (attribute in mainAttributes.keySet()) {
                                // TODO This isn't live, thus new attributes won't be copied
                                @Suppress("UNCHECKED_CAST")
                                attributes.attributeProvider(attribute as Attribute<Any>, provider { mainAttributes.getAttribute(attribute)!! })
                            }
                        }
                    }
                }
            }
        }
    }*/

    if (!singleTarget) {
        val compilationDirectory = project.layout.projectDirectory.dir("src").dir(target.namePath).dir(compilation.name)

        sourceSet.java.srcDir(compilationDirectory.dir("java"))
        sourceSet.resources.srcDir(compilationDirectory.dir("resources"))

        plugins.withId("org.jetbrains.kotlin.jvm") {
            val kotlin = (sourceSet.extensions.getByName("kotlin") as SourceDirectorySet)

            kotlin.srcDir(compilationDirectory.dir("kotlin"))
        }

        // TODO Groovy + Scala?
    }

    tasks.named(sourceSet.jarTaskName, Jar::class.java) {
        if (!singleTarget && target.name != COMMON) {
            val dev = (target as? MinecraftTargetInternal<*>)?.remapNamespace?.map { it.isNotEmpty() } ?: provider { false }

            val classifier = if (compilation.name == SourceSet.MAIN_SOURCE_SET_NAME) {
                target.capabilityName
            } else {
                "${target.capabilityName}-${compilation.name}"
            }

            it.archiveClassifier.set(dev.map {
                if (it) {
                    "$classifier-dev"
                } else {
                    classifier
                }
            })
        }
    }

    // TODO Use separate resource directories to have better compatibility with intellij
    tasks.named(sourceSet.processResourcesTaskName, ProcessResources::class.java) {
        it.from(configurations.named(compilation.sourceSet.mixinsConfigurationName))

        // access wideners need to be merged before being added
        // it.from(configurations.named(compilation.sourceSet.accessWidenersConfigurationName))
    }
}
