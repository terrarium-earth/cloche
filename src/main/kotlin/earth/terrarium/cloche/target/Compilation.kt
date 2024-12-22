package earth.terrarium.cloche.target

import earth.terrarium.cloche.COMMON
import earth.terrarium.cloche.ClocheDependencyHandler
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.runs.MinecraftRunConfigurationBuilder
import org.gradle.api.Action
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.FeatureSpec
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.tasks.Jar

interface Compilation : Named {
    val accessWideners: ConfigurableFileCollection
        @InputFiles get

    val mixins: ConfigurableFileCollection
        @InputFiles get

    fun dependencies(action: Action<ClocheDependencyHandler>)

    fun java(action: Action<FeatureSpec>)

    fun attributes(action: Action<AttributeContainer>)
}

@JvmDefaultWithoutCompatibility
internal interface CompilationInternal : Compilation {
    val dependencySetupActions: DomainObjectCollection<Action<ClocheDependencyHandler>>
    val javaFeatureActions: DomainObjectCollection<Action<FeatureSpec>>
    val attributeActions: DomainObjectCollection<Action<AttributeContainer>>

    val capabilityGroup: String
    val capabilityName: String

    val capability
        get() = "$capabilityGroup:$capabilityName"

    override fun dependencies(action: Action<ClocheDependencyHandler>) {
        dependencySetupActions.add(action)
    }

    override fun java(action: Action<FeatureSpec>) {
        javaFeatureActions.add(action)
    }

    override fun attributes(action: Action<AttributeContainer>) {
        attributeActions.add(action)
    }

    fun attributes(attributes: AttributeContainer) {
        for (action in attributeActions) {
            action.execute(attributes)
        }
    }
}

interface Runnable : Named {
    fun runConfiguration(action: Action<MinecraftRunConfigurationBuilder>)
}

internal interface RunnableInternal : Runnable {
    val runSetupActions: List<Action<MinecraftRunConfigurationBuilder>>
}

interface RunnableCompilation : Runnable, Compilation

internal interface RunnableCompilationInternal : CompilationInternal, RunnableCompilation, RunnableInternal {
    val targetMinecraftAttribute: Provider<String>

    val intermediaryMinecraftFile: Provider<FileSystemLocation>
    val finalMinecraftFile: Provider<FileSystemLocation>

    val target: MinecraftTargetInternal

    val sourceSet: SourceSet
}

internal fun sourceSetName(compilation: Compilation, target: ClocheTarget) = when {
    target.name == COMMON -> compilation.name
    compilation.name == SourceSet.MAIN_SOURCE_SET_NAME -> target.featureName
    else -> lowerCamelCaseGradleName(target.featureName, compilation.name)
}

context(Project, CommonTarget) internal val CompilationInternal.sourceSet: SourceSet
    get() {
        return project.extension<SourceSetContainer>().maybeCreate(sourceSetName(this, this@CommonTarget))
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

    project.tasks.named(sourceSet.jarTaskName, Jar::class.java) {
        if (!singleTarget && target.name != COMMON) {
            val classifier = if (compilation.name == SourceSet.MAIN_SOURCE_SET_NAME) {
                target.classifierName
            } else {
                "${target.classifierName}-${compilation.name}"
            }

            it.archiveClassifier.set(classifier)
        }
    }
}
