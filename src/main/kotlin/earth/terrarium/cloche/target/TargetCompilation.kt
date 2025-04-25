package earth.terrarium.cloche.target

import earth.terrarium.cloche.ModTransformationStateAttribute
import earth.terrarium.cloche.PublicationSide
import earth.terrarium.cloche.SIDE_ATTRIBUTE
import earth.terrarium.cloche.TargetAttributes
import net.msrandom.minecraftcodev.accesswidener.AccessWiden
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.getGlobalCacheDirectory
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseName
import net.msrandom.minecraftcodev.decompiler.task.Decompile
import net.msrandom.minecraftcodev.mixins.StripMixins
import net.msrandom.minecraftcodev.mixins.mixinsConfigurationName
import net.msrandom.minecraftcodev.mixins.task.Mixin
import net.msrandom.minecraftcodev.remapper.MinecraftCodevRemapperPlugin
import net.msrandom.minecraftcodev.remapper.RemapAction
import net.msrandom.minecraftcodev.remapper.task.LoadMappings
import net.msrandom.minecraftcodev.remapper.task.RemapJar
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.spongepowered.asm.mixin.MixinEnvironment
import javax.inject.Inject

internal fun Project.getModFiles(
    configurationName: String,
    isTransitive: Boolean = true,
    configure: Action<ArtifactView.ViewConfiguration>? = null
): FileCollection {
    val classpath = project.configurations.named(configurationName)

    val modDependencies = project.configurations.named(modConfigurationName(configurationName))

    return project.files(classpath.zip(modDependencies) { classpath, modDependencies ->
        val resolutionResult = modDependencies.incoming.resolutionResult

        val componentIdentifiers = if (isTransitive) {
            resolutionResult.allComponents.map(ResolvedComponentResult::getId) - resolutionResult.root.id
        } else {
            resolutionResult.root.dependencies.filterIsInstance<ResolvedDependencyResult>().map { it.selected.id }
        }

        classpath.incoming.artifactView {
            it.componentFilter(componentIdentifiers::contains)

            configure?.execute(it)
        }.files
    })
}

