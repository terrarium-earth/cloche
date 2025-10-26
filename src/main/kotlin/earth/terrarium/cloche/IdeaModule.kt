package earth.terrarium.cloche

import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.plugins.ide.idea.GenerateIdeaModule
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.plugins.ide.idea.model.IdeaModule
import org.gradle.plugins.ide.idea.model.IdeaModuleIml
import org.jetbrains.gradle.ext.ModuleRef

private fun Project.runModuleAction(sourceSet: SourceSet, action: (IdeaModule) -> Unit) {
    val modules = rootProject.extension<IdeaModel>().project.modules

    val moduleName = ModuleRef(project, sourceSet).toModuleName()

    // TODO In most cases, there won't be a module with the name, even if intellij will import it with the exact same name
    //  Not sure how to work around that
    modules.firstOrNull { it.name == moduleName }?.let(action::invoke)
}

internal fun Project.ideaModule(sourceSet: SourceSet, action: (IdeaModule) -> Unit) {
    if (project == rootProject) {
        // afterEvaluate needed because idea APIs are not lazy
        afterEvaluate {
            runModuleAction(sourceSet, action)
        }
    } else {
        runModuleAction(sourceSet, action)
    }
}
