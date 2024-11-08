package earth.terrarium.cloche.target

import earth.terrarium.cloche.*
import net.msrandom.minecraftcodev.accesswidener.AccessWiden
import net.msrandom.minecraftcodev.accesswidener.accessWidenersConfigurationName
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.decompiler.task.Decompile
import net.msrandom.minecraftcodev.forge.mappings.mcpConfigDependency
import net.msrandom.minecraftcodev.forge.mappings.mcpConfigExtraRemappingFiles
import net.msrandom.minecraftcodev.forge.patchesConfigurationName
import net.msrandom.minecraftcodev.includes.ExtractIncludes
import net.msrandom.minecraftcodev.mixins.mixinsConfigurationName
import net.msrandom.minecraftcodev.remapper.MinecraftCodevRemapperPlugin
import net.msrandom.minecraftcodev.remapper.RemapAction
import net.msrandom.minecraftcodev.remapper.mappingsConfigurationName
import net.msrandom.minecraftcodev.runs.MinecraftRunConfigurationBuilder
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.plugins.FeatureSpec
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.spongepowered.asm.mixin.MixinEnvironment.Side
import java.util.*
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
    final override val minecraftConfiguration: MinecraftConfiguration,
    private val variant: PublicationVariant,
    main: Optional<TargetCompilation>,
    side: Side,
    remapNamespace: Provider<String>,
    private val patched: Boolean,
    project: Project,
) : RunnableCompilationInternal {
    private val namePart = name.takeUnless { it == SourceSet.MAIN_SOURCE_SET_NAME }

    private val decompileMinecraft =
        project.tasks.register(
            // project.addSetupTask(
            lowerCamelCaseGradleName("decompile", target.featureName, namePart, "minecraft"),
            // ),
            Decompile::class.java,
        ) {
            // it.inputFile.set(project.layout.file(project.provider { finalMinecraftFiles.singleFile }))
        }

    final override val finalMinecraftFiles: ConfigurableFileCollection = project.files()

    final override val dependencySetupActions = mutableListOf<Action<ClocheDependencyHandler>>()
    final override val javaFeatureActions = mutableListOf<Action<FeatureSpec>>()
    final override val attributeActions = mutableListOf<Action<AttributeContainer>>()
    final override val runSetupActions = mutableListOf<Action<MinecraftRunConfigurationBuilder>>()

    override val capabilityGroup = project.group.toString()

    override val capabilityName: String = if (name == SourceSet.MAIN_SOURCE_SET_NAME) {
        project.name
    } else {
        "${project.name}-$name"
    }

    init {
        dependencies { dependencies ->
            val sourceSet = dependencies.sourceSet

            val main = with(project) {
                with(target) {
                    main.map { it.sourceSet }.orElse(this@TargetCompilation.sourceSet)
                }
            }

            val transformedView =
                project.configurations.getByName(sourceSet.compileClasspathConfigurationName).incoming.artifactView {
                    it.componentFilter {
                        it is ProjectComponentIdentifier && it.projectPath == minecraftConfiguration.dependency.dependencyProject.path
                    }

                    it.attributes { attributes ->
                        attributes
                            .attribute(TARGET_MINECRAFT_ATTRIBUTE, minecraftConfiguration.targetMinecraftAttribute)
                            .attribute(MinecraftTransformationStateAttribute.ATTRIBUTE, MinecraftTransformationStateAttribute.of(this, States.ACCESS_WIDENED))
                    }
                }

            finalMinecraftFiles.from(transformedView.files)

            decompileMinecraft.configure {
                // it.classpath.from(this@TargetCompilation.sourceSet.compileClasspath)
            }

            project.dependencies.add(sourceSet.accessWidenersConfigurationName, accessWideners)
            project.dependencies.add(sourceSet.mixinsConfigurationName, mixins)

            val remapNamespace = remapNamespace.takeIf(Provider<*>::isPresent)?.get()

            val state = if (remapNamespace == null) {
                States.INCLUDES_EXTRACTED
            } else {
                States.REMAPPED
            }

            project.configurations.named(sourceSet.compileClasspathConfigurationName) {
                it.attributes
                    .attribute(ModTransformationStateAttribute.ATTRIBUTE, ModTransformationStateAttribute.of(target, state))
                    .attribute(MinecraftTransformationStateAttribute.ATTRIBUTE, MinecraftTransformationStateAttribute.of(this, States.ACCESS_WIDENED))
            }

            project.configurations.named(sourceSet.runtimeClasspathConfigurationName) {
                it.attributes
                    .attribute(ModTransformationStateAttribute.ATTRIBUTE, ModTransformationStateAttribute.of(target, state))
                    .attribute(MinecraftTransformationStateAttribute.ATTRIBUTE, MinecraftTransformationStateAttribute.of(this, States.ACCESS_WIDENED))
            }

            setupMinecraftTransformationPipeline(
                project,
                remapNamespace,
                state,
                main,
                sourceSet,
            )
        }
    }

    override fun runConfiguration(action: Action<MinecraftRunConfigurationBuilder>) {
        runSetupActions.add(action)
    }

    override fun getName() = name

    private fun setupMinecraftTransformationPipeline(
        project: Project,
        remapNamespace: String?,
        lastState: String,
        main: SourceSet,
        sourceSet: SourceSet,
    ) {
        project.dependencies.registerTransform(ExtractIncludes::class.java) {
            it.from.attribute(MinecraftTransformationStateAttribute.ATTRIBUTE, MinecraftTransformationStateAttribute.INITIAL)
            it.to.attribute(MinecraftTransformationStateAttribute.ATTRIBUTE, MinecraftTransformationStateAttribute.of(this, States.INCLUDES_EXTRACTED))
        }

        project.dependencies.registerTransform(AccessWiden::class.java) {
            it.from.attribute(MinecraftTransformationStateAttribute.ATTRIBUTE, MinecraftTransformationStateAttribute.of(this, lastState))
            it.to.attribute(MinecraftTransformationStateAttribute.ATTRIBUTE, MinecraftTransformationStateAttribute.of(this, States.ACCESS_WIDENED))

            it.parameters { parameters ->
                parameters.namespace.set(MinecraftCodevRemapperPlugin.NAMED_MAPPINGS_NAMESPACE)
                parameters.accessWideners.from(project.configurations.named(sourceSet.accessWidenersConfigurationName))
            }
        }

        if (remapNamespace == null) {
            return
        }

        project.dependencies.registerTransform(RemapAction::class.java) {
            it.from.attribute(MinecraftTransformationStateAttribute.ATTRIBUTE, MinecraftTransformationStateAttribute.of(this, States.INCLUDES_EXTRACTED))
            it.to.attribute(MinecraftTransformationStateAttribute.ATTRIBUTE, MinecraftTransformationStateAttribute.of(this, States.REMAPPED))

            it.parameters { parameters ->
                parameters.mappings.from(project.configurations.named(main.mappingsConfigurationName))

                parameters.sourceNamespace.set(remapNamespace)

                if (patched) {
                    parameters.extraFiles.set(
                        mcpConfigDependency(project, project.configurations.getByName(main.patchesConfigurationName))
                            .flatMap { file ->
                                mcpConfigExtraRemappingFiles(project, file)
                            },
                    )
                }
            }
        }
    }

    override fun attributes(attributes: AttributeContainer) {
        super.attributes(attributes)

        attributes.attribute(TargetAttributes.MOD_LOADER, target.loaderAttributeName)
            .attributeProvider(TargetAttributes.MINECRAFT_VERSION, target.minecraftVersion)
            .attribute(VARIANT_ATTRIBUTE, variant)
    }
}
