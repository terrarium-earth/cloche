package earth.terrarium.cloche

import earth.terrarium.cloche.target.*
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.runs.RunsContainer
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet

context(Project)
internal fun handleTarget(target: MinecraftTargetInternal) {
    val cloche = project.extension<ClocheExtension>()

    fun add(compilation: RunnableCompilationInternal) {
        val sourceSet = with(target) {
            compilation.sourceSet
        }

        if (!cloche.isSingleTargetMode || compilation.name != SourceSet.MAIN_SOURCE_SET_NAME) {
            project.extension<JavaPluginExtension>().registerFeature(sourceSet.name) { spec ->
                spec.usingSourceSet(sourceSet)
                spec.capability(compilation.capabilityGroup, compilation.capabilityName, project.version.toString())

                for (featureAction in compilation.javaFeatureActions) {
                    featureAction.execute(spec)
                }
            }
        }

        configureSourceSet(sourceSet, target, compilation)

        if (compilation.name != SourceSet.MAIN_SOURCE_SET_NAME) {
            with(target) {
                // sourceSet.linkDynamically(target.main)
            }
        }

        val resolvableConfigurationNames = listOf(
            sourceSet.compileClasspathConfigurationName,
            sourceSet.runtimeClasspathConfigurationName,
        )

        val consumableConfigurationNames = listOf(
            sourceSet.apiElementsConfigurationName,
            sourceSet.runtimeElementsConfigurationName,
            sourceSet.javadocElementsConfigurationName,
            sourceSet.sourcesElementsConfigurationName,
        )

        val configurationNames = resolvableConfigurationNames + consumableConfigurationNames

        for (name in resolvableConfigurationNames) {
            project.configurations.named(name) { configuration ->
                configuration.attributes.attribute(TARGET_MINECRAFT_ATTRIBUTE, compilation.minecraftConfiguration.targetMinecraftAttribute)
            }
        }

        for (name in configurationNames) {
            project.configurations.findByName(name)?.attributes(compilation::attributes)
        }

        val dependencyHandler = ClocheDependencyHandler(project, sourceSet)

        for (action in compilation.dependencySetupActions) {
            action.execute(dependencyHandler)
        }
    }

    fun addRunnable(runnable: Runnable) {
        runnable as RunnableInternal

        project
            .extension<RunsContainer>()
            .create(lowerCamelCaseGradleName(target.name, runnable.name.takeUnless { it == SourceSet.MAIN_SOURCE_SET_NAME })) { builder ->
                for (runSetupAction in runnable.runSetupActions) {
                    runSetupAction.execute(builder)
                }
            }
    }

    target.compilations.forEach(::add)

    addRunnable(target.main)
    addRunnable(target.data)
    addRunnable(target.client)
}
