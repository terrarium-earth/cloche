package earth.terrarium.cloche

import earth.terrarium.cloche.target.ClocheTarget
import earth.terrarium.cloche.target.CommonTarget
import net.msrandom.minecraftcodev.accesswidener.MinecraftCodevAccessWidenerPlugin
import net.msrandom.minecraftcodev.core.dependency.minecraft
import net.msrandom.minecraftcodev.fabric.MinecraftCodevFabricPlugin
import net.msrandom.minecraftcodev.forge.MinecraftCodevForgePlugin
import net.msrandom.minecraftcodev.mixins.MinecraftCodevMixinsPlugin
import net.msrandom.minecraftcodev.remapper.MinecraftCodevRemapperPlugin
import net.msrandom.minecraftcodev.remapper.dependency.mappingsConfigurationName
import org.apache.commons.lang3.StringUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.configurationcache.extensions.serviceOf
import org.gradle.internal.instantiation.InstantiatorFactory
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation

class ClochePlugin : Plugin<Project> {
    private fun useKotlin(cloche: ClocheExtension, project: Project) =
        cloche.useKotlinMultiplatform.getOrElse(project.plugins.hasPlugin(KOTLIN_MULTIPLATFORM))

    private fun addTarget(cloche: ClocheExtension, project: Project, target: ClocheTarget, common: Boolean) {
        val kotlin = useKotlin(cloche, project)

        val targetCompilations = target.compilations

        target.minecraftVersion.convention(cloche.minecraftVersion)

        for (mappingAction in cloche.mappingActions) {
            target.mappings(mappingAction)
        }

        if (kotlin) {
            project.plugins.apply(KOTLIN_MULTIPLATFORM)

            val multiplatform = project.extensions.getByType(KotlinMultiplatformExtension::class.java)

            if (common) {
                for ((_, compilation) in targetCompilations) {
                    val sourceSet = multiplatform.sourceSets.maybeCreate("${target.name}${StringUtils.capitalize(compilation.name)}")

                    compilation.process(null, sourceSet, null)

                    val dependencyHandler = ClocheDependencyHandler(
                        project,
                        target.remapNamespace,
                        sourceSet.mappingsConfigurationName,
                        sourceSet.apiConfigurationName,
                        sourceSet.implementationConfigurationName,
                        sourceSet.runtimeOnlyConfigurationName,
                        sourceSet.compileOnlyConfigurationName
                    )

                    for (action in compilation.dependencySetupActions) {
                        action.execute(dependencyHandler)
                    }
                }
            } else {
                multiplatform.jvm(target.name) {
                    for ((_, compilation) in targetCompilations) {
                        val kotlinCompilation = compilations.maybeCreate(compilation.name)

                        compilation.process(null, null, kotlinCompilation)

                        val dependencyHandler = ClocheDependencyHandler(
                            project,
                            target.remapNamespace,
                            kotlinCompilation.mappingsConfigurationName,
                            kotlinCompilation.apiConfigurationName,
                            kotlinCompilation.implementationConfigurationName,
                            kotlinCompilation.runtimeOnlyConfigurationName,
                            kotlinCompilation.compileOnlyConfigurationName
                        )

                        for (action in compilation.dependencySetupActions) {
                            action.execute(dependencyHandler)
                        }
                    }
                }
            }
        } else {
            project.plugins.apply(JavaPlugin::class.java)
            project.plugins.apply(JavaLibraryPlugin::class.java)
            project.plugins.apply(ApplicationPlugin::class.java)

            val java = project.extensions.getByType(JavaPluginExtension::class.java)
            val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)

            for ((_, compilation) in targetCompilations) {
                val sourceSetName = when {
                    target.name == ClocheExtension::common.name -> compilation.name
                    compilation.name == KotlinCompilation.MAIN_COMPILATION_NAME -> target.name
                    else -> "${target.name}${StringUtils.capitalize(compilation.name)}"
                }

                val sourceSet = sourceSets.maybeCreate(sourceSetName)

                compilation.process(sourceSet, null, null)

                java.registerFeature(sourceSetName) {
                    it.withJavadocJar()
                    it.withSourcesJar()

                    it.usingSourceSet(sourceSet)
                }

                val dependencyHandler = ClocheDependencyHandler(
                    project,
                    target.remapNamespace,
                    compilation.mappingsConfigurationName,
                    sourceSet.apiConfigurationName,
                    sourceSet.implementationConfigurationName,
                    sourceSet.runtimeOnlyConfigurationName,
                    sourceSet.compileOnlyConfigurationName
                )

                for (action in compilation.dependencySetupActions) {
                    action.execute(dependencyHandler)
                }
            }
        }
    }

    private fun addDependencies(cloche: ClocheExtension, project: Project, target: ClocheTarget, baseCommonTarget: CommonTarget, common: Boolean) {
        val kotlin = useKotlin(cloche, project)
        val dependencies = if (target == baseCommonTarget) target.dependsOn else project.provider { target.dependsOn.get() + baseCommonTarget }

        if (kotlin) {
            val multiplatform = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
            for ((name, compilation) in target.compilations) {
                val sourceSet = if (common) {
                    multiplatform.targets.findByName(name)?.compilations?.findByName(compilation.name)?.defaultSourceSet
                } else {
                    multiplatform.sourceSets.findByName(compilation.sourceSetName)
                }

                if (sourceSet != null) {
                    for (commonTarget in dependencies.get()) {
                        val dependency = commonTarget.compilations[name]?.sourceSetName?.let(multiplatform.sourceSets::findByName)

                        if (dependency != null) {
                            sourceSet.dependsOn(dependency)
                        }
                    }
                }
            }
        } else {
            val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)

            for ((name, compilation) in target.compilations) {
                val sourceSet = sourceSets.findByName(compilation.sourceSetName)

                if (sourceSet != null) {
                    for (commonTarget in dependencies.get()) {
                        val dependency = commonTarget.compilations[name]?.sourceSetName?.let(sourceSets::findByName)

                        if (dependency != null) {
                            fun extend(base: String, dependency: String) = project.configurations.getByName(base).extendsFrom(project.configurations.getByName(dependency))

                            extend(sourceSet.apiConfigurationName, dependency.apiConfigurationName)
                            extend(sourceSet.implementationConfigurationName, dependency.implementationConfigurationName)
                            extend(sourceSet.runtimeOnlyConfigurationName, dependency.runtimeOnlyConfigurationName)
                            extend(sourceSet.compileOnlyConfigurationName, dependency.compileOnlyConfigurationName)
                        }
                    }
                }
            }
        }
    }

    override fun apply(target: Project) {
        val cloche = target.extensions.create("cloche", ClocheExtension::class.java)

        val common = cloche.commonTargets.maybeCreate(ClocheExtension::common.name)

        target.plugins.apply(MinecraftCodevFabricPlugin::class.java)
        target.plugins.apply(MinecraftCodevForgePlugin::class.java)
        target.plugins.apply(MinecraftCodevRemapperPlugin::class.java)
        target.plugins.apply(MinecraftCodevAccessWidenerPlugin::class.java)
        target.plugins.apply(MinecraftCodevMixinsPlugin::class.java)

        target.repositories.minecraft()

        target.repositories.maven { it.url = target.uri("https://maven.minecraftforge.net/") }
        target.repositories.maven { it.url = target.uri("https://maven.fabricmc.net/") }
        target.repositories.maven { it.url = target.uri("https://libraries.minecraft.net/") }

        target.afterEvaluate { project ->
            cloche.commonTargets.all { addTarget(cloche, target, it, true) }
            cloche.targets.all { addTarget(cloche, target, it, false) }

            cloche.commonTargets.all { addDependencies(cloche, project, it, common, true) }
            cloche.targets.all { addDependencies(cloche, project, it, common, false) }
        }
    }

    private companion object {
        private const val KOTLIN_MULTIPLATFORM = "org.jetbrains.kotlin.multiplatform"
    }
}
