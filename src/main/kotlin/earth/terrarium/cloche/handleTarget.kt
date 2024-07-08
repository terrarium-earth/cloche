package earth.terrarium.cloche

import earth.terrarium.cloche.target.ClientTarget
import earth.terrarium.cloche.target.Compilation
import earth.terrarium.cloche.target.MinecraftTarget
import earth.terrarium.cloche.target.RunnableCompilationInternal
import net.msrandom.extensions.ClassExtensionsExtension
import net.msrandom.minecraftcodev.accesswidener.accessWidenersConfigurationName
import net.msrandom.minecraftcodev.core.MinecraftCodevExtension
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseName
import net.msrandom.minecraftcodev.forge.patchesConfigurationName
import net.msrandom.minecraftcodev.mixins.mixinsConfigurationName
import net.msrandom.minecraftcodev.remapper.mappingsConfigurationName
import net.msrandom.minecraftcodev.runs.RunsContainer
import net.msrandom.virtualsourcesets.VirtualExtension
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.internal.component.external.model.ProjectDerivedCapability
import org.gradle.util.internal.GUtil
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetContainer

fun handleTarget(project: Project, target: MinecraftTarget) {
    val sourceSets = project.extension<SourceSetContainer>()
    val classExtensions = project.extension<ClassExtensionsExtension>()
    val cloche = project.extension<ClocheExtension>()
    val java = project.extension<JavaPluginExtension>()

    val sourceSetName = { compilation: Compilation ->
        when {
            target.name == ClocheExtension::common.name -> compilation.name
            compilation.name == SourceSet.MAIN_SOURCE_SET_NAME -> target.name
            else -> lowerCamelCaseName(target.name, compilation.name)
        }
    }

    fun add(compilation: RunnableCompilationInternal) {
        val main = sourceSets.maybeCreate(sourceSetName(target.main))

        val sourceSet = sourceSets.maybeCreate(sourceSetName(compilation))

        val extensionPattern = target.extensionPattern.orElse(cloche.extensionPattern).getOrElse("**/classextensions/**")

        compilation.sourceSet.set(sourceSet)

        classExtensions.registerForSourceSet(sourceSet, extensionPattern)

        if (compilation.name == SourceSet.MAIN_SOURCE_SET_NAME) {
            java.registerFeature(GUtil.toCamelCase(sourceSet.name)) { spec ->
                val capability = ProjectDerivedCapability(project)

                spec.withJavadocJar()
                spec.withSourcesJar()

                spec.usingSourceSet(sourceSet)

                spec.capability(capability.group, capability.name, capability.version!!)
            }

            for (name in arrayOf(
                sourceSet.apiElementsConfigurationName,
                sourceSet.compileClasspathConfigurationName,
                sourceSet.runtimeElementsConfigurationName,
                sourceSet.runtimeClasspathConfigurationName,
                sourceSet.javadocElementsConfigurationName,
                sourceSet.sourcesElementsConfigurationName,
            )) {
                project.configurations.named(name) { configuration ->
                    configuration.attributes.attribute(ClochePlugin.MOD_LOADER_ATTRIBUTE, target.loaderAttributeName)

                    project.afterEvaluate {
                        configuration.attributes.attribute(ClochePlugin.MINECRAFT_VERSION_ATTRIBUTE, target.minecraftVersion.orElse(cloche.minecraftVersion).get())
                    }
                }
            }
        } else {
            sourceSet.extension<VirtualExtension>().dependsOn.add(main)

            if (cloche.useKotlin.get()) {
                val kotlin = project.extension<KotlinSourceSetContainer>()

                kotlin.sourceSets.getByName(sourceSet.name).dependsOn(kotlin.sourceSets.getByName(main.name))
            }

            project.extend(sourceSet.mixinsConfigurationName, main.mixinsConfigurationName)
            project.extend(sourceSet.patchesConfigurationName, main.patchesConfigurationName)
            project.extend(sourceSet.mappingsConfigurationName, main.mappingsConfigurationName)
            project.extend(sourceSet.accessWidenersConfigurationName, main.accessWidenersConfigurationName)
        }

        fun modConfigurationName(name: String) = lowerCamelCaseName("mod", name)

        val modApi = project.configurations.create(modConfigurationName(sourceSet.apiConfigurationName))
        val modImplementation = project.configurations.create(modConfigurationName(sourceSet.implementationConfigurationName))
        val modRuntimeOnly = project.configurations.create(modConfigurationName(sourceSet.runtimeOnlyConfigurationName))
        val modCompileOnly = project.configurations.create(modConfigurationName(sourceSet.compileOnlyConfigurationName))

        project.afterEvaluate {
            val dependencyHandler = ClocheDependencyHandler(
                project,
                sourceSet.apiConfigurationName,
                sourceSet.implementationConfigurationName,
                sourceSet.runtimeOnlyConfigurationName,
                sourceSet.compileOnlyConfigurationName,
                modApi.name,
                modImplementation.name,
                modRuntimeOnly.name,
                modCompileOnly.name,
            )

            for (action in compilation.dependencySetupActions) {
                action.execute(dependencyHandler)
            }
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

    add(target.main as RunnableCompilationInternal)
    add(target.test as RunnableCompilationInternal)
    (target.data as RunnableCompilationInternal?)?.let(::add)
    ((target as? ClientTarget)?.client as RunnableCompilationInternal?)?.let(::add)
}
