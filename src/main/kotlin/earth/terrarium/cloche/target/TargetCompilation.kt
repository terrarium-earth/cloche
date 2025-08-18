package earth.terrarium.cloche.target

import earth.terrarium.cloche.ClocheExtension
import earth.terrarium.cloche.PublicationSide
import earth.terrarium.cloche.REMAPPED_ATTRIBUTE
import earth.terrarium.cloche.api.attributes.CompilationAttributes
import earth.terrarium.cloche.api.attributes.IncludeTransformationStateAttribute
import net.msrandom.minecraftcodev.accesswidener.AccessWiden
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.getGlobalCacheDirectory
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.decompiler.task.Decompile
import net.msrandom.minecraftcodev.remapper.MinecraftCodevRemapperPlugin
import net.msrandom.minecraftcodev.remapper.RemapAction
import net.msrandom.minecraftcodev.remapper.task.LoadMappings
import net.msrandom.minecraftcodev.remapper.task.RemapJar
import net.msrandom.minecraftcodev.runs.task.GenerateModOutputs
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import javax.inject.Inject

private fun Project.getUnmappedModFiles(configurationName: String): FileCollection {
    val classpath = project.configurations.named(configurationName)

    val modDependencies = project.configurations.named(modConfigurationName(configurationName))

    return project.files(classpath.zip(modDependencies) { classpath, modDependencies ->
        val resolutionResult = modDependencies.incoming.resolutionResult

        val componentIdentifiers = resolutionResult.allComponents.map(ResolvedComponentResult::getId) - resolutionResult.root.id

        val filteredIdentifiers = componentIdentifiers.filter { it !is ProjectComponentIdentifier }

        classpath.incoming.artifactView {
            it.componentFilter(filteredIdentifiers::contains)

            it.attributes {
                it.attribute(REMAPPED_ATTRIBUTE, false)
            }
        }.files
    })
}

private fun Project.getUnmappedClasspath(configurationName: String): FileCollection {
    val classpath = project.configurations.named(configurationName)

    return project.files(classpath.map { classpath ->
        classpath.incoming.artifactView {
            it.attributes {
                it.attribute(REMAPPED_ATTRIBUTE, false)
            }
        }.files
    })
}

internal fun registerCompilationTransformations(
    target: MinecraftTargetInternal,
    compilationName: String,
    sourceSet: SourceSet,
    namedMinecraftFile: Provider<RegularFile>,
    extraClasspathFiles: Provider<List<RegularFile>>,
): Pair<TaskProvider<AccessWiden>, Provider<RegularFile>> {
    val outputDirectory = target.outputDirectory.map { it.dir(compilationName) }

    val collapsedName = compilationName.takeUnless(SourceSet.MAIN_SOURCE_SET_NAME::equals)

    val project = target.project

    val accessWidenTask = project.tasks.register(
        lowerCamelCaseGradleName(
            "accessWiden",
            target.featureName,
            collapsedName,
            "minecraft",
        ),
        AccessWiden::class.java,
    ) {
        it.group = "minecraft-transforms"

        it.inputFile.set(namedMinecraftFile)
        it.namespace.set(MinecraftCodevRemapperPlugin.NAMED_MAPPINGS_NAMESPACE)

        it.accessWideners.from(project.configurations.getByName(sourceSet.runtimeClasspathConfigurationName))

        it.outputFile.set(outputDirectory.zip(namedMinecraftFile) { dir, file ->
            dir.file(file.asFile.name)
        })
    }

    val finalMinecraftFile = accessWidenTask.flatMap(AccessWiden::outputFile)

    val decompile = project.tasks.register(
        lowerCamelCaseGradleName("decompile", target.featureName, collapsedName, "minecraft"),
        Decompile::class.java,
    ) {
        it.group = "sources"

        it.inputFile.set(finalMinecraftFile)
        it.classpath.from(project.configurations.named(sourceSet.compileClasspathConfigurationName))
        it.classpath.from(extraClasspathFiles)

        it.outputFile.set(outputDirectory.zip(namedMinecraftFile) { dir, file ->
            dir.file("${file.asFile.nameWithoutExtension}-sources.${file.asFile.extension}")
        })
    }

    return accessWidenTask to decompile.flatMap(Decompile::outputFile)
}

internal fun compilationSourceSet(target: MinecraftTargetInternal, name: String, isSingleTarget: Boolean): SourceSet {
    val sourceSet = target.project.extension<SourceSetContainer>().maybeCreate(sourceSetName(target, name, isSingleTarget))

    if (sourceSet.localRuntimeConfigurationName !in target.project.configurations.names) {
        target.project.configurations.dependencyScope(sourceSet.localRuntimeConfigurationName)
    }

    if (sourceSet.localImplementationConfigurationName !in target.project.configurations.names) {
        target.project.configurations.dependencyScope(sourceSet.localImplementationConfigurationName)
    }

    return sourceSet
}

