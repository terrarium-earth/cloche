package earth.terrarium.cloche

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.modules
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import earth.terrarium.cloche.gradle.TARGETS_MODEL_KEY
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.util.GradleConstants

class DependantTargetGotoDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor,
    ): Array<PsiElement> {
        if (sourceElement != null) {
            val module = ModuleUtilCore.findModuleForPsiElement(sourceElement) ?: return emptyArray()
            val projectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module) ?: return emptyArray()
            val moduleData = ExternalSystemApiUtil.findModuleNode(sourceElement.project, GradleConstants.SYSTEM_ID, projectPath) ?: return emptyArray()

            val targetsModel = ExternalSystemApiUtil.find(moduleData, TARGETS_MODEL_KEY) ?: return emptyArray()
            val sourceSetName = ExternalSystemApiUtil.findChild(moduleData, GradleSourceSetData.KEY) { it.data.internalName == module.name }?.data?.moduleName
            val commonTarget = targetsModel.data.commons.firstOrNull { it.sourceSetName == sourceSetName }

            if (commonTarget != null) {
                // This is a common, return the declaration in the dependants
                val sourceSets = ExternalSystemApiUtil.findAll(moduleData, GradleSourceSetData.KEY)

                val modules = sourceSets.filter { sourceSet ->
                    commonTarget.dependants.any {
                        it.sourceSetName == sourceSet.data.moduleName
                    }
                }.map { sourceSet ->
                    module.project.modules.first {
                        it.name == sourceSet.data.internalName
                    }
                }

                val target = GotoDeclarationAction.findAllTargetElements(module.project, editor, offset).getOrNull(0) ?: return emptyArray()

                if (target is PsiClass) {
                    target.qualifiedName ?: return emptyArray()
                    val psiFacade = JavaPsiFacade.getInstance(module.project)

                    val firstModule = modules.first()

                    val scope = modules.drop(1).fold(firstModule.moduleWithLibrariesScope) { scope, module ->
                        scope.uniteWith(module.moduleWithLibrariesScope)
                    }

                    val classes = psiFacade.findClasses(target.qualifiedName!!, scope)
                    println()
                }
            }
            println()
            // val sourceSetData = ExternalSystemApiUtil.findChild(moduleData, GradleSourceSetData.KEY) { it.data.internalName == name } ?: return emptyArray()
        }
        return emptyArray()
    }
}
