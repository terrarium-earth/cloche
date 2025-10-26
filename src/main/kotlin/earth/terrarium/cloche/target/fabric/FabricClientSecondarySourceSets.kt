package earth.terrarium.cloche.target.fabric

import earth.terrarium.cloche.ClochePlugin
import earth.terrarium.cloche.PublicationSide
import earth.terrarium.cloche.api.attributes.IncludeTransformationStateAttribute
import earth.terrarium.cloche.api.target.TARGET_NAME_PATH_SEPARATOR
import earth.terrarium.cloche.api.target.compilation.FabricSecondarySourceSets
import earth.terrarium.cloche.ideaModule
import earth.terrarium.cloche.target.LazyConfigurableInternal
import earth.terrarium.cloche.target.TargetCompilation
import earth.terrarium.cloche.target.TargetCompilationInfo
import earth.terrarium.cloche.target.lazyConfigurable
import net.msrandom.minecraftcodev.fabric.task.JarInJar
import org.gradle.api.tasks.SourceSet
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.newInstance
import org.gradle.language.jvm.tasks.ProcessResources
import javax.inject.Inject

internal abstract class FabricClientSecondarySourceSets @Inject constructor(info: TargetCompilationInfo<FabricTargetImpl>) : TargetCompilation<FabricTargetImpl>(info), FabricSecondarySourceSets {
    override val data: LazyConfigurableInternal<FabricCompilationImpl> = project.lazyConfigurable {
        project.objects.newInstance<FabricCompilationImpl>(
            TargetCompilationInfo(
                name + TARGET_NAME_PATH_SEPARATOR + ClochePlugin.DATA_COMPILATION_NAME,
                target,
                info.intermediaryMinecraftClasspath,
                info.namedMinecraftFile,
                info.extraClasspathFiles,
                info.side,
                data = true,
                test = false,
                includeState = IncludeTransformationStateAttribute.Stripped,
                includeJarType = JarInJar::class.java,
            ),
        )
    }

    override val test: LazyConfigurableInternal<FabricCompilationImpl> = project.lazyConfigurable {
        project.objects.newInstance<FabricCompilationImpl>(
            TargetCompilationInfo(
                name + TARGET_NAME_PATH_SEPARATOR + SourceSet.TEST_SOURCE_SET_NAME,
                target,
                info.intermediaryMinecraftClasspath,
                info.namedMinecraftFile,
                info.extraClasspathFiles,
                info.side,
                data = false,
                test = true,
                includeState = IncludeTransformationStateAttribute.Stripped,
                includeJarType = JarInJar::class.java,
            ),
        )
    }

    init {
        project.tasks.named<ProcessResources>(sourceSet.processResourcesTaskName) {
            from(target.main.metadataDirectory)

            dependsOn(target.main.generateModJson)
        }

        project.ideaModule(sourceSet) {
            it.resourceDirs.add(target.main.metadataDirectory.get().asFile)
        }
    }
}
