package earth.terrarium.cloche

import earth.terrarium.cloche.target.*
import earth.terrarium.cloche.target.MinecraftTargetInternal
import net.msrandom.minecraftcodev.accesswidener.MinecraftCodevAccessWidenerPlugin
import net.msrandom.minecraftcodev.accesswidener.accessWidenersConfigurationName
import net.msrandom.minecraftcodev.core.MinecraftDependenciesOperatingSystemMetadataRule
import net.msrandom.minecraftcodev.core.VERSION_MANIFEST_URL
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.getCacheDirectory
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
                jar.attributes.attribute(ModTransformationStateAttribute.ATTRIBUTE, ModTransformationStateAttribute.INITIAL)
            }

            it.register(MINECRAFT_ARTIFACT_TYPE) { minecraft ->
                minecraft.attributes.attribute(MinecraftTransformationStateAttribute.ATTRIBUTE, MinecraftTransformationStateAttribute.INITIAL)
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
        cloche.targets.all {
            (it as MinecraftTargetInternal).initialize(false)
            addTarget(cloche, project, it, false)
        }

        project.afterEvaluate {
            project.dependencies.components.all(MinecraftDependenciesOperatingSystemMetadataRule::class.java) {
                val versions = if (cloche.singleTargetConfigurator.target == null) {
                    cloche.targets.map { it.minecraftVersion.get() }
                } else {
                    listOf(cloche.singleTargetConfigurator.target!!.minecraftVersion.get())
                }

                it.params(
                    getCacheDirectory(project),
                    versions,
                    VERSION_MANIFEST_URL,
                    project.gradle.startParameter.isOffline,
                )
            }

            if (cloche.singleTargetConfigurator.target != null) {
                return@afterEvaluate
            }

            fun getDependencies(target: ClocheTarget): List<CommonTarget> =
                target.dependsOn.get() + target.dependsOn.get().flatMap(::getDependencies)

            val targetDependencies = cloche.targets.toList().associateWith(::getDependencies)
            val commonToTarget = hashMapOf<CommonTargetInternal, MutableSet<MinecraftTargetInternal>>()

            for ((edgeTarget, dependencies) in targetDependencies) {
                for (dependency in dependencies) {
                    commonToTarget.computeIfAbsent(dependency as CommonTargetInternal) { hashSetOf() }.add(edgeTarget as MinecraftTargetInternal)
                }
            }

            val commons = commonToTarget.map { (common, edges) ->
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
                    common,
                    edges,
                    getDependencies(common),
                    commonType,
                    minecraftVersion,
                )
            }

            with(it) {
                for (common in commons) {
                    createCommonTarget(common, commons.count { it.type == common.type } == 1)
                }
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
