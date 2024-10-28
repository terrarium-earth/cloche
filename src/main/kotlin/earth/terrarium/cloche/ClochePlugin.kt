package earth.terrarium.cloche

import earth.terrarium.cloche.target.*
import earth.terrarium.cloche.target.MinecraftTargetInternal
import net.msrandom.minecraftcodev.accesswidener.MinecraftCodevAccessWidenerPlugin
import net.msrandom.minecraftcodev.accesswidener.accessWidenersConfigurationName
import net.msrandom.minecraftcodev.core.utils.extension
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
import org.gradle.api.plugins.JavaPlugin
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

class ClochePlugin : Plugin<Project> {
    private fun addTarget(
        cloche: ClocheExtension,
        project: Project,
        target: MinecraftTarget,
    ) {
        target as MinecraftTargetInternal

        target.minecraftVersion.convention(cloche.minecraftVersion)

        for (mappingAction in cloche.mappingActions) {
            target.mappings(mappingAction)
        }

        with(project) {
            handleTarget(target)
        }
    }

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

        target.extension<SourceSetContainer>().all { sourceSet ->
            sourceSet.extension<VirtualExtension>().dependsOn.all { dependency ->
                target.extend(sourceSet.accessWidenersConfigurationName, dependency.accessWidenersConfigurationName)
                target.extend(sourceSet.mixinsConfigurationName, dependency.mixinsConfigurationName)
            }
        }

        target.afterEvaluate { project ->
            applyTargets(project, cloche)
        }
    }

    private fun applyTargets(project: Project, cloche: ClocheExtension) {
        val isSingleTargetMode = cloche.targets.size == 1 && cloche.commonTargets.isEmpty()

        cloche.isSingleTargetMode = isSingleTargetMode

        if (isSingleTargetMode) {
            addTarget(cloche, project, cloche.targets.first())
            return
        }

        val primaryCommon = cloche.common()

        cloche.targets.all {
            addTarget(cloche, project, it)

            it.dependsOn(primaryCommon)
        }

        cloche.commonTargets.all {
            if (it != primaryCommon) {
                it.dependsOn(primaryCommon)
            }
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

            for (target in edges) {
                if (commonType == null) {
                    commonType = target.commonType
                } else if (target.commonType != commonType) {
                    commonType = GENERAL_COMMON_TYPE
                    break
                }
            }

            if (commonType == null) {
                commonType = GENERAL_COMMON_TYPE
            }

            CommonInfo(
                common,
                edges,
                getDependencies(common),
                commonType,
            )
        }

        with(project) {
            for (common in commons) {
                createCommonTarget(common, commons.count { it.type == common.type } == 1)
            }
        }
    }

    companion object {
        const val CLIENT_COMPILATION_NAME = "client"
        const val DATA_COMPILATION_NAME = "data"

        const val STUB_MODULE = "net.msrandom:stub"
        const val STUB_DEPENDENCY = "$STUB_MODULE:0.0.0"
    }
}
