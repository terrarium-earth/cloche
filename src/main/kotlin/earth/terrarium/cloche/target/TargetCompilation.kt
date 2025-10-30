package earth.terrarium.cloche.target

import earth.terrarium.cloche.CLOCHE_TARGET_ATTRIBUTE
import earth.terrarium.cloche.INCLUDE_TRANSFORMED_OUTPUT_ATTRIBUTE
import earth.terrarium.cloche.PublicationSide
import earth.terrarium.cloche.REMAPPED_ATTRIBUTE
import earth.terrarium.cloche.api.attributes.CompilationAttributes
import earth.terrarium.cloche.api.attributes.IncludeTransformationStateAttribute
import earth.terrarium.cloche.cloche
import earth.terrarium.cloche.util.fromJars
import earth.terrarium.cloche.util.optionalDir
import net.msrandom.minecraftcodev.accesswidener.AccessWiden
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.getGlobalCacheDirectory
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.decompiler.task.Decompile
import net.msrandom.minecraftcodev.includes.IncludesJar
import net.msrandom.minecraftcodev.remapper.MinecraftCodevRemapperPlugin
import net.msrandom.minecraftcodev.remapper.RemapAction
import net.msrandom.minecraftcodev.remapper.task.LoadMappings
import net.msrandom.minecraftcodev.remapper.task.RemapJar
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencyScopeConfiguration
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.internal.extensions.core.serviceOf
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.registerTransform
import javax.inject.Inject

private fun Project.getUnmappedModFiles(configurationName: String): FileCollection {
    val classpath = project.configurations.named(configurationName)

    val modDependencies = project.configurations.named(modConfigurationName(configurationName))

    return project.files(classpath.zip(modDependencies) { classpath, modDependencies ->
        val resolutionResult = modDependencies.incoming.resolutionResult

        val componentIdentifiers =
            resolutionResult.allComponents.map(ResolvedComponentResult::getId) - resolutionResult.root.id

        val filteredIdentifiers = componentIdentifiers.filter { it !is ProjectComponentIdentifier }

        classpath.incoming.artifactView {
            componentFilter(filteredIdentifiers::contains)

            attributes {
                attribute(REMAPPED_ATTRIBUTE, false)
            }
        }.files
    })
}

