package earth.terrarium.cloche.target

import earth.terrarium.cloche.ClocheDependencyHandler
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
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier
import org.spongepowered.asm.mixin.MixinEnvironment.Side
import java.util.*
import javax.inject.Inject

enum class ModTransformationState {
    None,
    IncludesExtracted,

    // MixinsStripped,
    Remapped,
    AccessWidened,
}

abstract class TargetCompilation
    @Inject
    constructor(
        private val name: String,
        target: MinecraftTarget,
        intermediateMinecraft: Provider<RegularFile>,
        main: Optional<TargetCompilation>,
        side: Side,
        remapNamespace: String?,
        classpath: FileCollection,
        project: Project,
    ) : RunnableCompilationInternal {
        private val namePart = name.takeUnless { it == SourceSet.MAIN_SOURCE_SET_NAME }

    /*
    private val mixinTask = project.tasks.register(lowerCamelCaseGradleName("mixin", target.name, namePart, "Minecraft"), Mixin::class.java) {
        it.inputFile.set(intermediateMinecraft)
        it.mixinFiles.from(extractCompileIncludes.map(ExtractIncludes::outputFiles))
        it.classpath.from(classpath)
        it.side.set(side)
    }*/

        private val decompileMinecraft =
            project.tasks.register( // project.addSetupTask(
                lowerCamelCaseGradleName("decompile", target.name, namePart, "minecraft")/*)*/,
                Decompile::class.java,
            ) {
                // it.inputFile.set(project.layout.file(project.provider { finalMinecraftFiles.singleFile }))
            }

        override val dependencyMinecraftFile = intermediateMinecraft

        final override val finalMinecraftFiles: ConfigurableFileCollection = project.files()

        final override val dependencySetupActions = mutableListOf<Action<ClocheDependencyHandler>>()
        final override val runSetupActions = mutableListOf<Action<MinecraftRunConfigurationBuilder>>()

        init {
            val modTransformationStateAttribute =
                Attribute.of(
                    lowerCamelCaseGradleName(target.name, namePart, "modTransformationState"),
                    ModTransformationState::class.java,
                )

            project.dependencies.attributesSchema {
                it.attribute(modTransformationStateAttribute)
            }

            project.dependencies.artifactTypes {
                it.named(ArtifactTypeDefinition.JAR_TYPE) { jar ->
                    jar.attributes.attribute(modTransformationStateAttribute, ModTransformationState.None)
                }
            }

            project.afterEvaluate {
                with(project) {
                    with(target) {
                        val main = main.map { it.sourceSet }.orElse(this@TargetCompilation.sourceSet)

                        val transformedView =
                            project.configurations.getByName(main.compileClasspathConfigurationName).incoming.artifactView {
                                it.componentFilter {
                                    it is OpaqueComponentArtifactIdentifier && it.file == dependencyMinecraftFile.get().asFile
                                }
                            }

                        finalMinecraftFiles.from(transformedView.files)

                        decompileMinecraft.configure {
                            // it.classpath.from(this@TargetCompilation.sourceSet.compileClasspath)
                        }

                        project.dependencies.add(sourceSet.accessWidenersConfigurationName, accessWideners)
                        project.dependencies.add(sourceSet.mixinsConfigurationName, mixins)

                        project.configurations.named(sourceSet.compileClasspathConfigurationName) {
                            it.attributes.attribute(modTransformationStateAttribute, ModTransformationState.AccessWidened)
                        }

                        project.configurations.named(sourceSet.runtimeClasspathConfigurationName) {
                            it.attributes.attribute(modTransformationStateAttribute, ModTransformationState.Remapped)
                        }

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

                                parameters.extraClasspath.from(classpath + files(intermediateMinecraft))

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
                            it.from
                                .attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
                                .attribute(modTransformationStateAttribute, ModTransformationState.Remapped)

                            it.to
                                .attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
                                .attribute(modTransformationStateAttribute, ModTransformationState.AccessWidened)

                            it.parameters { parameters ->
                                parameters.namespace.set(MinecraftCodevRemapperPlugin.NAMED_MAPPINGS_NAMESPACE)
                                parameters.accessWideners.from(project.configurations.named(sourceSet.accessWidenersConfigurationName))
                            }
                        }
                    }
                }
            }
        }

        override fun dependencies(action: Action<ClocheDependencyHandler>) {
            dependencySetupActions.add(action)
        }

        override fun runConfiguration(action: Action<MinecraftRunConfigurationBuilder>) {
            runSetupActions.add(action)
        }

        override fun getName() = name
    }
