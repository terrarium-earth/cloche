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
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.plugins.FeatureSpec
import org.gradle.api.tasks.SourceSet
import org.spongepowered.asm.mixin.MixinEnvironment.Side
import java.util.*
import javax.inject.Inject

class ModTransformationCompatibilityRule : AttributeCompatibilityRule<ModTransformationState> {
    override fun execute(details: CompatibilityCheckDetails<ModTransformationState>) {
        if (details.producerValue == null) {
            details.compatible()
        }
    }
}

enum class ModTransformationState {
    None,
    IncludesExtracted,

    // MixinsStripped,
    Remapped,
}

enum class MinecraftTransformationState {
    None,

    // Mixin,
    IncludesExtracted,
    Remapped,
    AccessWidened,
}

internal abstract class TargetCompilation
@Inject
constructor(
    private val name: String,
    private val target: MinecraftTargetInternal,
    final override val minecraftConfiguration: MinecraftConfiguration,
    private val variant: PublicationVariant,
    main: Optional<TargetCompilation>,
    side: Side,
    remapNamespace: String?,
    project: Project,
) : RunnableCompilationInternal {
    private val namePart = name.takeUnless { it == SourceSet.MAIN_SOURCE_SET_NAME }

    private val decompileMinecraft =
        project.tasks.register(
            // project.addSetupTask(
            lowerCamelCaseGradleName("decompile", target.name, namePart, "minecraft"),
            // ),
            Decompile::class.java,
        ) {
            // it.inputFile.set(project.layout.file(project.provider { finalMinecraftFiles.singleFile }))
        }

    final override val finalMinecraftFiles: ConfigurableFileCollection = project.files()

    final override val dependencySetupActions = mutableListOf<Action<ClocheDependencyHandler>>()
    final override val javaFeatureActions = mutableListOf<Action<FeatureSpec>>()
    final override val runSetupActions = mutableListOf<Action<MinecraftRunConfigurationBuilder>>()

    override val capabilityGroup = project.group.toString()

    override val capabilityName: String = if (name == SourceSet.MAIN_SOURCE_SET_NAME) {
        project.name
    } else {
        "${project.name}-$name"
    }

    init {
        val modTransformationStateAttribute =
            Attribute.of(
                lowerCamelCaseGradleName(target.name, namePart, "modTransformationState"),
                ModTransformationState::class.java,
            )

        val minecraftTransformationStateAttribute =
            Attribute.of(
                lowerCamelCaseGradleName(target.name, namePart, "minecraftTransformationState"),
                MinecraftTransformationState::class.java,
            )

        project.dependencies.attributesSchema { schema ->
            schema.attribute(modTransformationStateAttribute) {
                it.compatibilityRules.add(ModTransformationCompatibilityRule::class.java)
            }

            schema.attribute(minecraftTransformationStateAttribute)
        }

        project.dependencies.artifactTypes {
            it.named(ArtifactTypeDefinition.JAR_TYPE) { jar ->
                jar.attributes.attribute(modTransformationStateAttribute, ModTransformationState.None)
            }
        }

        minecraftConfiguration.attributes {
            it
                .attribute(minecraftTransformationStateAttribute, MinecraftTransformationState.None)
                .attribute(modTransformationStateAttribute, ModTransformationState.Remapped)
        }

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
                        attributes.attribute(TARGET_MINECRAFT_ATTRIBUTE, minecraftConfiguration.targetMinecraftAttribute)
                    }
                }

            finalMinecraftFiles.from(transformedView.files)

            decompileMinecraft.configure {
                // it.classpath.from(this@TargetCompilation.sourceSet.compileClasspath)
            }

            project.dependencies.add(sourceSet.accessWidenersConfigurationName, accessWideners)
            project.dependencies.add(sourceSet.mixinsConfigurationName, mixins)

            project.configurations.named(sourceSet.compileClasspathConfigurationName) {
                it.attributes
                    .attribute(modTransformationStateAttribute, ModTransformationState.Remapped)
                    .attribute(minecraftTransformationStateAttribute, MinecraftTransformationState.None)
                    // .attribute(minecraftTransformationStateAttribute, MinecraftTransformationState.AccessWidened)
            }

            project.configurations.named(sourceSet.runtimeClasspathConfigurationName) {
                it.attributes
                    .attribute(modTransformationStateAttribute, ModTransformationState.Remapped)
                    .attribute(minecraftTransformationStateAttribute, MinecraftTransformationState.None)
                    // .attribute(minecraftTransformationStateAttribute, MinecraftTransformationState.AccessWidened)
            }

            setupMinecraftTransformationPipeline(
                project,
                minecraftTransformationStateAttribute,
                remapNamespace,
                main,
                sourceSet,
            )

            setupModTransformationPipeline(
                project,
                modTransformationStateAttribute,
                remapNamespace,
                main,
            )
        }
    }

    override fun dependencies(action: Action<ClocheDependencyHandler>) {
        dependencySetupActions.add(action)
    }

    override fun java(action: Action<FeatureSpec>) {
        javaFeatureActions.add(action)
    }

    override fun runConfiguration(action: Action<MinecraftRunConfigurationBuilder>) {
        runSetupActions.add(action)
    }

    override fun getName() = name

    private fun setupModTransformationPipeline(
        project: Project,
        modTransformationStateAttribute: Attribute<ModTransformationState>,
        remapNamespace: String?,
        main: SourceSet,
    ) {
        project.dependencies.registerTransform(ExtractIncludes::class.java) {
            it.from
                .attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
                .attribute(modTransformationStateAttribute, ModTransformationState.None)

            it.to
                .attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
                .attribute(modTransformationStateAttribute, ModTransformationState.IncludesExtracted)
        }

        project.dependencies.registerTransform(RemapAction::class.java) {
            it.from
                .attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
                .attribute(modTransformationStateAttribute, ModTransformationState.IncludesExtracted)

            it.to
                .attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
                .attribute(modTransformationStateAttribute, ModTransformationState.Remapped)

            it.parameters { parameters ->
                parameters.mappings.from(project.configurations.named(main.mappingsConfigurationName))

                parameters.sourceNamespace.set(remapNamespace)
                parameters.extraClasspath.from(project.files(minecraftConfiguration.artifact))

                val patchesConfiguration = project.configurations.getByName(main.patchesConfigurationName)

                if (patchesConfiguration.allDependencies.isNotEmpty()) {
                    parameters.extraFiles.set(
                        mcpConfigDependency(project, patchesConfiguration)
                            .flatMap { file ->
                                mcpConfigExtraRemappingFiles(project, file)
                            },
                    )
                }
            }
        }
    }

    private fun setupMinecraftTransformationPipeline(
        project: Project,
        minecraftTransformationStateAttribute: Attribute<MinecraftTransformationState>,
        remapNamespace: String?,
        main: SourceSet,
        sourceSet: SourceSet,
    ) {
        project.dependencies.registerTransform(ExtractIncludes::class.java) {
            it.from.attribute(minecraftTransformationStateAttribute, MinecraftTransformationState.None)
            it.to.attribute(minecraftTransformationStateAttribute, MinecraftTransformationState.IncludesExtracted)
        }

        project.dependencies.registerTransform(RemapAction::class.java) {
            it.from.attribute(minecraftTransformationStateAttribute, MinecraftTransformationState.IncludesExtracted)
            it.to.attribute(minecraftTransformationStateAttribute, MinecraftTransformationState.Remapped)

            it.parameters { parameters ->
                parameters.mappings.from(project.configurations.named(main.mappingsConfigurationName))

                parameters.sourceNamespace.set(remapNamespace)

                val patchesConfiguration = project.configurations.getByName(main.patchesConfigurationName)

                if (patchesConfiguration.allDependencies.isNotEmpty()) {
                    parameters.extraFiles.set(
                        mcpConfigDependency(project, patchesConfiguration)
                            .flatMap { file ->
                                mcpConfigExtraRemappingFiles(project, file)
                            },
                    )
                }
            }
        }

        project.dependencies.registerTransform(AccessWiden::class.java) {
            it.from.attribute(minecraftTransformationStateAttribute, MinecraftTransformationState.Remapped)
            it.to.attribute(minecraftTransformationStateAttribute, MinecraftTransformationState.AccessWidened)

            it.parameters { parameters ->
                parameters.namespace.set(MinecraftCodevRemapperPlugin.NAMED_MAPPINGS_NAMESPACE)
                parameters.accessWideners.from(project.configurations.named(sourceSet.accessWidenersConfigurationName))
            }
        }
    }

    override fun attributes(attributes: AttributeContainer) {
        attributes.attribute(MOD_LOADER_ATTRIBUTE, target.loaderAttributeName)
            .attributeProvider(MINECRAFT_VERSION_ATTRIBUTE, target.minecraftVersion)
            .attribute(VARIANT_ATTRIBUTE, variant)
    }
}
