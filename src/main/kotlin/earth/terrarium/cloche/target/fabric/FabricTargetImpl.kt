package earth.terrarium.cloche.target.fabric

import earth.terrarium.cloche.ClochePlugin
import earth.terrarium.cloche.FABRIC
import earth.terrarium.cloche.INCLUDE_TRANSFORMED_OUTPUT_ATTRIBUTE
import earth.terrarium.cloche.PublicationSide
import earth.terrarium.cloche.api.attributes.CompilationAttributes
import earth.terrarium.cloche.api.attributes.IncludeTransformationStateAttribute
import earth.terrarium.cloche.api.metadata.FabricMetadata
import earth.terrarium.cloche.api.target.FabricTarget
import earth.terrarium.cloche.api.target.compilation.FabricIncludedClient
import earth.terrarium.cloche.api.target.isSingleTarget
import earth.terrarium.cloche.api.target.targetName
import earth.terrarium.cloche.cloche
import earth.terrarium.cloche.modId
import earth.terrarium.cloche.target.CompilationInternal
import earth.terrarium.cloche.target.LazyConfigurableInternal
import earth.terrarium.cloche.target.MinecraftTargetInternal
import earth.terrarium.cloche.target.TargetCompilation
import earth.terrarium.cloche.target.TargetCompilationInfo
import earth.terrarium.cloche.target.compilationSourceSet
import earth.terrarium.cloche.target.lazyConfigurable
import earth.terrarium.cloche.target.localImplementationConfigurationName
import earth.terrarium.cloche.target.registerCompilationTransformations
import earth.terrarium.cloche.tasks.data.FabricMod
import earth.terrarium.cloche.tasks.data.MetadataFileProvider
import earth.terrarium.cloche.util.fromJars
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import net.msrandom.minecraftcodev.accesswidener.AccessWiden
import net.msrandom.minecraftcodev.core.MinecraftCodevPlugin.Companion.json
import net.msrandom.minecraftcodev.core.MinecraftComponentMetadataRule
import net.msrandom.minecraftcodev.core.MinecraftOperatingSystemAttribute
import net.msrandom.minecraftcodev.core.VERSION_MANIFEST_URL
import net.msrandom.minecraftcodev.core.operatingSystemName
import net.msrandom.minecraftcodev.core.task.ResolveMinecraftClient
import net.msrandom.minecraftcodev.core.task.ResolveMinecraftCommon
import net.msrandom.minecraftcodev.core.utils.getGlobalCacheDirectory
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.core.utils.zipFileSystem
import net.msrandom.minecraftcodev.fabric.MinecraftCodevFabricPlugin
import net.msrandom.minecraftcodev.fabric.task.JarInJar
import net.msrandom.minecraftcodev.fabric.task.MergeAccessWideners
import net.msrandom.minecraftcodev.remapper.MinecraftCodevRemapperPlugin
import net.msrandom.minecraftcodev.remapper.task.LoadMappings
import net.msrandom.minecraftcodev.remapper.task.RemapTask
import net.msrandom.minecraftcodev.runs.task.WriteClasspathFile
import org.gradle.api.Action
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ResolvableConfiguration
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.internal.extensions.core.serviceOf
import org.gradle.process.CommandLineArgumentProvider
import java.io.File
import java.util.jar.JarFile
import javax.inject.Inject
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

