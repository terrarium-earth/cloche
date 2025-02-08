package earth.terrarium.cloche.target

import earth.terrarium.cloche.*
import net.msrandom.minecraftcodev.accesswidener.AccessWiden
import net.msrandom.minecraftcodev.accesswidener.accessWidenersConfigurationName
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.getGlobalCacheDirectory
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.decompiler.task.Decompile
import net.msrandom.minecraftcodev.includes.ExtractIncludes
import net.msrandom.minecraftcodev.mixins.mixinsConfigurationName
import net.msrandom.minecraftcodev.remapper.MinecraftCodevRemapperPlugin
import net.msrandom.minecraftcodev.remapper.RemapAction
import net.msrandom.minecraftcodev.remapper.task.LoadMappings
import org.gradle.api.Project
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.FileCollection
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

internal fun registerCompilationTransformations(
    target: MinecraftTargetInternal<*>,
    compilationName: String,
    sourceSet: SourceSet,
    namedMinecraftFile: Provider<RegularFile>,
    extraClasspathFiles: FileCollection,
): Provider<RegularFile> {
    val namePart = compilationName.takeUnless { it == SourceSet.MAIN_SOURCE_SET_NAME }

    val project = target.project

    val accessWidenTask = project.tasks.maybeRegister(
        project.addSetupTask(
            lowerCamelCaseGradleName(
                "accessWiden",
                target.featureName,
                namePart,
                "minecraft"
            )
        ), AccessWiden::class.java
    ) {
        it.group = "minecraft-transforms"

        it.inputFile.set(namedMinecraftFile)
        it.namespace.set(MinecraftCodevRemapperPlugin.NAMED_MAPPINGS_NAMESPACE)
        it.accessWideners.from(project.configurations.named(sourceSet.accessWidenersConfigurationName))
    }

    val finalMinecraftFile = accessWidenTask.flatMap(AccessWiden::outputFile)

    project.tasks.maybeRegister(
        lowerCamelCaseGradleName("decompile", target.featureName, namePart, "minecraft"),
        Decompile::class.java,
    ) {
        it.group = "sources"

        it.inputFile.set(finalMinecraftFile)
        it.classpath.from(project.configurations.named(sourceSet.compileClasspathConfigurationName))
        it.classpath.from(extraClasspathFiles)
    }

    return finalMinecraftFile
}

internal fun compilationSourceSet(target: MinecraftTargetInternal<*>, name: String, isSingleTarget: Boolean): SourceSet {
    val name = if (isSingleTarget) {
        name
    } else {
        sourceSetName(name, target)
    }

    return target.project.extension<SourceSetContainer>().maybeCreate(name)
}

private fun setupModTransformationPipeline(
    project: Project,
    target: MinecraftTargetInternal<*>,
    remapNamespace: Provider<String>,
    intermediateClasspath: FileCollection,
) {
    project.dependencies.registerTransform(ExtractIncludes::class.java) {
        it.from.attribute(ModTransformationStateAttribute.ATTRIBUTE, ModTransformationStateAttribute.INITIAL)
        it.to.attribute(
            ModTransformationStateAttribute.ATTRIBUTE,
            ModTransformationStateAttribute.of(target, States.INCLUDES_EXTRACTED)
        )
    }

    // afterEvaluate needed as the registration of a transform is dependent on a lazy provider
    //  this can potentially be changed to a no-op transform but that's far slower
    project.afterEvaluate {
        if (remapNamespace.get().isEmpty()) {
            return@afterEvaluate
        }

        project.dependencies.registerTransform(RemapAction::class.java) {
            it.from.attribute(
                ModTransformationStateAttribute.ATTRIBUTE,
                ModTransformationStateAttribute.of(target, States.INCLUDES_EXTRACTED)
            )

            it.to.attribute(
                ModTransformationStateAttribute.ATTRIBUTE,
                ModTransformationStateAttribute.of(target, States.REMAPPED)
            )

            it.parameters {
                it.mappings.set(target.loadMappingsTask.flatMap(LoadMappings::output))

                it.sourceNamespace.set(remapNamespace)

                it.extraClasspath.from(project.files(intermediateClasspath))

                it.cacheDirectory.set(getGlobalCacheDirectory(project))
            }
        }
    }
}

internal abstract class TargetCompilation
@Inject
constructor(
    private val name: String,
    override val target: MinecraftTargetInternal<*>,
    internal val intermediaryMinecraftClasspath: FileCollection,
    namedMinecraftFile: Provider<RegularFile>,
    extraClasspathFiles: FileCollection,
    private val variant: PublicationSide,
    side: Side,
    isSingleTarget: Boolean,
    remapNamespace: Provider<String>,
) : CompilationInternal() {
    final override val sourceSet: SourceSet = compilationSourceSet(target, name, isSingleTarget)

    val finalMinecraftFile: Provider<RegularFile> = registerCompilationTransformations(
        target,
        name,
        sourceSet,
        namedMinecraftFile,
        extraClasspathFiles,
    )

    init {
        project.dependencies.add(sourceSet.accessWidenersConfigurationName, accessWideners)
        project.dependencies.add(sourceSet.mixinsConfigurationName, mixins)

        setupModTransformationPipeline(project, target, remapNamespace, intermediaryMinecraftClasspath)

        val state = remapNamespace.map {
            if (it.isEmpty()) {
                ModTransformationStateAttribute.of(target, States.INCLUDES_EXTRACTED)
            } else {
                ModTransformationStateAttribute.of(target, States.REMAPPED)
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

        // Use detached configuration for idea compat
        val minecraftFiles = project.files(finalMinecraftFile) + extraClasspathFiles
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
