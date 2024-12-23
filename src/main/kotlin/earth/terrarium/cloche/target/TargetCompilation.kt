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

internal object States {
    const val INCLUDES_EXTRACTED = "includesExtracted"
    const val MIXINS_STRIPPED = "mixinsStripped"
    const val REMAPPED = "remapped"
    const val MIXIN = "mixin"
    const val ACCESS_WIDENED = "accessWidened"
}

internal abstract class TargetCompilation
@Inject
constructor(
    private val name: String,
    final override val target: MinecraftTargetInternal,
    override val intermediaryMinecraftFile: Provider<FileSystemLocation>,
    private val namedMinecraftFile: Provider<RegularFile>,
    override val targetMinecraftAttribute: Provider<String>,
    private val variant: PublicationVariant,
    side: Side,
    isSingleTarget: Boolean,
    remapNamespace: Provider<String>,
    project: Project,
) : RunnableCompilationInternal {
    private val namePart = name.takeUnless { it == SourceSet.MAIN_SOURCE_SET_NAME }

    private val accessWidenTask = project.tasks.register(project.addSetupTask(lowerCamelCaseGradleName("accessWiden", target.featureName, namePart, "minecraft")), AccessWiden::class.java) {
        it.inputFile.set(namedMinecraftFile)
        it.namespace.set(MinecraftCodevRemapperPlugin.NAMED_MAPPINGS_NAMESPACE)
        it.accessWideners.from(project.configurations.named(sourceSet.accessWidenersConfigurationName))
    }

    final override val finalMinecraftFile: Provider<RegularFile> = accessWidenTask.flatMap(AccessWiden::outputFile)

    override val dependencySetupActions = project.objects.domainObjectSet(Action::class.java) as DomainObjectCollection<Action<ClocheDependencyHandler>>
    override val attributeActions = project.objects.domainObjectSet(Action::class.java) as DomainObjectCollection<Action<AttributeContainer>>

    override var withJavadoc: Boolean = false
    override var withSources: Boolean = false

    override val runSetupActions = project.objects.domainObjectSet(Action::class.java) as DomainObjectCollection<Action<MinecraftRunConfigurationBuilder>>

    final override val sourceSet: SourceSet

    init {
        val name = if (isSingleTarget) {
            name
        } else {
            sourceSetName(this, target)
        }

        sourceSet = project.extension<SourceSetContainer>().maybeCreate(name)

        project.tasks.register(
            //project.addSetupTask(
                lowerCamelCaseGradleName("decompile", target.featureName, namePart, "minecraft")
            //)
            , Decompile::class.java,
        ) {
            it.inputFile.set(finalMinecraftFile)
            it.classpath.from(this@TargetCompilation.sourceSet.compileClasspath)
        }

        dependencies { dependencies ->
            val sourceSet = dependencies.sourceSet

            project.dependencies.add(sourceSet.accessWidenersConfigurationName, accessWideners)
            project.dependencies.add(sourceSet.mixinsConfigurationName, mixins)

            val remapNamespace = remapNamespace.takeIf(Provider<*>::isPresent)?.get()

            val state = if (remapNamespace == null) {
                States.INCLUDES_EXTRACTED
            } else {
                States.REMAPPED
            }

            project.configurations.named(sourceSet.compileClasspathConfigurationName) {
                it.attributes.attribute(ModTransformationStateAttribute.ATTRIBUTE, ModTransformationStateAttribute.of(target, state))
            }

            project.configurations.named(sourceSet.runtimeClasspathConfigurationName) {
                it.attributes.attribute(ModTransformationStateAttribute.ATTRIBUTE, ModTransformationStateAttribute.of(target, state))
            }

            // Use detached configuration for idea compat
            val minecraftFiles = project.files(finalMinecraftFile)
            val minecraftFileConfiguration = project.configurations.detachedConfiguration(project.dependencies.create(minecraftFiles))

            sourceSet.compileClasspath += minecraftFileConfiguration
            sourceSet.runtimeClasspath += minecraftFileConfiguration
        }
    }

    override fun runConfiguration(action: Action<MinecraftRunConfigurationBuilder>) {
        runSetupActions.add(action)
    }

    override fun getName() = name

    override fun attributes(attributes: AttributeContainer) {
        super.attributes(attributes)

        attributes.attribute(TargetAttributes.MOD_LOADER, target.loaderAttributeName)
            .attributeProvider(TargetAttributes.MINECRAFT_VERSION, target.minecraftVersion)
            .attribute(VARIANT_ATTRIBUTE, variant)
    }
}
