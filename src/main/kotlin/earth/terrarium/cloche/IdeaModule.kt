package earth.terrarium.cloche

import net.msrandom.minecraftcodev.core.utils.extension
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.plugins.ide.idea.model.IdeaModule
import org.jetbrains.gradle.ext.ModuleRef

private fun runModuleAction(project: Project, sourceSet: SourceSet, action: Action<IdeaModule>) {
    val modules = project.rootProject.extension<IdeaModel>().project.modules

    val moduleName = ModuleRef(project, sourceSet).toModuleName()

    // TODO In most cases, there won't be a module with the name, even if intellij will import it with the exact same name
    //  Not sure how to work around that
    modules.firstOrNull { it.name == moduleName }?.let(action::execute)
}

fun Project.ideaModule(sourceSet: SourceSet, action: Action<IdeaModule>) {
    if (project == rootProject) {
        // afterEvaluate needed because idea APIs are not lazy
        afterEvaluate {
            runModuleAction(it, sourceSet, action)
        }
    } else {
        runModuleAction(this, sourceSet, action)
    }
}
