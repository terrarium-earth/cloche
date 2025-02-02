package earth.terrarium.cloche.target

import earth.terrarium.cloche.PublicationVariant
import net.msrandom.minecraftcodev.runs.MinecraftRunConfiguration
import org.gradle.api.Action
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.spongepowered.asm.mixin.MixinEnvironment.Side
import javax.inject.Inject

internal abstract class RunnableTargetCompilation
@Inject
constructor(
    name: String,
    target: MinecraftTargetInternal<*>,
    intermediaryMinecraftFile: Provider<FileSystemLocation>,
    namedMinecraftFile: Provider<RegularFile>,
    extraClasspathFiles: FileCollection,
    variant: PublicationVariant,
    side: Side,
    isSingleTarget: Boolean,
    remapNamespace: Provider<String>,
    project: Project,
) : TargetCompilation(
    name,
    target,
    intermediaryMinecraftFile,
    namedMinecraftFile,
    extraClasspathFiles,
    variant,
    side,
    isSingleTarget,
    remapNamespace,
    project
), RunnableInternal, RunnableCompilation {
    override val runSetupActions =
        project.objects.domainObjectSet(Action::class.java) as DomainObjectCollection<Action<MinecraftRunConfiguration>>

    override fun runConfiguration(action: Action<MinecraftRunConfiguration>) {
        runSetupActions.add(action)
    }
}
