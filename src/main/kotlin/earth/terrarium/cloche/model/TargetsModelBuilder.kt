package earth.terrarium.cloche.model

import com.google.auto.service.AutoService
import earth.terrarium.cloche.ClocheExtension
import earth.terrarium.cloche.api.target.targetName
import net.msrandom.minecraftcodev.core.utils.extension
import org.gradle.api.Project
import org.gradle.tooling.provider.model.ToolingModelBuilder

private fun convertTarget(
    commons: MutableMap<String, CommonTarget>,
    targets: MutableMap<String, MinecraftTarget>,
    target: earth.terrarium.cloche.api.target.MinecraftTarget,
): MinecraftTarget = targets.computeIfAbsent(target.name) {
    MinecraftTarget(
        target.targetName,
        target.sourceSet.name,
        target.dependsOn.map { convertCommon(commons, targets, it) },
        target.loaderName,
        target.minecraftVersion.get(),
    )
}

private fun convertCommon(
    commons: MutableMap<String, CommonTarget>,
    targets: MutableMap<String, MinecraftTarget>,
    common: earth.terrarium.cloche.api.target.CommonTarget,
): CommonTarget =
    commons.computeIfAbsent(common.name) {
        CommonTarget(
            it,
            common.sourceSet.name,
            common.dependsOn.map { convertCommon(commons, targets, it) },
            common.dependents.get().map {
                convertTarget(commons, targets, it)
            },
            common.dependents.get().map { it.loaderName },
            common.minecraftVersions.get().toList(),
        )
    }

@AutoService(ToolingModelBuilder::class)
class TargetsModelBuilder : ToolingModelBuilder {
    override fun canBuild(modelName: String) =
        modelName == TargetsModel::class.qualifiedName

    override fun buildAll(modelName: String, project: Project): TargetsModel {
        val commons = hashMapOf<String, CommonTarget>()
        val targets = hashMapOf<String, MinecraftTarget>()

        val cloche = project.extension<ClocheExtension>()

        return TargetsModelImpl(
            cloche.commonTargets.map {
                convertCommon(commons, targets, it)
            },
            cloche.targets.map {
                convertTarget(commons, targets, it)
            },
        )
    }
}
