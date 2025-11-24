package earth.terrarium.cloche.target.fabric

import earth.terrarium.cloche.ClochePlugin
import earth.terrarium.cloche.api.attributes.IncludeTransformationStateAttribute
import earth.terrarium.cloche.api.target.TARGET_NAME_PATH_SEPARATOR
import earth.terrarium.cloche.api.target.compilation.FabricSecondarySourceSets
import earth.terrarium.cloche.target.LazyConfigurableInternal
import earth.terrarium.cloche.target.TargetCompilation
import earth.terrarium.cloche.target.TargetCompilationInfo
import earth.terrarium.cloche.target.lazyConfigurable
import net.msrandom.minecraftcodev.fabric.task.JarInJar
import org.gradle.api.tasks.SourceSet
import org.gradle.kotlin.dsl.newInstance
import javax.inject.Inject

internal abstract class FabricClientSecondarySourceSets @Inject constructor(info: FabricCompilationInfo) : TargetCompilation<FabricTargetImpl>(info), FabricSecondarySourceSets {
    override val data: LazyConfigurableInternal<FabricCompilationImpl> = project.lazyConfigurable {
        val data = project.objects.newInstance<FabricCompilationImpl>(
            FabricCompilationInfo(
                name + TARGET_NAME_PATH_SEPARATOR + ClochePlugin.DATA_COMPILATION_NAME,
                target,
                info.intermediaryMinecraftClasspath,
                info.namedMinecraftFile,
                info.clientMinecraftFile,
                info.finalCommonJar,
                target.main.finalMinecraftFile,
                data = true,
                test = false,
                client = project.provider { true },
            ),
        )

        target.data.onConfigured {
            it.generateModJson.configure {
                clientMixinConfigs.from(data.mixins)
            }
        }

        data
    }

    override val test: LazyConfigurableInternal<FabricCompilationImpl> = project.lazyConfigurable {
        val test = project.objects.newInstance<FabricCompilationImpl>(
            FabricCompilationInfo(
                name + TARGET_NAME_PATH_SEPARATOR + SourceSet.TEST_SOURCE_SET_NAME,
                target,
                info.intermediaryMinecraftClasspath,
                info.commonMinecraftFile,
                info.clientMinecraftFile,
                info.finalCommonJar,
                target.main.finalMinecraftFile,
                data = false,
                test = true,
                client = project.provider { true },
            ),
        )

        target.test.onConfigured {
            it.generateModJson.configure {
                clientMixinConfigs.from(test.mixins)
            }
        }

        test
    }
}