private fun Project.getUnmappedClasspath(configurationName: String): FileCollection {
    val classpath = project.configurations.named(configurationName)

    return project.files(classpath.map { classpath ->
        classpath.incoming.artifactView {
            componentFilter {
                it !is ProjectComponentIdentifier
            }

            attributes {
                attribute(REMAPPED_ATTRIBUTE, false)
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

    val accessWidenTask = project.tasks.register<AccessWiden>(
        lowerCamelCaseGradleName(
            "accessWiden",
            target.featureName,
            collapsedName,
            "minecraft",
        ),
    ) {
        group = "minecraft-transforms"

        inputFile.set(namedMinecraftFile)
        namespace.set(MinecraftCodevRemapperPlugin.NAMED_MAPPINGS_NAMESPACE)

        with(project) {
            // TODO Export access wideners as a separate artifact
            accessWideners.from(getRelevantSyncArtifacts(sourceSet.compileClasspathConfigurationName))
        }

        outputFile.set(outputDirectory.zip(namedMinecraftFile) { dir, file ->
            dir.file(file.asFile.name)
        })
    }

    val finalMinecraftFile = accessWidenTask.flatMap(AccessWiden::outputFile)

    val decompile = project.tasks.register<Decompile>(
        lowerCamelCaseGradleName("decompile", target.featureName, collapsedName, "minecraft"),
    ) {
        group = "sources"

        inputFile.set(finalMinecraftFile)
        classpath.from(project.configurations.named(sourceSet.compileClasspathConfigurationName))
        classpath.from(extraClasspathFiles)

        outputFile.set(outputDirectory.zip(namedMinecraftFile) { dir, file ->
            dir.file("${file.asFile.nameWithoutExtension}-sources.${file.asFile.extension}")
        })
    }

    return accessWidenTask to decompile.flatMap(Decompile::outputFile)
}

internal fun compilationSourceSet(target: MinecraftTargetInternal, name: String): SourceSet {
    val sourceSet =
        target.project.extension<SourceSetContainer>().maybeCreate(sourceSetName(target, name))

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
    compilation: TargetCompilation<*>,
) {
    // afterEvaluate needed as the registration of a transform is dependent on a lazy provider
    //  this can potentially be changed to a no-op transform, but that's far slower
    project.afterEvaluate {
        if (target.modRemapNamespace.get().isEmpty()) {
            return@afterEvaluate
        }

        project.dependencies.registerTransform(RemapAction::class) {
            from
                .attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
                .attribute(REMAPPED_ATTRIBUTE, false)
                .attribute(CLOCHE_TARGET_ATTRIBUTE, target.name)

            to
                .attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
                .attribute(REMAPPED_ATTRIBUTE, true)
                .attribute(CLOCHE_TARGET_ATTRIBUTE, target.name)

            compilation.attributes(from)
            compilation.attributes(to)

            parameters {
                val compileClasspath =
                    project.getUnmappedClasspath(compilation.sourceSet.compileClasspathConfigurationName)

                val runtimeClasspath =
                    project.getUnmappedClasspath(compilation.sourceSet.runtimeClasspathConfigurationName)

                val modCompileClasspath =
                    project.getUnmappedModFiles(compilation.sourceSet.compileClasspathConfigurationName)

                val modRuntimeClasspath =
                    project.getUnmappedModFiles(compilation.sourceSet.runtimeClasspathConfigurationName)

                mappings.set(target.loadMappingsTask.flatMap(LoadMappings::output))

                sourceNamespace.set(target.modRemapNamespace.get())

                extraClasspath.from(compilation.info.intermediaryMinecraftClasspath)
                extraClasspath.from(compileClasspath)
                extraClasspath.from(runtimeClasspath)

                cacheDirectory.set(getGlobalCacheDirectory(project))

                modFiles.from(modCompileClasspath)
                modFiles.from(modRuntimeClasspath)
            }
        }
    }
}

internal data class TargetCompilationInfo<T : MinecraftTargetInternal>(
    val name: String,
    val target: T,
    val intermediaryMinecraftClasspath: FileCollection,
    val namedMinecraftFile: Provider<RegularFile>,
    val extraClasspathFiles: Provider<List<RegularFile>>,
    val side: Provider<PublicationSide>,
    val data: Boolean,
    val test: Boolean,
    val includeState: IncludeTransformationStateAttribute,
    val includeJarType: Class<out IncludesJar>,
)

@Suppress("UnstableApiUsage")
internal abstract class TargetCompilation<T : MinecraftTargetInternal> @Inject constructor(val info: TargetCompilationInfo<T>) : CompilationInternal() {
    override val target get() = info.target

    override val isTest get() = info.test

    final override val sourceSet: SourceSet = compilationSourceSet(target, info.name)

    private val setupFiles = registerCompilationTransformations(
        target,
        info.name,
        sourceSet,
        info.namedMinecraftFile,
        info.extraClasspathFiles,
    )

    val metadataDirectory: Provider<Directory>
        @Internal
        get() = project.layout.buildDirectory.dir("generated").map { it.dir("metadata").optionalDir(target.featureName).dir(namePath) }

    val finalMinecraftFile: Provider<RegularFile> = setupFiles.first.flatMap(AccessWiden::outputFile)
    val sources = setupFiles.second

    val includeBucketConfiguration: NamedDomainObjectProvider<DependencyScopeConfiguration> =
        project.configurations.dependencyScope(lowerCamelCaseGradleName(target.featureName, featureName, "include")) {
            addCollectedDependencies(dependencyHandler.include)
        }

    private val includeResolvableConfiguration =
        project.configurations.resolvable(
            lowerCamelCaseGradleName(
                target.featureName,
                featureName,
                "includeFiles",
            )
        ) {
            extendsFrom(includeBucketConfiguration.get())

            this@TargetCompilation.attributes(attributes)

            attributes
                .attribute(INCLUDE_TRANSFORMED_OUTPUT_ATTRIBUTE, true)
                .attributeProvider(CompilationAttributes.SIDE, info.side)
                .attribute(CompilationAttributes.DATA, info.data)

            isTransitive = false
        }

    val remapJarTask: TaskProvider<RemapJar>? = if (!isTest) {
        project.tasks.register<RemapJar>(
            lowerCamelCaseGradleName(sourceSet.takeUnless(SourceSet::isMain)?.name, "remapJar"),
        ) {
            val jarFile = project.tasks.named<Jar>(sourceSet.jarTaskName).flatMap(Jar::getArchiveFile)

            destinationDirectory.set(project.cloche.intermediateOutputsDirectory)
            input.set(jarFile)
            sourceNamespace.set(MinecraftCodevRemapperPlugin.NAMED_MAPPINGS_NAMESPACE)
            targetNamespace.set(target.modRemapNamespace)
            classpath.from(sourceSet.compileClasspath)

            mappings.set(target.loadMappingsTask.flatMap(LoadMappings::output))

            manifest.fromJars(project.serviceOf(), jarFile)
        }
    } else {
        null
    }

    val includeJarTask: TaskProvider<out IncludesJar>? = if (!isTest) {
        project.tasks.register(
            lowerCamelCaseGradleName(sourceSet.takeUnless(SourceSet::isMain)?.name, "includeJar"),
            info.includeJarType,
        ) {
            destinationDirectory.set(project.cloche.finalOutputsDirectory)

            val jar = project.tasks.named<Jar>(sourceSet.jarTaskName)
            val remapped = target.modRemapNamespace.map(String::isNotEmpty)

            val jarFile = remapped.flatMap {
                val jarTask = if (it) {
                    remapJarTask!!
                } else {
                    jar
                }

                jarTask.flatMap(Jar::getArchiveFile)
            }

            input.set(jarFile)
            manifest.fromJars(project.serviceOf(), jarFile)

            fromResolutionResults(includeResolvableConfiguration)
        }
    } else {
        null
    }

    init {
        setupModTransformationPipeline(project, target, this)

        val remapped = target.modRemapNamespace.map(String::isNotEmpty)

        project.configurations.named(sourceSet.compileClasspathConfigurationName) {
            attributes
                .attributeProvider(REMAPPED_ATTRIBUTE, remapped)
                .attribute(INCLUDE_TRANSFORMED_OUTPUT_ATTRIBUTE, false)
                .attribute(IncludeTransformationStateAttribute.ATTRIBUTE, info.includeState)
                .attribute(CLOCHE_TARGET_ATTRIBUTE, target.name)

            extendsFrom(target.mappingsBuildDependenciesHolder)
        }

        project.configurations.named(sourceSet.runtimeClasspathConfigurationName) {
            attributes
                .attributeProvider(REMAPPED_ATTRIBUTE, remapped)
                .attribute(INCLUDE_TRANSFORMED_OUTPUT_ATTRIBUTE, false)
                .attribute(IncludeTransformationStateAttribute.ATTRIBUTE, info.includeState)
                .attribute(CLOCHE_TARGET_ATTRIBUTE, target.name)

            extendsFrom(target.mappingsBuildDependenciesHolder)
        }

        setupFiles.first.configure {
            accessWideners.from(this@TargetCompilation.accessWideners)
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
            .attributeProvider(CompilationAttributes.SIDE, info.side)
            .attribute(CompilationAttributes.DATA, info.data)
    }

    override fun resolvableAttributes(attributes: AttributeContainer) {
        super.resolvableAttributes(attributes)

        attributes
            .attribute(INCLUDE_TRANSFORMED_OUTPUT_ATTRIBUTE, false)
            .attribute(CLOCHE_TARGET_ATTRIBUTE, target.name)
    }
}