@Suppress("UnstableApiUsage")
internal abstract class FabricTargetImpl @Inject constructor(name: String) :
    MinecraftTargetInternal(name),
    FabricTarget {
    internal val commonLibrariesConfiguration =
        project.configurations.create(lowerCamelCaseGradleName(featureName, "commonMinecraftLibraries")) {
            it.isCanBeConsumed = false

            it.attributes.attribute(
                MinecraftOperatingSystemAttribute.attribute,
                project.objects.named(
                    MinecraftOperatingSystemAttribute::class.java,
                    operatingSystemName(),
                ),
            )
        }

    internal val clientLibrariesConfiguration =
        project.configurations.create(lowerCamelCaseGradleName(featureName, "clientMinecraftLibraries")) {
            it.isCanBeConsumed = false

            it.attributes.attribute(
                MinecraftOperatingSystemAttribute.attribute,
                project.objects.named(
                    MinecraftOperatingSystemAttribute::class.java,
                    operatingSystemName(),
                ),
            )
        }

    internal val resolveCommonMinecraft =
        project.tasks.register(
            lowerCamelCaseGradleName("resolve", name, "common"),
            ResolveMinecraftCommon::class.java,
        ) {
            it.group = "minecraft-resolution"

            it.minecraftVersion.set(minecraftVersion)

            it.output.set(output("obf"))
        }

    internal val resolveClientMinecraft =
        project.tasks.register(
            lowerCamelCaseGradleName("resolve", name, "client"),
            ResolveMinecraftClient::class.java,
        ) {
            it.group = "minecraft-resolution"

            it.minecraftVersion.set(minecraftVersion)

            it.output.set(output("client-obf"))
        }

    internal val remapCommonMinecraftIntermediary =
        project.tasks.register(
            lowerCamelCaseGradleName(
                "remap",
                name,
                "commonMinecraft",
                MinecraftCodevFabricPlugin.INTERMEDIARY_MAPPINGS_NAMESPACE,
            ),
            RemapTask::class.java,
        ) {
            it.group = "minecraft-transforms"

            it.inputFile.set(resolveCommonMinecraft.flatMap(ResolveMinecraftCommon::output))
            it.sourceNamespace.set("obf")
            it.targetNamespace.set(minecraftRemapNamespace)

            it.classpath.from(commonLibrariesConfiguration)

            it.mappings.set(loadMappingsTask.flatMap(LoadMappings::output))

            it.outputFile.set(output(MinecraftCodevFabricPlugin.INTERMEDIARY_MAPPINGS_NAMESPACE))
        }

    internal val remapClientMinecraftIntermediary =
        project.tasks.register(
            lowerCamelCaseGradleName(
                "remap",
                name,
                "clientMinecraft",
                MinecraftCodevFabricPlugin.INTERMEDIARY_MAPPINGS_NAMESPACE,
            ),
            RemapTask::class.java,
        ) {
            it.group = "minecraft-transforms"

            it.inputFile.set(resolveClientMinecraft.flatMap(ResolveMinecraftClient::output))
            it.sourceNamespace.set("obf")
            it.targetNamespace.set(minecraftRemapNamespace)

            it.classpath.from(commonLibrariesConfiguration)
            it.classpath.from(clientLibrariesConfiguration)
            it.classpath.from(resolveCommonMinecraft.flatMap(ResolveMinecraftCommon::output))

            it.mappings.set(loadMappingsTask.flatMap(LoadMappings::output))

            it.outputFile.set(output("client-${MinecraftCodevFabricPlugin.INTERMEDIARY_MAPPINGS_NAMESPACE}"))
        }

    private val remapCommon = project.tasks.register(
        lowerCamelCaseGradleName("remap", featureName, "commonMinecraftNamed"),
        RemapTask::class.java
    ) {
        it.group = "minecraft-transforms"

        it.inputFile.set(remapCommonMinecraftIntermediary.flatMap(RemapTask::outputFile))

        it.classpath.from(commonLibrariesConfiguration)

        it.mappings.set(loadMappingsTask.flatMap(LoadMappings::output))

        it.sourceNamespace.set(minecraftRemapNamespace)

        it.outputFile.set(output(MinecraftCodevRemapperPlugin.NAMED_MAPPINGS_NAMESPACE))
    }

    private val remapClient = project.tasks.register(
        lowerCamelCaseGradleName("remap", featureName, "clientMinecraftNamed"),
        RemapTask::class.java
    ) {
        it.group = "minecraft-transforms"

        it.inputFile.set(remapClientMinecraftIntermediary.flatMap(RemapTask::outputFile))

        it.classpath.from(commonLibrariesConfiguration)
        it.classpath.from(clientLibrariesConfiguration)
        it.classpath.from(remapCommonMinecraftIntermediary.flatMap(RemapTask::outputFile))

        it.mappings.set(loadMappingsTask.flatMap(LoadMappings::output))

        it.sourceNamespace.set(minecraftRemapNamespace)

        it.outputFile.set(output("client-${MinecraftCodevRemapperPlugin.NAMED_MAPPINGS_NAMESPACE}"))
    }

    private val generateMappingsArtifact = project.tasks.register(
        lowerCamelCaseGradleName("generate", featureName, "mappingsArtifact"),
        Zip::class.java,
    ) {
        it.destinationDirectory.set(it.temporaryDir)
        it.archiveBaseName.set("$featureName-mappings")
        it.archiveVersion.set(null as String?)

        it.from(loadMappingsTask.flatMap(LoadMappings::output)) {
            it.into("mappings")
        }
    }

    val writeRemapClasspathTask: TaskProvider<WriteClasspathFile> = project.tasks.register(
        lowerCamelCaseGradleName("write", featureName, "remapClasspath"),
        WriteClasspathFile::class.java,
    ) {
        it.classpath.from(commonLibrariesConfiguration)
        it.classpath.from(clientLibrariesConfiguration)
        it.classpath.from(remapCommonMinecraftIntermediary.flatMap(RemapTask::outputFile))

        it.separator.set(File.pathSeparator)
    }

    val mergeJarTask: TaskProvider<Jar>
    val mergeIncludeJarTask: TaskProvider<JarInJar>

    override val finalJar: Provider<Jar>
        get() = client.isConfigured.flatMap {
            if (it) {
                mergeIncludeJarTask
            } else {
                main.includeJarTask!!
            }
        }

    final override val main: FabricCompilationImpl

    final override val client: LazyConfigurableInternal<FabricClientSecondarySourceSets> = project.lazyConfigurable {
        if (includedClient.isConfiguredValue) {
            throw InvalidUserCodeException("Used `client()` in $name after previously using `includedClient()`")
        }

        val sideProvider = project.provider {
            PublicationSide.Client
        }

        val client =
            project.objects.newInstance(
                FabricClientSecondarySourceSets::class.java,
                TargetCompilationInfo(
                    ClochePlugin.CLIENT_COMPILATION_NAME,
                    this,
                    project.files(
                        remapCommonMinecraftIntermediary.flatMap(RemapTask::outputFile),
                        remapClientMinecraftIntermediary.flatMap(RemapTask::outputFile),
                    ),
                    remapClient.flatMap(RemapTask::outputFile),
                    main.finalMinecraftFile.map(::listOf),
                    sideProvider,
                    data = false,
                    test = false,
                    includeState = IncludeTransformationStateAttribute.Stripped,
                    includeJarType = JarInJar::class.java,
                ),
            )

        clientLibrariesConfiguration.shouldResolveConsistentlyWith(project.configurations.getByName(client.sourceSet.runtimeClasspathConfigurationName))

        project.configurations.named(client.sourceSet.localImplementationConfigurationName) {
            it.extendsFrom(clientLibrariesConfiguration)
        }

        client
    }

    final override val includedClient: LazyConfigurableInternal<FabricIncludedClient> = project.lazyConfigurable {
        if (client.isConfiguredValue) {
            throw InvalidUserCodeException("Used 'includedClient' in target $name after already configuring client compilation")
        }

        clientLibrariesConfiguration.shouldResolveConsistentlyWith(project.configurations.getByName(sourceSet.runtimeClasspathConfigurationName))

        project.configurations.named(sourceSet.localImplementationConfigurationName) {
            it.extendsFrom(clientLibrariesConfiguration)
        }

        project.objects.newInstance(FabricIncludedClient::class.java)
    }

    final override val data: LazyConfigurableInternal<FabricCompilationImpl> = project.lazyConfigurable {
        registerCommonCompilation(ClochePlugin.DATA_COMPILATION_NAME)
    }

    final override val test: LazyConfigurableInternal<FabricCompilationImpl> = project.lazyConfigurable {
        registerCommonCompilation(SourceSet.TEST_SOURCE_SET_NAME)
    }

    override val metadata: FabricMetadata = project.objects.newInstance(FabricMetadata::class.java, this)

    protected abstract val providerFactory: ProviderFactory
        @Inject get

    final override val minecraftRemapNamespace: Provider<String>
        get() = providerFactory.provider { MinecraftCodevFabricPlugin.INTERMEDIARY_MAPPINGS_NAMESPACE }

    override val hasSeparateClient = client.isConfigured

    override val runs: FabricRunConfigurations = project.objects.newInstance(FabricRunConfigurations::class.java, this)

    override val commonType get() = FABRIC

    private val mainTargetMinecraftName = featureName ?: SourceSet.MAIN_SOURCE_SET_NAME
    private val clientTargetMinecraftName = lowerCamelCaseGradleName(featureName, "client")

    private val fabricLoader = loaderVersion.map {
        main.dependencyHandler.module("net.fabricmc", "fabric-loader", it)
    }

    init {
        val commonStub =
            project.dependencies.enforcedPlatform(ClochePlugin.STUB_DEPENDENCY) as ExternalModuleDependency

        val clientStub =
            project.dependencies.enforcedPlatform(ClochePlugin.STUB_DEPENDENCY) as ExternalModuleDependency

        project.dependencies.add(commonLibrariesConfiguration.name, commonStub.apply {
            capabilities {
                it.requireCapability("net.msrandom:$mainTargetMinecraftName")
            }
        })

        project.dependencies.add(clientLibrariesConfiguration.name, clientStub.apply {
            capabilities {
                it.requireCapability("net.msrandom:$clientTargetMinecraftName")
            }
        })

        main = registerCommonCompilation(SourceSet.MAIN_SOURCE_SET_NAME)

        val mergeIncludeResolvableConfiguration: NamedDomainObjectProvider<ResolvableConfiguration> =
            project.configurations.resolvable(
                lowerCamelCaseGradleName(featureName, "mergeIncludeFiles")
            ) { configuration ->
                configuration.extendsFrom(main.includeBucketConfiguration.get())

                client.onConfigured {
                    configuration.extendsFrom(it.includeBucketConfiguration.get())
                }

                attributes(configuration.attributes)

                configuration.attributes
                    .attribute(INCLUDE_TRANSFORMED_OUTPUT_ATTRIBUTE, true)
                    .attribute(CompilationAttributes.SIDE, PublicationSide.Joined)
                    .attribute(CompilationAttributes.DATA, false)

                configuration.isTransitive = false
            }

        project.dependencies.add(
            main.sourceSet.runtimeOnlyConfigurationName,
            project.files(generateMappingsArtifact.flatMap(Zip::getArchiveFile)),
        )

        mergeJarTask = project.tasks.register(
            lowerCamelCaseGradleName(sourceSet.takeUnless(SourceSet::isMain)?.name, "mergeJar"),
            Jar::class.java,
        ) {
            if (isSingleTarget) {
                it.archiveClassifier.set("merged")
            } else {
                it.archiveClassifier.set("$capabilitySuffix-merged")
            }

            it.destinationDirectory.set(project.cloche.intermediateOutputsDirectory)

            val mainJar = main.remapJarTask!!.flatMap(Jar::getArchiveFile)
            val clientJar =
                client.value.flatMap(TargetCompilation<FabricTargetImpl>::remapJarTask).flatMap(Jar::getArchiveFile)

            it.from(project.zipTree(mainJar)) {
                it.exclude(JarFile.MANIFEST_NAME)
            }

            it.from(project.zipTree(clientJar)) {
                it.exclude(JarFile.MANIFEST_NAME)
            }

            it.manifest.fromJars(project.serviceOf(), mainJar, clientJar)
        }

        mergeIncludeJarTask = project.tasks.register(
            lowerCamelCaseGradleName(target.targetName, "mergeIncludeJar"),
            JarInJar::class.java,
        ) {
            val jarFile = mergeJarTask.flatMap(Jar::getArchiveFile)

            if (!isSingleTarget) {
                it.archiveClassifier.set(capabilitySuffix)
            }

            it.destinationDirectory.set(project.cloche.finalOutputsDirectory)

            it.input.set(jarFile)
            it.manifest.fromJars(project.serviceOf(), jarFile)

            it.fromResolutionResults(mergeIncludeResolvableConfiguration)
        }

        commonLibrariesConfiguration.shouldResolveConsistentlyWith(project.configurations.getByName(sourceSet.runtimeClasspathConfigurationName))

        project.configurations.named(sourceSet.localImplementationConfigurationName) {
            it.extendsFrom(commonLibrariesConfiguration)
        }

        mappings.fabricIntermediary()

        registerMappings()

        // afterEvaluate needed because of the component rules using providers
        project.afterEvaluate { project ->
            project.dependencies.components { components ->
                components.withModule(
                    ClochePlugin.STUB_MODULE,
                    MinecraftComponentMetadataRule::class.java,
                ) {
                    it.params(
                        getGlobalCacheDirectory(project),
                        minecraftVersion.get(),
                        VERSION_MANIFEST_URL,
                        project.gradle.startParameter.isOffline,
                        mainTargetMinecraftName,
                        clientTargetMinecraftName,
                    )
                }
            }
        }

        main.dependencies { dependencies ->
            dependencies.implementation.add(fabricLoader)
        }
    }

    private fun output(suffix: String) = outputDirectory.zip(minecraftVersion) { dir, version ->
        dir.file("minecraft-$version-$suffix.jar")
    }

    private fun registerCommonCompilation(name: String): FabricCompilationImpl {
        fun <T> clientAlternative(normal: Provider<T>, client: Provider<T>) =
            includedClient.isConfigured.flatMap {
                if (it) {
                    client
                } else {
                    normal
                }
            }

        fun list(vararg values: Provider<RegularFile>) = project.objects.listProperty(RegularFile::class.java).apply {
            for (value in values) {
                add(value)
            }
        }

        val commonTask = registerCompilationTransformations(
            this,
            lowerCamelCaseGradleName(name.takeUnless(SourceSet.MAIN_SOURCE_SET_NAME::equals), "common"),
            compilationSourceSet(this, name),
            remapCommon.flatMap(RemapTask::outputFile),
            project.provider { emptyList() },
        ).first

        commonTask.configure {
            it.accessWideners.from(accessWideners)
        }

        val commonClasspath = list(commonTask.flatMap(AccessWiden::outputFile))

        // TODO Once client splitting is done, we might not always need the client Jar
        val intermediateClasspath = project.files(
            remapCommonMinecraftIntermediary.flatMap(RemapTask::outputFile),
            remapClientMinecraftIntermediary.flatMap(RemapTask::outputFile),
        )

        val remappedFile = clientAlternative(
            normal = remapCommon.flatMap(RemapTask::outputFile),
            client = remapClient.flatMap(RemapTask::outputFile),
        )

        val extraClasspath = if (name == SourceSet.MAIN_SOURCE_SET_NAME) {
            clientAlternative(
                normal = project.provider { emptyList() },
                client = commonClasspath,
            )
        } else {
            clientAlternative(
                normal = main.finalMinecraftFile.map(::listOf),
                client = commonClasspath.zip(main.finalMinecraftFile, List<RegularFile>::plus),
            )
        }

        val side = includedClient.isConfigured.map {
            if (it) {
                PublicationSide.Joined
            } else {
                PublicationSide.Common
            }
        }

        return project.objects.newInstance(
            FabricCompilationImpl::class.java,
            TargetCompilationInfo(
                name,
                this,
                intermediateClasspath,
                remappedFile,
                extraClasspath,
                side,
                name == ClochePlugin.DATA_COMPILATION_NAME,
                name == SourceSet.TEST_SOURCE_SET_NAME,
                IncludeTransformationStateAttribute.Stripped,
                JarInJar::class.java,
            ),
        )
    }

    override fun registerAccessWidenerMergeTask(compilation: CompilationInternal) {
        if (compilation.isTest) {
            return
        }

        val modId = project.modId

        val task = project.tasks.register(
            lowerCamelCaseGradleName("merge", name, compilation.featureName, "accessWideners"),
            MergeAccessWideners::class.java
        ) {
            it.input.from(accessWideners)
            it.accessWidenerName.set(modId)

            val output = modId.zip(project.layout.buildDirectory.dir("generated")) { modId, directory ->
                directory.dir("mergedAccessWideners").dir(compilation.sourceSet.name).file("$modId.accessWidener")
            }

            it.output.set(output)
        }

        project.tasks.named(compilation.sourceSet.jarTaskName, Jar::class.java) {
            it.from(task.flatMap(MergeAccessWideners::output))

            it.doLast {
                it as Jar

                zipFileSystem(it.archiveFile.get().asFile.toPath()).use {
                    val accessWidenerFileName = "${modId.get()}.accessWidener"
                    val accessWidenerPath = it.getPath(accessWidenerFileName)
                    val modJsonPath = it.getPath(MinecraftCodevFabricPlugin.MOD_JSON)

                    if (!accessWidenerPath.exists() || !modJsonPath.exists()) {
                        return@use
                    }

                    val metadata: FabricMod = modJsonPath.inputStream().use(json::decodeFromStream)

                    if (metadata.accessWidener != null) {
                        return@use
                    }

                    modJsonPath.outputStream().use {
                        json.encodeToStream(metadata.copy(accessWidener = accessWidenerFileName), it)
                    }
                }
            }
        }
    }

    override fun addJarInjects(compilation: CompilationInternal) {
        project.tasks.named(compilation.sourceSet.jarTaskName, Jar::class.java) {
            it.manifest {
                it.attributes["Fabric-Loom-Mixin-Remap-Type"] = "static"
            }
        }
    }

    override fun addAnnotationProcessors(compilation: CompilationInternal) {
        compilation.dependencyHandler.annotationProcessor.add(fabricLoader)
        compilation.dependencyHandler.annotationProcessor.add(project.files(generateMappingsArtifact.flatMap(Zip::getArchiveFile)))
        compilation.dependencyHandler.annotationProcessor.add(main.dependencyHandler.module("net.fabricmc", "fabric-mixin-compile-extensions", "0.6.0"))

        project.tasks.named(compilation.sourceSet.compileJavaTaskName, JavaCompile::class.java) {
            val inMapFile = lowerCamelCaseGradleName(
                "inMapFile",
                MinecraftCodevRemapperPlugin.NAMED_MAPPINGS_NAMESPACE,
                MinecraftCodevFabricPlugin.INTERMEDIARY_MAPPINGS_NAMESPACE
            )

            val inMapFileArgument = loadMappingsTask.flatMap(LoadMappings::output).map {
                "-A$inMapFile=${it}"
            }

            it.options.compilerArgumentProviders.add(CommandLineArgumentProvider {
                listOf(
                    inMapFileArgument.get(),
                    "-AdefaultObfuscationEnv=${MinecraftCodevRemapperPlugin.NAMED_MAPPINGS_NAMESPACE}:${MinecraftCodevFabricPlugin.INTERMEDIARY_MAPPINGS_NAMESPACE}",
                )
            })
        }
    }

    override fun onClientIncluded(action: () -> Unit) = includedClient.onConfigured {
        action()
    }

    override fun withMetadataJson(action: Action<MetadataFileProvider<JsonObject>>) = main.withMetadataJson(action)
}
