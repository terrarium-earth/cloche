package earth.terrarium.cloche.target.forge

import earth.terrarium.cloche.ClocheExtension
import earth.terrarium.cloche.ClochePlugin
import earth.terrarium.cloche.DATA_ATTRIBUTE
import earth.terrarium.cloche.FORGE
import earth.terrarium.cloche.IncludeTransformationState
import earth.terrarium.cloche.PublicationSide
import earth.terrarium.cloche.SIDE_ATTRIBUTE
import earth.terrarium.cloche.TRANSFORMED_OUTPUT_ATTRIBUTE
import earth.terrarium.cloche.api.metadata.ForgeMetadata
import earth.terrarium.cloche.api.metadata.ModMetadata
import earth.terrarium.cloche.api.target.ForgeLikeTarget
import earth.terrarium.cloche.target.CompilationInternal
import earth.terrarium.cloche.target.LazyConfigurableInternal
import earth.terrarium.cloche.target.MinecraftTargetInternal
import earth.terrarium.cloche.target.TargetCompilation
import earth.terrarium.cloche.target.TargetCompilationInfo
import earth.terrarium.cloche.target.addCollectedDependencies
import earth.terrarium.cloche.target.forge.lex.ForgeTargetImpl
import earth.terrarium.cloche.target.lazyConfigurable
import earth.terrarium.cloche.target.localImplementationConfigurationName
import earth.terrarium.cloche.tasks.GenerateForgeModsToml
import net.msrandom.minecraftcodev.core.MinecraftOperatingSystemAttribute
import net.msrandom.minecraftcodev.core.operatingSystemName
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.forge.patchesConfigurationName
import net.msrandom.minecraftcodev.forge.task.GenerateAccessTransformer
import net.msrandom.minecraftcodev.forge.task.JarJar
import net.msrandom.minecraftcodev.forge.task.ResolvePatchedMinecraft
import net.msrandom.minecraftcodev.remapper.MinecraftCodevRemapperPlugin
import net.msrandom.minecraftcodev.remapper.mappingsConfigurationName
import net.msrandom.minecraftcodev.remapper.task.LoadMappings
import net.msrandom.minecraftcodev.remapper.task.RemapTask
import net.msrandom.minecraftcodev.runs.task.WriteClasspathFile
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.attributes.Usage
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import javax.inject.Inject

