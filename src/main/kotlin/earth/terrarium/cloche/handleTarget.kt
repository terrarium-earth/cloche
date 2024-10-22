package earth.terrarium.cloche

import earth.terrarium.cloche.target.*
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseName
import net.msrandom.minecraftcodev.runs.RunsContainer
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.internal.DefaultJavaFeatureSpec
import org.gradle.api.tasks.SourceSet
import org.gradle.internal.component.external.model.ProjectDerivedCapability

context(Project)
fun handleTarget(target: MinecraftTarget) {
    val cloche = extension<ClocheExtension>()

    fun add(
        compilation: RunnableCompilationInternal?,
        variant: PublicationVariant,
    ) {
        if (compilation == null) {
            // TODO Setup run configurations regardless

            return
        }

        val sourceSet =
            with(target) {
                compilation.sourceSet
            }

        project.dependencies.add(sourceSet.implementationConfigurationName, project.files(compilation.dependencyMinecraftFile))

        if (compilation.name != SourceSet.MAIN_SOURCE_SET_NAME) {
            val main =
                with(target) {
                    (target.main as RunnableCompilationInternal).sourceSet
                }

            sourceSet.linkDynamically(main)
        }

        val spec = DefaultJavaFeatureSpec(target.name, project as ProjectInternal)

        val capability = ProjectDerivedCapability(project)

        spec.withJavadocJar()
        spec.withSourcesJar()

        spec.usingSourceSet(sourceSet)

        spec.capability(capability.group, capability.name, capability.version!!)
        spec.create()

        val resolvableConfigurationNames = listOf(
            sourceSet.compileClasspathConfigurationName,
            sourceSet.runtimeClasspathConfigurationName,
        )

        val configurationNames = resolvableConfigurationNames + listOf(
            sourceSet.apiElementsConfigurationName,
            sourceSet.runtimeElementsConfigurationName,
            sourceSet.javadocElementsConfigurationName,
            sourceSet.sourcesElementsConfigurationName,
        )

        val variantSuffix = if (compilation.name == ClochePlugin.CLIENT_COMPILATION_NAME) {
            ClochePlugin.CLIENT_COMPILATION_NAME
        } else {
            ""
        }

        for (name in resolvableConfigurationNames) {
            project.configurations.named(name) { configuration ->
                configuration.attributes.attribute(TARGET_MINECRAFT_ATTRIBUTE, lowerCamelCaseName(target.name, variantSuffix))
            }
        }

        for (name in configurationNames) {
            project.configurations.named(name) { configuration ->
                configuration.attributes.attribute(MOD_LOADER_ATTRIBUTE, target.loaderAttributeName)

                configuration.attributes.attribute(
                    MINECRAFT_VERSION_ATTRIBUTE,
                    target.minecraftVersion.orElse(cloche.minecraftVersion).get(),
                )

                configuration.attributes.attribute(VARIANT_ATTRIBUTE, variant)
            }
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
                project.afterEvaluate {
                    for (runSetupAction in runnable.runSetupActions) {
                        runSetupAction.execute(builder)
                    }
                }
            }
    }

    add(target.main as RunnableCompilationInternal, PublicationVariant.Common)
    add(target.data as RunnableCompilationInternal, PublicationVariant.Data)
    add((target as? ClientTarget)?.client as RunnableCompilationInternal?, PublicationVariant.Client)

    addRunnable(target.main)
    addRunnable(target.data)
    addRunnable(target.client)
}