internal fun registerCompilationTransformations(
    target: MinecraftTargetInternal,
    compilationName: String,
    sourceSet: SourceSet,
    namedMinecraftFile: Provider<RegularFile>,
    extraClasspathFiles: Provider<List<RegularFile>>,
    side: MixinEnvironment.Side,
    attributeCompilationName: String = compilationName
): Triple<TaskProvider<AccessWiden>, TaskProvider<Mixin>, Provider<RegularFile>> {
    val collapsedName = compilationName.takeUnless { it == SourceSet.MAIN_SOURCE_SET_NAME }
    val collapsedAttributeName = attributeCompilationName.takeUnless { it == SourceSet.MAIN_SOURCE_SET_NAME }

    val project = target.project

    val accessWidenTask = project.tasks.register(
        lowerCamelCaseGradleName("accessWiden", target.featureName, collapsedName, "minecraft"),
        AccessWiden::class.java,
    ) {
        it.group = "minecraft-transforms"

        it.inputFile.set(namedMinecraftFile)
        it.namespace.set(MinecraftCodevRemapperPlugin.NAMED_MAPPINGS_NAMESPACE)

        it.accessWideners.from(project.getModFiles(sourceSet.runtimeClasspathConfigurationName) {
            it.attributes {
                it.attribute(
                    ModTransformationStateAttribute.ATTRIBUTE,
                    lowerCamelCaseName(target.featureName, collapsedAttributeName, ModTransformationStateAttribute.REMAPPED)
                )
            }
        })
    }

    val mixinTask: TaskProvider<Mixin> = project.tasks.register(
        lowerCamelCaseGradleName("mixin", target.featureName, collapsedName, "minecraft"),
        Mixin::class.java
    ) {
        it.group = "minecraft-transforms"

        it.inputFile.set(accessWidenTask.flatMap(AccessWiden::outputFile))

        it.sourceNamespace.set(target.modRemapNamespace)
        it.targetNamespace.set(MinecraftCodevRemapperPlugin.NAMED_MAPPINGS_NAMESPACE)
        it.mappings.set(target.loadMappingsTask.flatMap(LoadMappings::output))

        val modCompileClasspath = project.getModFiles(sourceSet.compileClasspathConfigurationName) {
            it.attributes {
                it.attribute(
                    ModTransformationStateAttribute.ATTRIBUTE,
                    lowerCamelCaseName(target.featureName, collapsedAttributeName, ModTransformationStateAttribute.REMAPPED)
                )
            }
        }
        val modRuntimeClasspath = project.getModFiles(sourceSet.runtimeClasspathConfigurationName) {
            it.attributes {
                it.attribute(
                    ModTransformationStateAttribute.ATTRIBUTE,
                    lowerCamelCaseName(target.featureName, collapsedAttributeName, ModTransformationStateAttribute.REMAPPED)
                )
            }
        }

        it.mixinFiles.from(modCompileClasspath)
        it.mixinFiles.from(modRuntimeClasspath)

        it.classpath.from(project.configurations.named(sourceSet.compileClasspathConfigurationName).map {
            it.incoming.artifactView {
                it.attributes {
                    it.attribute(
                        ModTransformationStateAttribute.ATTRIBUTE,
                        lowerCamelCaseName(target.featureName, collapsedAttributeName, ModTransformationStateAttribute.REMAPPED)
                    )
                }
            }.files
        })
        it.classpath.from(project.configurations.named(sourceSet.runtimeClasspathConfigurationName).map {
            it.incoming.artifactView {
                it.attributes {
                    it.attribute(
                        ModTransformationStateAttribute.ATTRIBUTE,
                        lowerCamelCaseName(target.featureName, collapsedAttributeName, ModTransformationStateAttribute.REMAPPED)
                    )
                }
            }.files
        })
        it.classpath.from(extraClasspathFiles)

        it.side.set(side)
    }

    val finalMinecraftFile = mixinTask.flatMap(Mixin::outputFile)

    val decompile = project.tasks.register(
        lowerCamelCaseGradleName("decompile", target.featureName, collapsedName, "minecraft"),
        Decompile::class.java,
    ) {
        it.group = "sources"

        it.inputFile.set(finalMinecraftFile)
        it.classpath.from(project.configurations.named(sourceSet.compileClasspathConfigurationName))
        it.classpath.from(extraClasspathFiles)
    }

    return Triple(accessWidenTask, mixinTask, decompile.flatMap(Decompile::outputFile))
}

internal fun compilationSourceSet(target: MinecraftTargetInternal, name: String, isSingleTarget: Boolean): SourceSet {
    val name = if (isSingleTarget) {
        name
    } else {
        sourceSetName(name, target)
    }

    return target.project.extension<SourceSetContainer>().maybeCreate(name)
}

private fun setupModTransformationPipeline(
    project: Project,
    target: MinecraftTargetInternal,
    compilation: TargetCompilation,
) {
    // afterEvaluate needed as the registration of a transform is dependent on a lazy provider
    //  this can potentially be changed to a no-op transform but that's far slower
    project.afterEvaluate {
        if (target.modRemapNamespace.get().isEmpty()) {
            return@afterEvaluate
        }

        project.dependencies.registerTransform(RemapAction::class.java) {
            it.from.attribute(
                ModTransformationStateAttribute.ATTRIBUTE,
                ModTransformationStateAttribute.INITIAL,
            )

            it.to.attribute(
                ModTransformationStateAttribute.ATTRIBUTE,
                ModTransformationStateAttribute.of(target, compilation, ModTransformationStateAttribute.REMAPPED),
            )

            it.parameters {
                it.mappings.set(target.loadMappingsTask.flatMap(LoadMappings::output))

                it.sourceNamespace.set(target.modRemapNamespace.get())

                it.extraClasspath.from(compilation.intermediaryMinecraftClasspath)

                it.cacheDirectory.set(getGlobalCacheDirectory(project))

                val modCompileClasspath = project.getModFiles(compilation.sourceSet.compileClasspathConfigurationName) {
                    it.attributes {
                        it.attribute(
                            ModTransformationStateAttribute.ATTRIBUTE,
                            ModTransformationStateAttribute.INITIAL,
                        )
                    }
                }

                val modRuntimeClasspath = project.getModFiles(compilation.sourceSet.runtimeClasspathConfigurationName) {
                    it.attributes {
                        it.attribute(
                            ModTransformationStateAttribute.ATTRIBUTE,
                            ModTransformationStateAttribute.INITIAL,
                        )
                    }
                }

                it.modFiles.from(modCompileClasspath)
                it.modFiles.from(modRuntimeClasspath)
            }
        }

        project.dependencies.registerTransform(StripMixins::class.java) {
            it.from.attribute(
                ModTransformationStateAttribute.ATTRIBUTE,
                ModTransformationStateAttribute.of(target, compilation, ModTransformationStateAttribute.REMAPPED),
            )

            it.to.attribute(
                ModTransformationStateAttribute.ATTRIBUTE,
                ModTransformationStateAttribute.of(
                    target,
                    compilation,
                    ModTransformationStateAttribute.MIXINS_STRIPPED
                ),
            )

            it.parameters {
                it.appliedMixins.from(compilation.setupFiles.second.flatMap(Mixin::appliedMixins))
            }
        }
    }
}

