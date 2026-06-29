package earth.terrarium.cloche.gradle

import com.google.auto.service.AutoService
import earth.terrarium.cloche.model.TargetsModel
import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService;

@CompileStatic
@AutoService(ModelBuilderService)
class ClocheTargetsModelBuilder extends AbstractModelBuilderService {
    boolean canBuild(String modelName) {
        modelName == TargetsModel.name
    }

    TargetsModel buildAll(String modelName, Project project, ModelBuilderContext modelBuilderContext) {
        if (!project.plugins.hasPlugin("earth.terrarium.cloche")) {
            return null
        }

        return (project as ProjectInternal)
                .services
                .get(ToolingModelBuilderRegistry)
                .getBuilder(TargetsModel.name)
                .buildAll(TargetsModel.name, project) as TargetsModel
    }
}
