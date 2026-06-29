package earth.terrarium.cloche.gradle

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.project.ModuleData
import earth.terrarium.cloche.model.TargetsModel
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension

val TARGETS_MODEL_KEY = Key.create(TargetsModel::class.java, 0)

class TargetsModelResolverExtension : AbstractProjectResolverExtension() {
    override fun getExtraProjectModelClasses() =
        setOf(TargetsModel::class.java)

    override fun getToolingExtensionsClasses() =
        setOf(ClocheTargetsModelBuilder::class.java)

    override fun populateModuleExtraModels(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
        super.populateModuleExtraModels(gradleModule, ideModule)

        val model = resolverCtx.getExtraProject(gradleModule, TargetsModel::class.java) ?: return

        ideModule.createChild(TARGETS_MODEL_KEY, model)
    }
}
