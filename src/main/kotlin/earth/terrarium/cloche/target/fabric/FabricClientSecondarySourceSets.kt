package earth.terrarium.cloche.target.fabric

import earth.terrarium.cloche.ClochePlugin
import earth.terrarium.cloche.PublicationSide
import earth.terrarium.cloche.api.target.TARGET_NAME_PATH_SEPARATOR
import earth.terrarium.cloche.api.target.compilation.TargetSecondarySourceSets
import earth.terrarium.cloche.target.LazyConfigurableInternal
import earth.terrarium.cloche.target.MinecraftTargetInternal
import earth.terrarium.cloche.target.TargetCompilation
import earth.terrarium.cloche.target.lazyConfigurable
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.spongepowered.asm.mixin.MixinEnvironment.Side
import javax.inject.Inject

internal abstract class FabricClientSecondarySourceSets @Inject constructor(
    name: String,
    target: MinecraftTargetInternal,
    intermediaryMinecraftClasspath: FileCollection,
    namedMinecraftFile: Provider<RegularFile>,
    extraClasspathFiles: Provider<List<RegularFile>>,
    variant: PublicationSide,
    side: Side,
    isSingleTarget: Boolean,
) : TargetCompilation(
    name,
    target,
    intermediaryMinecraftClasspath,
    namedMinecraftFile,
    extraClasspathFiles,
    variant,
    side,
    isSingleTarget,
), TargetSecondarySourceSets {
    override val data: LazyConfigurableInternal<TargetCompilation> = project.lazyConfigurable {
        project.objects.newInstance(
            TargetCompilation::class.java,
            name + TARGET_NAME_PATH_SEPARATOR + ClochePlugin.DATA_COMPILATION_NAME,
            target,
            intermediaryMinecraftClasspath,
            namedMinecraftFile,
            extraClasspathFiles,
            PublicationSide.Client,
            side,
            isSingleTarget,
        )
    }

    override val test: LazyConfigurableInternal<TargetCompilation> = project.lazyConfigurable {
        project.objects.newInstance(
            TargetCompilation::class.java,
            name + TARGET_NAME_PATH_SEPARATOR + SourceSet.TEST_SOURCE_SET_NAME,
            target,
            intermediaryMinecraftClasspath,
            namedMinecraftFile,
            extraClasspathFiles,
            PublicationSide.Client,
            side,
            isSingleTarget,
        )
    }
}