private fun setupModTransformationPipeline(
    project: Project,
    target: MinecraftTargetInternal,
    compilation: TargetCompilation,
) {
    // afterEvaluate needed as the registration of a transform is dependent on a lazy provider
    //  this can potentially be changed to a no-op transform, but that's far slower
    project.afterEvaluate {
        if (target.modRemapNamespace.get().isEmpty()) {
            return@afterEvaluate
        }

        project.dependencies.registerTransform(RemapAction::class.java) {
            it.from.attribute(REMAPPED_ATTRIBUTE, false)
            it.to.attribute(REMAPPED_ATTRIBUTE, true)

            compilation.attributes(it.from)
            compilation.attributes(it.to)

            it.parameters {
                val compileClasspath = project.getUnmappedClasspath(compilation.sourceSet.compileClasspathConfigurationName)
                val runtimeClasspath = project.getUnmappedClasspath(compilation.sourceSet.runtimeClasspathConfigurationName)
                val modCompileClasspath = project.getUnmappedModFiles(compilation.sourceSet.compileClasspathConfigurationName)
                val modRuntimeClasspath = project.getUnmappedModFiles(compilation.sourceSet.runtimeClasspathConfigurationName)

                it.mappings.set(target.loadMappingsTask.flatMap(LoadMappings::output))

                it.sourceNamespace.set(target.modRemapNamespace.get())

                it.extraClasspath.from(compilation.info.intermediaryMinecraftClasspath)
                it.extraClasspath.from(compileClasspath)
                it.extraClasspath.from(runtimeClasspath)

                it.cacheDirectory.set(getGlobalCacheDirectory(project))

                it.modFiles.from(modCompileClasspath)
                it.modFiles.from(modRuntimeClasspath)
            }
        }
    }
}

private fun GenerateModOutputs.addSourceSet(sourceSet: SourceSet) {
    val rootDirectory = project.rootProject.layout.projectDirectory.asFile

    paths.addAll(project.provider {
        sourceSet.output.classesDirs.map {
            it.relativeTo(rootDirectory).path
        }
    })

    paths.add(sourceSet.output.resourcesDir!!.relativeTo(rootDirectory).path)
}

internal data class TargetCompilationInfo(
    val name: String,
    val target: MinecraftTargetInternal,
    val intermediaryMinecraftClasspath: FileCollection,
    val namedMinecraftFile: Provider<RegularFile>,
    val extraClasspathFiles: Provider<List<RegularFile>>,
    val variant: PublicationSide,
    val data: Boolean,
    val test: Boolean,
    val isSingleTarget: Boolean,
    val includeState: IncludeTransformationStateAttribute,
)

internal abstract class TargetCompilation @Inject constructor(val info: TargetCompilationInfo) : CompilationInternal() {
    override val target get() = info.target

    override val isTest get() = info.test

    final override val sourceSet: SourceSet = compilationSourceSet(target, info.name, info.isSingleTarget)

    private val setupFiles = registerCompilationTransformations(
        target,
        info.name,
        sourceSet,
        info.namedMinecraftFile,
        info.extraClasspathFiles,
    )

    val generateModOutputs: TaskProvider<GenerateModOutputs> = project.tasks.register(
        lowerCamelCaseGradleName("generate", sourceSet.takeUnless(SourceSet::isMain)?.name, "modOutputs"),
        GenerateModOutputs::class.java,
    ) {
        it.modId.set(project.extension<ClocheExtension>().metadata.modId)

        // TODO Make this logic a bit better;
        //   The way it should go is as follows:
        //     data - main + data
        //     client - main + client
        //     clientData - main + client + data + clientData
        if (name != SourceSet.MAIN_SOURCE_SET_NAME) {
            it.addSourceSet(target.main.sourceSet)
        }

        it.addSourceSet(sourceSet)
    }

    val finalMinecraftFile: Provider<RegularFile> = setupFiles.first.flatMap(AccessWiden::outputFile)
    val sources = setupFiles.second

    val remapJarTask: TaskProvider<RemapJar> = project.tasks.register(
        lowerCamelCaseGradleName(sourceSet.takeUnless(SourceSet::isMain)?.name, "remapJar"),
        RemapJar::class.java
    ) {
        it.destinationDirectory.set(project.extension<ClocheExtension>().intermediateOutputsDirectory)

        it.input.set(project.tasks.named(sourceSet.jarTaskName, Jar::class.java).flatMap(Jar::getArchiveFile))
        it.sourceNamespace.set(MinecraftCodevRemapperPlugin.NAMED_MAPPINGS_NAMESPACE)
        it.targetNamespace.set(target.modRemapNamespace)
        it.classpath.from(sourceSet.compileClasspath)

        it.mappings.set(target.loadMappingsTask.flatMap(LoadMappings::output))
    }

    init {
        setupModTransformationPipeline(project, target, this)

        val remapped = target.modRemapNamespace.map(String::isNotEmpty)

        project.configurations.named(sourceSet.compileClasspathConfigurationName) {
            it.attributes.attributeProvider(REMAPPED_ATTRIBUTE, remapped)
            it.attributes.attribute(IncludeTransformationStateAttribute.ATTRIBUTE, info.includeState)

            it.extendsFrom(target.mappingsBuildDependenciesHolder)
        }

        project.configurations.named(sourceSet.runtimeClasspathConfigurationName) {
            it.attributes.attributeProvider(REMAPPED_ATTRIBUTE, remapped)
            it.attributes.attribute(IncludeTransformationStateAttribute.ATTRIBUTE, info.includeState)

            it.extendsFrom(target.mappingsBuildDependenciesHolder)
        }

        setupFiles.first.configure {
            it.accessWideners.from(accessWideners)
        }

        // Use detached configuration for idea compat
        val minecraftFiles = project.files(finalMinecraftFile, info.extraClasspathFiles)
        val minecraftFileConfiguration =
            project.configurations.detachedConfiguration(project.dependencies.create(minecraftFiles))

        sourceSet.compileClasspath += minecraftFileConfiguration
        sourceSet.runtimeClasspath += minecraftFileConfiguration
    }

    override fun getName() = info.name

    override fun attributes(attributes: AttributeContainer) {
        super.attributes(attributes)

        target.attributes(attributes)

        attributes
            .attribute(CompilationAttributes.SIDE, info.variant)
            .attribute(CompilationAttributes.DATA, info.data)
    }
}
