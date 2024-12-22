package earth.terrarium.cloche

import earth.terrarium.cloche.target.*
import earth.terrarium.cloche.target.MinecraftTargetInternal
import net.msrandom.minecraftcodev.accesswidener.MinecraftCodevAccessWidenerPlugin
import net.msrandom.minecraftcodev.accesswidener.accessWidenersConfigurationName
import net.msrandom.minecraftcodev.core.MinecraftDependenciesOperatingSystemMetadataRule
import net.msrandom.minecraftcodev.core.VERSION_MANIFEST_URL
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.getGlobalCacheDirectory
import net.msrandom.minecraftcodev.decompiler.MinecraftCodevDecompilerPlugin
import net.msrandom.minecraftcodev.fabric.MinecraftCodevFabricPlugin
import net.msrandom.minecraftcodev.forge.MinecraftCodevForgePlugin
import net.msrandom.minecraftcodev.includes.MinecraftCodevIncludesPlugin
import net.msrandom.minecraftcodev.intersection.MinecraftCodevIntersectionPlugin
import net.msrandom.minecraftcodev.mixins.MinecraftCodevMixinsPlugin
import net.msrandom.minecraftcodev.mixins.mixinsConfigurationName
import net.msrandom.minecraftcodev.remapper.MinecraftCodevRemapperPlugin
import net.msrandom.minecraftcodev.runs.MinecraftCodevRunsPlugin
import net.msrandom.virtualsourcesets.JavaVirtualSourceSetsPlugin
import net.msrandom.virtualsourcesets.VirtualExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSetContainer

fun Project.addSetupTask(name: String): String {
    if (!System.getProperty("idea.sync.active", "false").toBoolean()) {
        return name
    }

    val fullName =
        if (project == project.rootProject) {
            name
        } else {
            "${project.path}:$name"
        }

    val taskNames = project.gradle.startParameter.taskNames

    if (fullName !in taskNames) {
        project.gradle.startParameter.setTaskNames(taskNames + fullName)
    }

    return name
}

fun Project.extend(
    base: String,
    dependency: String,
) = project.configurations.findByName(dependency)?.let {
    project.configurations.findByName(base)?.extendsFrom(it)
}

internal fun addTarget(
    cloche: ClocheExtension,
    project: Project,
    target: MinecraftTarget,
    singleTarget: Boolean,
) {
    target as MinecraftTargetInternal

    target.minecraftVersion.convention(cloche.minecraftVersion)

    for (mappingAction in cloche.mappingActions) {
        target.mappings(mappingAction)
    }

    with(project) {
        handleTarget(target, singleTarget)
    }
}

class ClochePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val cloche = target.extensions.create("cloche", ClocheExtension::class.java)

        target.plugins.apply(MinecraftCodevFabricPlugin::class.java)
        target.plugins.apply(MinecraftCodevForgePlugin::class.java)
        target.plugins.apply(MinecraftCodevRemapperPlugin::class.java)
        target.plugins.apply(MinecraftCodevIncludesPlugin::class.java)
        target.plugins.apply(MinecraftCodevIntersectionPlugin::class.java)
        target.plugins.apply(MinecraftCodevDecompilerPlugin::class.java)
        target.plugins.apply(MinecraftCodevAccessWidenerPlugin::class.java)
        target.plugins.apply(MinecraftCodevMixinsPlugin::class.java)
        target.plugins.apply(MinecraftCodevRunsPlugin::class.java)

        target.plugins.apply(JavaVirtualSourceSetsPlugin::class.java)

        target.plugins.apply(JavaLibraryPlugin::class.java)
        target.plugins.apply(ApplicationPlugin::class.java)

        target.dependencies.attributesSchema { schema ->
            schema.attribute(VARIANT_ATTRIBUTE) {
                it.compatibilityRules.add(VariantCompatibilityRule::class.java)
            }
        }

        target.dependencies.artifactTypes {
            it.named(ArtifactTypeDefinition.JAR_TYPE) { jar ->
                jar.attributes.attribute(
                    ModTransformationStateAttribute.ATTRIBUTE,
                    ModTransformationStateAttribute.INITIAL
                )
            }
        }

        target.extension<SourceSetContainer>().all { sourceSet ->
            sourceSet.extension<VirtualExtension>().dependsOn.all { dependency ->
                target.extend(sourceSet.accessWidenersConfigurationName, dependency.accessWidenersConfigurationName)
                target.extend(sourceSet.mixinsConfigurationName, dependency.mixinsConfigurationName)
            }
        }

        applyTargets(target, cloche)
    }

    private fun applyTargets(project: Project, cloche: ClocheExtension) {
        fun getDependencies(target: ClocheTarget): List<CommonTargetInternal> {
            return (target.dependsOn.get() + target.dependsOn.get().flatMap(::getDependencies)).map { it as CommonTargetInternal }
        }

        val targetsProvider = project.provider {
            cloche.targets.map { it as MinecraftTargetInternal }
        }

        @Suppress("UNCHECKED_CAST")
        val targetDependencies = targetsProvider.flatMap { targets ->
            val association = project.objects.mapProperty(MinecraftTargetInternal::class.java, List::class.java) as MapProperty<MinecraftTargetInternal, List<CommonTargetInternal>>

            for (target in targets) {
                association.put(target, getDependencies(target))
            }

            association
        }

        @Suppress("UNCHECKED_CAST")
        val commonToTarget = targetDependencies.map {
            val association = hashMapOf<CommonTargetInternal, MutableSet<MinecraftTargetInternal>>()

            for ((edgeTarget, dependencies) in it) {
                for (dependency in dependencies) {
                    association.computeIfAbsent(dependency) { hashSetOf() }.add(edgeTarget as MinecraftTargetInternal)
                }
            }

            association as Map<CommonTargetInternal, Set<MinecraftTargetInternal>>
        }

        @Suppress("UNCHECKED_CAST")
        val commons = project.objects.mapProperty(CommonTargetInternal::class.java, CommonInfo::class.java)

        commons.putAll(commonToTarget.map {
            it.mapValues { (_, edges) ->
                var commonType: String? = null
                var minecraftVersion: String? = null

                for (target in edges) {
                    if (minecraftVersion == null) {
                        minecraftVersion = target.minecraftVersion.get()
                    } else if (minecraftVersion != target.minecraftVersion.get()) {
                        minecraftVersion = null
                    }

                    if (commonType == null) {
                        commonType = target.commonType
                    } else if (target.commonType != commonType) {
                        commonType = null
                        break
                    }
                }

                CommonInfo(
                    edges,
                    commonType,
                    minecraftVersion,
                )
            }
        })

        cloche.targets.all {
            it as MinecraftTargetInternal

            it.initialize(false)

            addTarget(cloche, project, it, false)

            for (dependency in it.dependsOn.get()) {
                dependency as CommonTargetInternal

                with(dependency) {
                    with(project) {
                        it.main.sourceSet.linkStatically(dependency.main.sourceSet)
                        it.data.sourceSet.linkStatically(dependency.data.sourceSet)

                        if (it.client is RunnableCompilationInternal) {
                            (it.client as RunnableCompilationInternal).sourceSet.linkStatically(dependency.client.sourceSet)
                        }
                    }
                }
            }
        }

        cloche.commonTargets.all { commonTarget ->
            commonTarget as CommonTargetInternal

            for (dependency in commonTarget.dependsOn.get()) {
                dependency as CommonTargetInternal

                with(project) {
                    fun add(compilation: CommonTargetInternal.() -> CompilationInternal) {
                        val sourceSet = with(commonTarget) {
                            commonTarget.compilation().sourceSet
                        }

                        with(dependency) {
                            sourceSet.linkStatically(dependency.compilation().sourceSet)
                        }
                    }

                    add(CommonTargetInternal::main)
                    add(CommonTargetInternal::client)
                    add(CommonTargetInternal::data)
                }
            }

            with(project) {
                val commonInfo = commons.getting(commonTarget)

                val onlyCommonOfType = commons.zip(commonInfo, ::Pair).map { (commons, info) ->
                    commons.count { it.value.type == info.type } == 1
                }

                createCommonTarget(commonTarget, commonInfo, onlyCommonOfType)
            }
        }

        project.afterEvaluate {
            val targets = if (cloche.singleTargetConfigurator.target == null) {
                cloche.targets
            } else {
                listOf(cloche.singleTargetConfigurator.target!!)
            }

            project.dependencies.components.all(MinecraftDependenciesOperatingSystemMetadataRule::class.java) {
                it.params(
                    getGlobalCacheDirectory(project),
                    targets.map { it.minecraftVersion.get() },
                    VERSION_MANIFEST_URL,
                    project.gradle.startParameter.isOffline,
                )
            }

            val userdevs = targets.filterIsInstance<ForgeTargetImpl>().map(ForgeTargetImpl::getUserdev)

            if (userdevs.isNotEmpty()) {
/*                project.dependencies.components.all(MinecraftForgeComponentClassifierMetadataRule::class.java) {
                    it.params(userdevs)
                }*/
            }
        }
    }

    companion object {
        const val CLIENT_COMPILATION_NAME = "client"
        const val DATA_COMPILATION_NAME = "data"

        const val STUB_GROUP = "net.msrandom"
        const val STUB_NAME = "stub"
        const val STUB_VERSION = "0.0.0"
        const val STUB_MODULE = "$STUB_GROUP:$STUB_NAME"
        const val STUB_DEPENDENCY = "$STUB_MODULE:$STUB_VERSION"
    }
}
