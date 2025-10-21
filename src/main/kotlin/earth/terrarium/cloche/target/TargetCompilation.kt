package earth.terrarium.cloche.target

import earth.terrarium.cloche.INCLUDE_TRANSFORMED_OUTPUT_ATTRIBUTE
import earth.terrarium.cloche.NoopAction
import earth.terrarium.cloche.PublicationSide
import earth.terrarium.cloche.REMAPPED_ATTRIBUTE
import earth.terrarium.cloche.api.attributes.CompilationAttributes
import earth.terrarium.cloche.api.attributes.IncludeTransformationStateAttribute
import earth.terrarium.cloche.cloche
import earth.terrarium.cloche.modId
import earth.terrarium.cloche.util.fromJars
import earth.terrarium.cloche.util.optionalDir
import earth.terrarium.cloche.api.attributes.RemapNamespaceAttribute
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
import net.msrandom.minecraftcodev.runs.task.GenerateModOutputs
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencyScopeConfiguration
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
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
        for (remapNamespace in target.mappings.remapNamespaces.get()) {
            val namespace =
                remapNamespace?.takeUnless { it == RemapNamespaceAttribute.INITIAL } ?: target.modRemapNamespace.get()

            if (namespace.isEmpty()) {
                project.dependencies.registerTransform(NoopAction::class.java) {
                    it.from.attribute(REMAPPED_ATTRIBUTE, false)
                    it.to.attribute(REMAPPED_ATTRIBUTE, true)

                    compilation.attributes(it.from)
                    compilation.attributes(it.to)
                }
                continue
            }

        project.dependencies.registerTransform(RemapAction::class.java) {
            it.from.attribute(REMAPPED_ATTRIBUTE, false)
            it.to.attribute(REMAPPED_ATTRIBUTE, true)

            it.from.attribute(RemapNamespaceAttribute.ATTRIBUTE, RemapNamespaceAttribute.INITIAL)
            it.to.attribute(RemapNamespaceAttribute.ATTRIBUTE, remapNamespace)

            compilation.attributes(it.from)
            compilation.attributes(it.to)

            it.parameters {
                val compileClasspath =
                    project.getUnmappedClasspath(compilation.sourceSet.compileClasspathConfigurationName)
                val runtimeClasspath =
                    project.getUnmappedClasspath(compilation.sourceSet.runtimeClasspathConfigurationName)
                val modCompileClasspath =
                    project.getUnmappedModFiles(compilation.sourceSet.compileClasspathConfigurationName)
                val modRuntimeClasspath =
                    project.getUnmappedModFiles(compilation.sourceSet.runtimeClasspathConfigurationName)

                    it.mappings.set(target.loadMappingsTask.flatMap(LoadMappings::output))

                    it.sourceNamespace.set(namespace)

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

    val generateModOutputs: TaskProvider<GenerateModOutputs> = project.tasks.register(
        lowerCamelCaseGradleName("generate", sourceSet.takeUnless(SourceSet::isMain)?.name, "modOutputs"),
        GenerateModOutputs::class.java,
    ) {
        it.modId.set(project.modId)

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

    val includeBucketConfiguration: NamedDomainObjectProvider<DependencyScopeConfiguration?> =
        project.configurations.dependencyScope(lowerCamelCaseGradleName(target.featureName, featureName, "include")) {
            it.addCollectedDependencies(dependencyHandler.include)
        }

    private val includeResolvableConfiguration =
        project.configurations.resolvable(
            lowerCamelCaseGradleName(
                target.featureName,
                featureName,
                "includeFiles"
            )
        ) { configuration ->
            configuration.extendsFrom(includeBucketConfiguration.get())

            attributes(configuration.attributes)

            configuration.attributes
                .attribute(INCLUDE_TRANSFORMED_OUTPUT_ATTRIBUTE, true)
                .attributeProvider(CompilationAttributes.SIDE, info.side)
                .attribute(CompilationAttributes.DATA, info.data)

            configuration.isTransitive = false
        }

    val remapJarTask: TaskProvider<RemapJar>? = if (!isTest) {
        project.tasks.register(
            lowerCamelCaseGradleName(sourceSet.takeUnless(SourceSet::isMain)?.name, "remapJar"),
            RemapJar::class.java
        ) {
            val jarFile = project.tasks.named(sourceSet.jarTaskName, Jar::class.java)
                .flatMap(Jar::getArchiveFile)

            it.destinationDirectory.set(project.cloche.intermediateOutputsDirectory)
            it.input.set(jarFile)
            it.sourceNamespace.set(MinecraftCodevRemapperPlugin.NAMED_MAPPINGS_NAMESPACE)
            it.targetNamespace.set(target.modRemapNamespace)
            it.classpath.from(sourceSet.compileClasspath)

            it.mappings.set(target.loadMappingsTask.flatMap(LoadMappings::output))

            it.manifest.fromJars(project.serviceOf(), jarFile)
        }
    } else {
        null
    }

    val includeJarTask: TaskProvider<out IncludesJar>? = if (!isTest) {
        project.tasks.register(
            lowerCamelCaseGradleName(sourceSet.takeUnless(SourceSet::isMain)?.name, "includeJar"),
            info.includeJarType,
        ) {
            it.destinationDirectory.set(project.cloche.finalOutputsDirectory)

            val jar = project.tasks.named(sourceSet.jarTaskName, Jar::class.java)
            val remapped = target.modRemapNamespace.map(String::isNotEmpty)

            val jarFile = remapped.flatMap {
                val jarTask = if (it) {
                    remapJarTask!!
                } else {
                    jar
                }

                jarTask.flatMap(Jar::getArchiveFile)
            }

            it.input.set(jarFile)
            it.manifest.fromJars(project.serviceOf(), jarFile)

            it.fromResolutionResults(includeResolvableConfiguration)
        }
    } else {
        null
    }

    init {
        setupModTransformationPipeline(project, target, this)

        project.configurations.named(sourceSet.compileClasspathConfigurationName) {
            it.attributes.attribute(REMAPPED_ATTRIBUTE, true)
            it.attributes.attribute(INCLUDE_TRANSFORMED_OUTPUT_ATTRIBUTE, false)
            it.attributes.attribute(IncludeTransformationStateAttribute.ATTRIBUTE, info.includeState)
            it.attributes.attribute(RemapNamespaceAttribute.ATTRIBUTE, RemapNamespaceAttribute.INITIAL)

            it.extendsFrom(target.mappingsBuildDependenciesHolder)
        }

        project.configurations.named(sourceSet.runtimeClasspathConfigurationName) {
            it.attributes.attribute(REMAPPED_ATTRIBUTE, true)
            it.attributes.attribute(INCLUDE_TRANSFORMED_OUTPUT_ATTRIBUTE, false)
            it.attributes.attribute(IncludeTransformationStateAttribute.ATTRIBUTE, info.includeState)
            it.attributes.attribute(RemapNamespaceAttribute.ATTRIBUTE, RemapNamespaceAttribute.INITIAL)

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
            .attributeProvider(CompilationAttributes.SIDE, info.side)
            .attribute(CompilationAttributes.DATA, info.data)
    }

    override fun resolvableAttributes(attributes: AttributeContainer) {
        super.resolvableAttributes(attributes)

        attributes.attribute(INCLUDE_TRANSFORMED_OUTPUT_ATTRIBUTE, false)
    }
}
