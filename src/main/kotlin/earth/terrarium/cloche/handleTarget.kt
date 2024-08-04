package earth.terrarium.cloche

import earth.terrarium.cloche.target.*
import net.msrandom.minecraftcodev.accesswidener.accessWidenersConfigurationName
import net.msrandom.minecraftcodev.core.MinecraftCodevExtension
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseName
import net.msrandom.minecraftcodev.forge.patchesConfigurationName
import net.msrandom.minecraftcodev.mixins.mixinsConfigurationName
import net.msrandom.minecraftcodev.remapper.mappingsConfigurationName
import net.msrandom.minecraftcodev.runs.RunsContainer
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.internal.DefaultJavaFeatureSpec
import org.gradle.api.tasks.SourceSet
import org.gradle.internal.component.external.model.ProjectDerivedCapability

fun modConfigurationName(name: String) = lowerCamelCaseName("mod", name)

context(Project) fun handleTarget(target: MinecraftTarget) {
    val cloche = extension<ClocheExtension>()

    fun add(compilation: RunnableCompilationInternal?, variant: PublicationVariant) {
        if (compilation == null) {
            // TODO Setup run configurations regardless

            return
        }

        val sourceSet = with(target) {
            compilation.sourceSet
        }

        if (compilation.name != SourceSet.MAIN_SOURCE_SET_NAME) {
            val main = with(target) {
                target.main.sourceSet
            }

            sourceSet.compileClasspath += main.compileClasspath + main.output
            sourceSet.runtimeClasspath += main.runtimeClasspath + main.output

            project.extend(sourceSet.mixinsConfigurationName, main.mixinsConfigurationName)
            project.extend(sourceSet.patchesConfigurationName, main.patchesConfigurationName)
            project.extend(sourceSet.mappingsConfigurationName, main.mappingsConfigurationName)
            project.extend(sourceSet.accessWidenersConfigurationName, main.accessWidenersConfigurationName)
        }

        val spec = DefaultJavaFeatureSpec(sourceSet.name, project as ProjectInternal)

        val capability = ProjectDerivedCapability(project)

        spec.withJavadocJar()
        spec.withSourcesJar()

        spec.usingSourceSet(sourceSet)

        spec.capability(capability.group, capability.name, capability.version!!)
        spec.create()

        val modApi = project.configurations.getByName(modConfigurationName(sourceSet.apiConfigurationName))
        val modCompileOnlyApi = project.configurations.getByName(modConfigurationName(sourceSet.compileOnlyApiConfigurationName))
        val modRuntimeOnly = project.configurations.getByName(modConfigurationName(sourceSet.runtimeOnlyConfigurationName))
        val modImplementation = project.configurations.getByName(modConfigurationName(sourceSet.implementationConfigurationName))
        val modCompileOnly = project.configurations.getByName(modConfigurationName(sourceSet.compileOnlyConfigurationName))

        project.configurations.named(sourceSet.apiElementsConfigurationName) {
            it.extendsFrom(modApi)
            it.extendsFrom(modCompileOnlyApi)

            it.artifacts.clear()
            project.artifacts.add(it.name, project.tasks.named(sourceSet.jarTaskName))
        }

        project.configurations.named(sourceSet.runtimeElementsConfigurationName) {
            it.extendsFrom(modRuntimeOnly)
            it.extendsFrom(modImplementation)

            it.artifacts.clear()
            project.artifacts.add(it.name, project.tasks.named(sourceSet.jarTaskName))
        }

        val configurationNames = listOf(
            sourceSet.compileClasspathConfigurationName,
            sourceSet.runtimeClasspathConfigurationName,
            sourceSet.apiElementsConfigurationName,
            sourceSet.runtimeElementsConfigurationName,
            sourceSet.javadocElementsConfigurationName,
            sourceSet.sourcesElementsConfigurationName,
            modConfigurationName(sourceSet.compileClasspathConfigurationName),
            modConfigurationName(sourceSet.runtimeClasspathConfigurationName),
        )

        for (name in configurationNames) {
            project.configurations.named(name) { configuration ->
                configuration.attributes.attribute(ClochePlugin.MOD_LOADER_ATTRIBUTE, target.loaderAttributeName)

                project.afterEvaluate {
                    configuration.attributes.attribute(ClochePlugin.MINECRAFT_VERSION_ATTRIBUTE, target.minecraftVersion.orElse(cloche.minecraftVersion).get())
                }

                configuration.attributes.attribute(ClochePlugin.VARIANT_ATTRIBUTE, variant)
            }
        }

        val dependencyHandler = ClocheDependencyHandler(
            project,
            sourceSet.apiConfigurationName,
            sourceSet.compileOnlyApiConfigurationName,
            sourceSet.implementationConfigurationName,
            sourceSet.runtimeOnlyConfigurationName,
            sourceSet.compileOnlyConfigurationName,

            modApi.name,
            modCompileOnlyApi.name,
            modImplementation.name,
            modRuntimeOnly.name,
            modCompileOnly.name,
        )

        for (action in compilation.dependencySetupActions) {
            action.execute(dependencyHandler)
        }

        project
            .extension<MinecraftCodevExtension>()
            .extension<RunsContainer>()
            .create(lowerCamelCaseName(target.name, compilation.name.takeUnless { it == SourceSet.MAIN_SOURCE_SET_NAME })) { builder ->
                builder.sourceSet(sourceSet)

                project.afterEvaluate {
                    for (runSetupAction in compilation.runSetupActions) {
                        runSetupAction.execute(builder)
                    }
                }
            }
    }

    add(target.main as RunnableCompilationInternal, PublicationVariant.Common)
    add(target.data as RunnableCompilationInternal, PublicationVariant.Data)
    add((target as? ClientTarget)?.client as RunnableCompilationInternal?, PublicationVariant.Client)
}
