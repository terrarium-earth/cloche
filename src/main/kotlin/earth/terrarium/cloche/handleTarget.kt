package earth.terrarium.cloche

import earth.terrarium.cloche.target.*
import net.msrandom.minecraftcodev.core.MinecraftDependenciesOperatingSystemMetadataRule
import net.msrandom.minecraftcodev.core.VERSION_MANIFEST_URL
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.getCacheDirectory
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.forge.mappings.mcpConfigDependency
import net.msrandom.minecraftcodev.forge.mappings.mcpConfigExtraRemappingFiles
import net.msrandom.minecraftcodev.forge.patchesConfigurationName
import net.msrandom.minecraftcodev.includes.ExtractIncludes
import net.msrandom.minecraftcodev.remapper.RemapAction
import net.msrandom.minecraftcodev.remapper.mappingsConfigurationName
import net.msrandom.minecraftcodev.runs.RunsContainer
import org.gradle.api.Project
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.internal.tasks.JvmConstants
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.nativeplatform.OperatingSystemFamily
import org.gradle.nativeplatform.TargetMachine
import org.gradle.nativeplatform.platform.OperatingSystem
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

private fun setupModTransformationPipeline(
    project: Project,
    target: MinecraftTargetInternal,
    remapNamespace: String?,
    main: SourceSet,
    patched: Boolean,
    minecraftConfiguration: MinecraftConfiguration,
) {
    project.dependencies.registerTransform(ExtractIncludes::class.java) {
        it.from.attribute(ModTransformationStateAttribute.ATTRIBUTE, ModTransformationStateAttribute.INITIAL)
        it.to.attribute(ModTransformationStateAttribute.ATTRIBUTE, ModTransformationStateAttribute.of(target, States.INCLUDES_EXTRACTED))
    }

    if (remapNamespace == null) {
        return
    }

    project.dependencies.registerTransform(RemapAction::class.java) {
        it.from.attribute(ModTransformationStateAttribute.ATTRIBUTE, ModTransformationStateAttribute.of(target, States.INCLUDES_EXTRACTED))
        it.to.attribute(ModTransformationStateAttribute.ATTRIBUTE, ModTransformationStateAttribute.of(target, States.REMAPPED))

        it.parameters { parameters ->
            parameters.mappings.from(project.configurations.named(main.mappingsConfigurationName))

            parameters.sourceNamespace.set(remapNamespace)

            // TODO This won't need to exist if we inject MC as a dependency
            parameters.extraClasspath.from(project.files(minecraftConfiguration.artifact))

            if (patched) {
                parameters.extraFiles.set(
                    mcpConfigDependency(project, project.configurations.getByName(main.patchesConfigurationName))
                        .flatMap { file ->
                            mcpConfigExtraRemappingFiles(project, file)
                        },
                )
            }
        }
    }
}

context(Project)
internal fun handleTarget(target: MinecraftTargetInternal, singleTarget: Boolean) {
    fun add(compilation: RunnableCompilationInternal) {
        val sourceSet = compilation.sourceSet

        if (!singleTarget || compilation.name != SourceSet.MAIN_SOURCE_SET_NAME) {
            project.extension<JavaPluginExtension>().registerFeature(sourceSet.name) { spec ->
                spec.usingSourceSet(sourceSet)
                spec.capability(compilation.capabilityGroup, compilation.capabilityName, project.version.toString())

                for (featureAction in compilation.javaFeatureActions) {
                    featureAction.execute(spec)
                }
            }
        }

        configureSourceSet(sourceSet, target, compilation, singleTarget)

        if (compilation.name == SourceSet.MAIN_SOURCE_SET_NAME) {
            for (name in listOf(sourceSet.apiElementsConfigurationName, sourceSet.runtimeElementsConfigurationName)) {
                val configuration = project.configurations.getByName(name)

                val state = if (target.remapNamespace != null) {
                    States.REMAPPED
                } else {
                    States.INCLUDES_EXTRACTED
                }

                configuration.outgoing.variants { variants ->
                    variants.register("transformedJar") { variant ->
                        variant.artifact(tasks.named(sourceSet.jarTaskName))
                    }

                    variants.all { variant ->
                        variant.attributes.attribute(ModTransformationStateAttribute.ATTRIBUTE, ModTransformationStateAttribute.of(target, state))
                    }
                }

                project.components.named(JvmConstants.JAVA_MAIN_COMPONENT_NAME) { component ->
                    component as AdhocComponentWithVariants

                    component.withVariantsFromConfiguration(configuration) {
                        if (it.configurationVariant.name == "transformedJar") {
                            it.skip()
                        }
                    }
                }
            }
        } else {
            with(target) {
                // compilation.linkDynamically(target.main)
            }
        }

        setupModTransformationPipeline(
            project,
            target,
            target.remapNamespace,
            with(target) { target.main.sourceSet },
            target is ForgeTarget,
            target.main.minecraftConfiguration,
        )

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
                configuration.attributes.attribute(MinecraftAttributes.TARGET_MINECRAFT, compilation.minecraftConfiguration.targetMinecraftAttribute)

                configuration.attributes.attribute(
                    OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE,
                    objects.named(OperatingSystemFamily::class.java, DefaultNativePlatform.host().operatingSystem.toFamilyName()),
                )
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

        val runnableName = if (runnable.name == SourceSet.MAIN_SOURCE_SET_NAME) {
            "server"
        } else {
            runnable.name
        }

        project
            .extension<RunsContainer>()
            .create(target.name + TARGET_NAME_PATH_SEPARATOR + runnableName) { builder ->
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