@Suppress("UnstableApiUsage")
internal abstract class ForgeLikeTargetImpl @Inject constructor(name: String) :
    MinecraftTargetInternal(name), ForgeLikeTarget {
    protected val minecraftLibrariesConfiguration: Configuration =
        project.configurations.create(lowerCamelCaseGradleName(featureName, "minecraftLibraries")) {
            it.isCanBeConsumed = false

            it.attributes.attribute(
                MinecraftOperatingSystemAttribute.attribute,
                project.objects.named(
                    MinecraftOperatingSystemAttribute::class.java,
                    operatingSystemName(),
                ),
            )

            it.attributes.attribute(
                Usage.USAGE_ATTRIBUTE,
                project.objects.named(
                    Usage::class.java,
                    Usage.JAVA_RUNTIME,
                )
            )
        }

    val dataIncludeConfiguration: NamedDomainObjectProvider<Configuration> =
        project.configurations.register(lowerCamelCaseGradleName(target.featureName, "dataInclude")) {
            it.extendsFrom(includeConfiguration.get())

            it.addCollectedDependencies(dataInclude)

            attributes(it.attributes)

            it.attributes
                .attribute(TRANSFORMED_OUTPUT_ATTRIBUTE, true)
                .attribute(SIDE_ATTRIBUTE, PublicationSide.Joined)
                .attribute(DATA_ATTRIBUTE, true)

            it.isCanBeConsumed = false
            it.isTransitive = false
        }

    private val legacyClasspathConfiguration = project.configurations.register(lowerCamelCaseGradleName(target.featureName, "legacyClasspath")) {
        it.addCollectedDependencies(legacyClasspath)

        attributes(it.attributes)

        it.attributes
            .attribute(SIDE_ATTRIBUTE, PublicationSide.Joined)
            .attribute(DATA_ATTRIBUTE, false)

        it.isCanBeConsumed = false
    }

    private val dataLegacyClasspathConfiguration = project.configurations.register(lowerCamelCaseGradleName(target.featureName, "dataLegacyClasspath")) {
        it.extendsFrom(legacyClasspathConfiguration.get())

        it.addCollectedDependencies(dataLegacyClasspath)

        attributes(it.attributes)

        it.attributes
            .attribute(SIDE_ATTRIBUTE, PublicationSide.Joined)
            .attribute(DATA_ATTRIBUTE, true)

        it.isCanBeConsumed = false
    }

    private val testLegacyClasspathConfiguration = project.configurations.register(lowerCamelCaseGradleName(target.featureName, "testLegacyClasspath")) {
        it.extendsFrom(legacyClasspathConfiguration.get())

        it.addCollectedDependencies(testLegacyClasspath)

        attributes(it.attributes)

        it.attributes
            .attribute(SIDE_ATTRIBUTE, PublicationSide.Joined)
            .attribute(DATA_ATTRIBUTE, false)

        it.isCanBeConsumed = false
    }

    private val universal = project.configurations.create(lowerCamelCaseGradleName(featureName, "forgeUniversal")) {
        it.isCanBeConsumed = false
    }

    protected val resolvePatchedMinecraft: TaskProvider<ResolvePatchedMinecraft> = project.tasks.register(
        lowerCamelCaseGradleName("resolve", featureName, "patchedMinecraft"),
        ResolvePatchedMinecraft::class.java
    ) {
        it.group = "minecraft-resolution"

        it.minecraftVersion.set(minecraftVersion)
        it.universal.from(universal)

        it.output.set(output(minecraftRemapNamespace.map {
            if (it.isEmpty()) {
                MinecraftCodevRemapperPlugin.NAMED_MAPPINGS_NAMESPACE
            } else {
                it
            }
        }))
    }

    internal val writeLegacyClasspath = project.tasks.register(
        lowerCamelCaseGradleName("write", featureName, "legacyClasspath"),
        WriteClasspathFile::class.java,
    ) { task ->
        configureLegacyClasspath(task, main, legacyClasspathConfiguration)
    }

    internal val writeLegacyDataClasspath: TaskProvider<WriteClasspathFile> = project.tasks.register(
        lowerCamelCaseGradleName("write", featureName, "dataLegacyClasspath"),
        WriteClasspathFile::class.java,
    ) { task ->
        data.onConfigured { data ->
            configureLegacyClasspath(task, data, dataLegacyClasspathConfiguration)
        }
    }

    internal val writeLegacyTestClasspath: TaskProvider<WriteClasspathFile> = project.tasks.register(
        lowerCamelCaseGradleName("write", featureName, "testLegacyClasspath"),
        WriteClasspathFile::class.java,
    ) { task ->
        test.onConfigured { test ->
            configureLegacyClasspath(task, test, testLegacyClasspathConfiguration)
        }
    }

    final override lateinit var main: TargetCompilation

    final override val data: LazyConfigurableInternal<TargetCompilation> = project.lazyConfigurable {
        val data = run {
            project.objects.newInstance(
                TargetCompilation::class.java,
                TargetCompilationInfo(
                    ClochePlugin.DATA_COMPILATION_NAME,
                    this,
                    project.files(resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::output)),
                    minecraftFile,
                    project.provider { emptyList<RegularFile>() },
                    PublicationSide.Joined,
                    data = true,
                    test = false,
                    isSingleTarget = isSingleTarget,
                    includeState = IncludeTransformationState.None,
                ),
            )
        }

        data.dependencies { dependencies ->
            dependencies.runtimeOnly.add(project.files(resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::clientExtra)))
        }

        data
    }

    final override val test: LazyConfigurableInternal<TargetCompilation> = project.lazyConfigurable {
        val data = run {
            project.objects.newInstance(
                TargetCompilation::class.java,
                TargetCompilationInfo(
                    SourceSet.TEST_SOURCE_SET_NAME,
                    this,
                    project.files(resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::output)),
                    minecraftFile,
                    project.provider { emptyList<RegularFile>() },
                    PublicationSide.Joined,
                    data = false,
                    test = true,
                    isSingleTarget = isSingleTarget,
                    includeState = IncludeTransformationState.None,
                ),
            )
        }

        data.dependencies { dependencies ->
            dependencies.runtimeOnly.add(project.files(resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::clientExtra)))
        }

        data
    }

    override lateinit var includeJarTask: TaskProvider<JarJar>
    lateinit var dataIncludeJarTask: TaskProvider<JarJar>

    protected abstract val providerFactory: ProviderFactory
        @Inject get

    override val runs: ForgeRunConfigurations<out ForgeLikeTargetImpl> =
        project.objects.newInstance(ForgeRunConfigurations::class.java, this)

    abstract val group: String
    abstract val artifact: String

    override val commonType get() = FORGE

    override val metadata: ForgeMetadata = project.objects.newInstance(ForgeMetadata::class.java)

    private val remapTask = project.tasks.register(
        lowerCamelCaseGradleName("remap", name, "minecraftNamed"),
        RemapTask::class.java,
    ) {
        it.group = "minecraft-transforms"

        it.inputFile.set(resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::output))

        it.classpath.from(minecraftLibrariesConfiguration)

        it.mappings.set(loadMappingsTask.flatMap(LoadMappings::output))

        it.sourceNamespace.set(minecraftRemapNamespace)

        it.outputFile.set(output(project.provider { MinecraftCodevRemapperPlugin.NAMED_MAPPINGS_NAMESPACE }))
    }

    protected val generateModsToml: TaskProvider<GenerateForgeModsToml> = project.tasks.register(
        lowerCamelCaseGradleName("generate", featureName, "modsToml"),
        GenerateForgeModsToml::class.java
    ) {
        it.output.set(metadataDirectory.map {
            it.dir("META-INF").file("mods.toml")
        })

        it.commonMetadata.set(project.extension<ClocheExtension>().metadata)
        it.targetMetadata.set(metadata)

        it.loaderName.set(loaderName)
    }

    private val minecraftFile = minecraftRemapNamespace.flatMap {
        if (it.isEmpty()) {
            resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::output)
        } else {
            remapTask.flatMap(RemapTask::outputFile)
        }
    }

    private var isSingleTarget = false

    init {
        project.dependencies.add(minecraftLibrariesConfiguration.name, forgeDependency {
            capabilities {
                it.requireFeature("dependencies")
            }
        })

        project.dependencies.add(universal.name, forgeDependency {})
    }

    private fun configureLegacyClasspath(task: WriteClasspathFile, compilation: TargetCompilation, configuration: Provider<Configuration>) {
        task.classpath.from(minecraftLibrariesConfiguration)
        task.classpath.from(resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::clientExtra))
        task.classpath.from(configuration)

        if (this is ForgeTargetImpl) {
            task.classpath.from(compilation.finalMinecraftFile)
        }
    }

    private fun output(suffix: Provider<String>) = suffix.flatMap { suffix ->
        outputDirectory.zip(minecraftVersion.zip(loaderVersion) { a, b -> "$a-$b" }) { dir, version ->
            dir.file("$loaderName-$version-$suffix.jar")
        }
    }

    protected fun loaderVersionRange(version: String): ModMetadata.VersionRange =
        objectFactory.newInstance(ModMetadata.VersionRange::class.java).apply {
            start.set(version)
        }

    private fun forgeDependency(configure: ExternalModuleDependency.() -> Unit): Provider<ExternalModuleDependency> =
        minecraftVersion.flatMap { minecraftVersion ->
            loaderVersion.map { forgeVersion ->
                module(group, artifact, null).apply {
                    version { version ->
                        version.strictly(version(minecraftVersion, forgeVersion))
                    }

                    configure()
                }
            }
        }

    override fun initialize(isSingleTarget: Boolean) {
        this.isSingleTarget = isSingleTarget

        main = project.objects.newInstance(
            TargetCompilation::class.java,
            TargetCompilationInfo(
                SourceSet.MAIN_SOURCE_SET_NAME,
                this,
                project.files(resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::output)),
                minecraftFile,
                project.provider { emptyList() },
                PublicationSide.Joined,
                data = false,
                test = false,
                isSingleTarget = isSingleTarget,
                includeState = IncludeTransformationState.None,
            ),
        )

        legacyClasspathConfiguration.configure {
            it.shouldResolveConsistentlyWith(project.configurations.getByName(sourceSet.runtimeClasspathConfigurationName))
        }

        dataLegacyClasspathConfiguration.configure {
            data.onConfigured { data ->
                it.shouldResolveConsistentlyWith(project.configurations.getByName(data.sourceSet.runtimeClasspathConfigurationName))
            }
        }

        testLegacyClasspathConfiguration.configure {
            test.onConfigured { test ->
                it.shouldResolveConsistentlyWith(project.configurations.getByName(test.sourceSet.runtimeClasspathConfigurationName))
            }
        }

        includeJarTask = project.tasks.register(
            lowerCamelCaseGradleName(sourceSet.takeUnless(SourceSet::isMain)?.name, "jarJar"),
            JarJar::class.java,
        ) {
            val jar = project.tasks.named(sourceSet.jarTaskName, Jar::class.java)
            val remapJar = main.remapJarTask

            if (!isSingleTarget) {
                it.archiveClassifier.set(classifierName)
            }

            it.destinationDirectory.set(project.extension<ClocheExtension>().finalOutputsDirectory)

            it.input.set(modRemapNamespace.flatMap {
                val jarTask = if (it.isEmpty()) {
                    jar
                } else {
                    remapJar
                }

                jarTask.flatMap(Jar::getArchiveFile)
            })

            it.fromResolutionResults(includeConfiguration)
        }

        dataIncludeJarTask = project.tasks.register(
            lowerCamelCaseGradleName(sourceSet.takeUnless(SourceSet::isMain)?.name, "dataJarJar"),
            JarJar::class.java,
        ) {
            val jar = data.value.flatMap {
                project.tasks.named(it.sourceSet.jarTaskName, Jar::class.java)
            }

            val remapJar = data.value.flatMap(TargetCompilation::remapJarTask)

            if (!isSingleTarget) {
                it.archiveClassifier.set(classifierName)
            }

            it.destinationDirectory.set(project.extension<ClocheExtension>().finalOutputsDirectory)

            it.input.set(modRemapNamespace.flatMap {
                val jarTask = if (it.isEmpty()) {
                    jar
                } else {
                    remapJar
                }

                jarTask.flatMap(Jar::getArchiveFile)
            })

            it.fromResolutionResults(dataIncludeConfiguration)
        }

        sourceSet.resources.srcDir(metadataDirectory)

        project.tasks.named(sourceSet.processResourcesTaskName) {
            it.dependsOn(generateModsToml)
        }

        data.onConfigured {
            project.tasks.named(it.sourceSet.processResourcesTaskName) {
                it.dependsOn(generateModsToml)
            }
        }

        minecraftLibrariesConfiguration.shouldResolveConsistentlyWith(project.configurations.getByName(sourceSet.runtimeClasspathConfigurationName))

        project.configurations.named(sourceSet.localImplementationConfigurationName) {
            it.extendsFrom(minecraftLibrariesConfiguration)
        }

        project.dependencies.add(
            sourceSet.runtimeOnlyConfigurationName,
            project.files(resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::clientExtra)),
        )

        val userdev = forgeDependency {
            capabilities {
                it.requireFeature("moddev-bundle")
            }
        }

        project.dependencies.addProvider(sourceSet.patchesConfigurationName, userdev)

        resolvePatchedMinecraft.configure {
            it.patches.from(project.configurations.named(sourceSet.patchesConfigurationName))
            it.libraries.from(minecraftLibrariesConfiguration)
        }

        project.dependencies.addProvider(sourceSet.mappingsConfigurationName, userdev)

        registerMappings()
    }

    protected abstract fun version(minecraftVersion: String, loaderVersion: String): String

    override fun registerAccessWidenerMergeTask(compilation: CompilationInternal) {
        if (compilation.isTest) {
            return
        }

        val task = project.tasks.register(
            lowerCamelCaseGradleName("generate", name, compilation.featureName, "accessTransformer"),
            GenerateAccessTransformer::class.java
        ) {
            it.input.from(accessWideners)

            val output = project.layout.buildDirectory.dir("generated")
                .map { directory ->
                    directory.dir("accessTransformers").dir(compilation.sourceSet.name).file("accesstransformer.cfg")
                }

            it.output.set(output)
        }

        project.tasks.named(compilation.sourceSet.jarTaskName, Jar::class.java) {
            it.from(task.flatMap(GenerateAccessTransformer::output)) {
                it.into("META-INF")
            }
        }
    }

    override fun addAnnotationProcessors(compilation: CompilationInternal) {
        project.configurations.named(compilation.sourceSet.annotationProcessorConfigurationName) {
            it.extendsFrom(minecraftLibrariesConfiguration)
        }

        // TODO Add forge mixin arguments
    }

    override fun addJarInjects(compilation: CompilationInternal) {}

    override fun onClientIncluded(action: () -> Unit) {
        action()
    }
}
