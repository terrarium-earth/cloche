package earth.terrarium.cloche.target.fabric

import earth.terrarium.cloche.ClochePlugin
import earth.terrarium.cloche.IncludeTransformationState
import earth.terrarium.cloche.PublicationSide
import earth.terrarium.cloche.api.target.TARGET_NAME_PATH_SEPARATOR
import earth.terrarium.cloche.api.target.compilation.TargetSecondarySourceSets
import earth.terrarium.cloche.target.LazyConfigurableInternal
import earth.terrarium.cloche.target.TargetCompilation
import earth.terrarium.cloche.target.TargetCompilationInfo
import earth.terrarium.cloche.target.lazyConfigurable
import org.gradle.api.tasks.SourceSet
import javax.inject.Inject

internal abstract class FabricClientSecondarySourceSets @Inject constructor(info: TargetCompilationInfo) : TargetCompilation(info), TargetSecondarySourceSets {
    override val data: LazyConfigurableInternal<TargetCompilation> = project.lazyConfigurable {
        project.objects.newInstance(
            TargetCompilation::class.java,
            TargetCompilationInfo(
                name + TARGET_NAME_PATH_SEPARATOR + ClochePlugin.DATA_COMPILATION_NAME,
                target,
                info.intermediaryMinecraftClasspath,
                info.namedMinecraftFile,
                info.extraClasspathFiles,
                PublicationSide.Client,
                true,
                info.isSingleTarget,
                IncludeTransformationState.Stripped,
            ),
        )
    }

    override val test: LazyConfigurableInternal<TargetCompilation> = project.lazyConfigurable {
        project.objects.newInstance(
            TargetCompilation::class.java,
            TargetCompilationInfo(
                name + TARGET_NAME_PATH_SEPARATOR + SourceSet.TEST_SOURCE_SET_NAME,
                target,
                info.intermediaryMinecraftClasspath,
                info.namedMinecraftFile,
                info.extraClasspathFiles,
                PublicationSide.Client,
                false,
                info.isSingleTarget,
                IncludeTransformationState.Stripped,
            ),
        )
    }
}
