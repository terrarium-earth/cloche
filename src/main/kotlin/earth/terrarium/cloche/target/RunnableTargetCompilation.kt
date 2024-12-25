package earth.terrarium.cloche.target

import earth.terrarium.cloche.*
import net.msrandom.minecraftcodev.accesswidener.AccessWiden
import net.msrandom.minecraftcodev.accesswidener.accessWidenersConfigurationName
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.decompiler.task.Decompile
import net.msrandom.minecraftcodev.mixins.mixinsConfigurationName
import net.msrandom.minecraftcodev.remapper.MinecraftCodevRemapperPlugin
import net.msrandom.minecraftcodev.runs.MinecraftRunConfigurationBuilder
import org.gradle.api.Action
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Project
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.spongepowered.asm.mixin.MixinEnvironment.Side
import javax.inject.Inject

internal abstract class RunnableTargetCompilation
@Inject
constructor(
    name: String,
    target: MinecraftTargetInternal,
    intermediaryMinecraftFile: Provider<FileSystemLocation>,
    namedMinecraftFile: Provider<RegularFile>,
    targetMinecraftAttribute: Provider<String>,
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
    targetMinecraftAttribute,
    variant,
    side,
    isSingleTarget,
    remapNamespace,
    project
), RunnableInternal, RunnableCompilation {
    override val runSetupActions =
        project.objects.domainObjectSet(Action::class.java) as DomainObjectCollection<Action<MinecraftRunConfigurationBuilder>>

    override fun runConfiguration(action: Action<MinecraftRunConfigurationBuilder>) {
        runSetupActions.add(action)
    }
}
