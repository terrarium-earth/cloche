package earth.terrarium.cloche.target.fabric

import earth.terrarium.cloche.ClochePlugin
import earth.terrarium.cloche.api.target.compilation.FabricSecondarySourceSets
import earth.terrarium.cloche.target.LazyConfigurableInternal
import earth.terrarium.cloche.target.TargetCompilation
import earth.terrarium.cloche.target.lazyConfigurable
import org.gradle.kotlin.dsl.newInstance
import javax.inject.Inject

internal abstract class FabricClientSecondarySourceSets @Inject constructor(info: FabricCompilationInfo) : TargetCompilation<FabricTargetImpl>(info), FabricSecondarySourceSets {
    override val data: LazyConfigurableInternal<FabricCompilationImpl> = project.lazyConfigurable {
        val data = project.objects.newInstance<FabricCompilationImpl>(
            FabricCompilationInfo(
                ClochePlugin.CLIENT_DATA_COMPILATION_NAME,
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
                ClochePlugin.CLIENT_TEST_COMPILATION_NAME,
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