internal abstract class TargetCompilation
@Inject
constructor(
    private val name: String,
    override val target: MinecraftTargetInternal,
    val intermediaryMinecraftClasspath: FileCollection,
    namedMinecraftFile: Provider<RegularFile>,
    val extraClasspathFiles: Provider<List<RegularFile>>,
    private val variant: PublicationSide,
    side: MixinEnvironment.Side,
    isSingleTarget: Boolean,
) : CompilationInternal() {
    final override val sourceSet: SourceSet = compilationSourceSet(target, name, isSingleTarget)

    internal val setupFiles = registerCompilationTransformations(
        target,
        name,
        sourceSet,
        namedMinecraftFile,
        extraClasspathFiles,
        side
    )

    val finalMinecraftFile: Provider<RegularFile> = setupFiles.second.flatMap(Mixin::outputFile)
    val sources = setupFiles.third

    val remapJarTask: TaskProvider<RemapJar> = project.tasks.register(
        lowerCamelCaseGradleName(sourceSet.takeUnless(SourceSet::isMain)?.name, "remapJar"),
        RemapJar::class.java
    ) {
        it.input.set(project.tasks.named(sourceSet.jarTaskName, Jar::class.java).flatMap(Jar::getArchiveFile))
        it.sourceNamespace.set(MinecraftCodevRemapperPlugin.NAMED_MAPPINGS_NAMESPACE)
        it.targetNamespace.set(target.modRemapNamespace)
        it.classpath.from(sourceSet.compileClasspath)

        it.mappings.set(target.loadMappingsTask.flatMap(LoadMappings::output))
    }

    init {
        project.dependencies.add(sourceSet.mixinsConfigurationName, mixins)

        setupModTransformationPipeline(project, target, this)

        val state = target.modRemapNamespace.map {
            if (it.isEmpty()) {
                ModTransformationStateAttribute.INITIAL
            } else {
                ModTransformationStateAttribute.of(target, this, ModTransformationStateAttribute.MIXINS_STRIPPED)
            }
        }

        project.configurations.named(sourceSet.compileClasspathConfigurationName) {
            it.attributes.attributeProvider(ModTransformationStateAttribute.ATTRIBUTE, state)

            it.extendsFrom(target.mappingsBuildDependenciesHolder)
        }

        project.configurations.named(sourceSet.runtimeClasspathConfigurationName) {
            it.attributes.attributeProvider(ModTransformationStateAttribute.ATTRIBUTE, state)

            it.extendsFrom(target.mappingsBuildDependenciesHolder)
        }

        setupFiles.first.configure {
            it.accessWideners.from(accessWideners)
        }

        // Use detached configuration for idea compat
        val minecraftFiles = project.files(finalMinecraftFile, extraClasspathFiles)
        val minecraftFileConfiguration =
            project.configurations.detachedConfiguration(project.dependencies.create(minecraftFiles))

        sourceSet.compileClasspath += minecraftFileConfiguration
        sourceSet.runtimeClasspath += minecraftFileConfiguration
    }

    override fun getName() = name

    override fun attributes(attributes: AttributeContainer) {
        super.attributes(attributes)

        attributes.attribute(TargetAttributes.MOD_LOADER, target.loaderName)
            .attributeProvider(TargetAttributes.MINECRAFT_VERSION, target.minecraftVersion)
            .attribute(SIDE_ATTRIBUTE, variant)
    }
}
