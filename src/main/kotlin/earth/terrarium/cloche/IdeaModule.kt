package earth.terrarium.cloche

import net.msrandom.minecraftcodev.core.utils.extension
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.plugins.ide.idea.model.IdeaModule
import org.jetbrains.gradle.ext.ModuleRef

internal fun Project.withIdeaModule(sourceSet: SourceSet, action: (IdeaModule) -> Unit) = withIdeaModel {
    val modules = it.project.modules

    val moduleName = ModuleRef(project, sourceSet).toModuleName()

    // TODO In most cases, there won't be a module with the name, even if intellij will import it with the exact same name
    //  Not sure how to work around that
    modules.firstOrNull { it.name == moduleName }?.let(action::invoke)
}

internal fun Project.withIdeaModel(action: (IdeaModel) -> Unit) {
    if (project == rootProject) {
        // afterEvaluate needed because idea APIs are not lazy
        afterEvaluate {
            action(extension<IdeaModel>())
        }
    } else {
        action(rootProject.extension<IdeaModel>())
    }
}
