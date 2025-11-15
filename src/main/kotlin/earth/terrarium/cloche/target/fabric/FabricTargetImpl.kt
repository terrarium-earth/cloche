package earth.terrarium.cloche.target.fabric

import earth.terrarium.cloche.ClochePlugin
import earth.terrarium.cloche.api.attributes.ModDistribution
import earth.terrarium.cloche.api.attributes.IncludeTransformationStateAttribute
import earth.terrarium.cloche.api.metadata.FabricMetadata
import earth.terrarium.cloche.api.target.FabricTarget
import earth.terrarium.cloche.api.target.compilation.FabricIncludedClient
import earth.terrarium.cloche.modId
import earth.terrarium.cloche.target.CompilationInternal
import earth.terrarium.cloche.target.LazyConfigurableInternal
import earth.terrarium.cloche.target.MinecraftTargetInternal
import earth.terrarium.cloche.target.TargetCompilationInfo
import earth.terrarium.cloche.target.compilationSourceSet
import earth.terrarium.cloche.target.lazyConfigurable
import earth.terrarium.cloche.target.localImplementationConfigurationName
import earth.terrarium.cloche.target.registerCompilationTransformations
import earth.terrarium.cloche.tasks.data.MetadataFileProvider
import earth.terrarium.cloche.util.fromJars
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
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
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.internal.extensions.core.serviceOf
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withModule
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
    private val commonLibrariesConfiguration =
        project.configurations.create(lowerCamelCaseGradleName(featureName, "commonMinecraftLibraries")) {
            isCanBeConsumed = false

            attributes.attribute(
                MinecraftOperatingSystemAttribute.attribute,
                project.objects.named(operatingSystemName()),
            )
        }

    private val clientLibrariesConfiguration =
        project.configurations.create(lowerCamelCaseGradleName(featureName, "clientMinecraftLibraries")) {
            isCanBeConsumed = false

            attributes.attribute(
                MinecraftOperatingSystemAttribute.attribute,
                project.objects.named(operatingSystemName()),
            )
        }

    private val resolveCommonMinecraft =
        project.tasks.register<ResolveMinecraftCommon>(
            lowerCamelCaseGradleName("resolve", name, "common"),
        ) {
            group = "minecraft-resolution"

            minecraftVersion.set(this@FabricTargetImpl.minecraftVersion)

            output.set(output("obf"))
        }

    private val resolveClientMinecraft =
        project.tasks.register<ResolveMinecraftClient>(
            lowerCamelCaseGradleName("resolve", name, "client"),
        ) {
            group = "minecraft-resolution"

            minecraftVersion.set(this@FabricTargetImpl.minecraftVersion)

            output.set(output("client-obf"))
        }

    private val remapCommonMinecraftIntermediary =
        project.tasks.register<RemapTask>(
            lowerCamelCaseGradleName(
                "remap",
                name,
                "commonMinecraft",
                MinecraftCodevFabricPlugin.INTERMEDIARY_MAPPINGS_NAMESPACE,
            ),
        ) {
            group = "minecraft-transforms"

            inputFile.set(resolveCommonMinecraft.flatMap(ResolveMinecraftCommon::output))
            sourceNamespace.set("obf")
            targetNamespace.set(minecraftRemapNamespace)

            classpath.from(commonLibrariesConfiguration)

            mappings.set(loadMappingsTask.flatMap(LoadMappings::output))

            outputFile.set(output(MinecraftCodevFabricPlugin.INTERMEDIARY_MAPPINGS_NAMESPACE))
        }

    private val remapClientMinecraftIntermediary =
        project.tasks.register<RemapTask>(
            lowerCamelCaseGradleName(
                "remap",
                name,
                "clientMinecraft",
                MinecraftCodevFabricPlugin.INTERMEDIARY_MAPPINGS_NAMESPACE,
            ),
        ) {
            group = "minecraft-transforms"

            inputFile.set(resolveClientMinecraft.flatMap(ResolveMinecraftClient::output))
            sourceNamespace.set("obf")
            targetNamespace.set(minecraftRemapNamespace)

            classpath.from(commonLibrariesConfiguration)
            classpath.from(clientLibrariesConfiguration)
            classpath.from(resolveCommonMinecraft.flatMap(ResolveMinecraftCommon::output))

            mappings.set(loadMappingsTask.flatMap(LoadMappings::output))

            outputFile.set(output("client-${MinecraftCodevFabricPlugin.INTERMEDIARY_MAPPINGS_NAMESPACE}"))
        }

    private val remapCommon = project.tasks.register<RemapTask>(
        lowerCamelCaseGradleName("remap", featureName, "commonMinecraftNamed"),
    ) {
        group = "minecraft-transforms"

        inputFile.set(remapCommonMinecraftIntermediary.flatMap(RemapTask::outputFile))

        classpath.from(commonLibrariesConfiguration)

        mappings.set(loadMappingsTask.flatMap(LoadMappings::output))

        sourceNamespace.set(minecraftRemapNamespace)

        outputFile.set(output(MinecraftCodevRemapperPlugin.NAMED_MAPPINGS_NAMESPACE))
    }

    private val remapClient = project.tasks.register<RemapTask>(
        lowerCamelCaseGradleName("remap", featureName, "clientMinecraftNamed"),
    ) {
        group = "minecraft-transforms"

        inputFile.set(remapClientMinecraftIntermediary.flatMap(RemapTask::outputFile))

        classpath.from(commonLibrariesConfiguration)
        classpath.from(clientLibrariesConfiguration)
        classpath.from(remapCommonMinecraftIntermediary.flatMap(RemapTask::outputFile))

        mappings.set(loadMappingsTask.flatMap(LoadMappings::output))

        sourceNamespace.set(minecraftRemapNamespace)

        outputFile.set(output("client-${MinecraftCodevRemapperPlugin.NAMED_MAPPINGS_NAMESPACE}"))
    }

    private val generateMappingsArtifact = project.tasks.register<Zip>(
        lowerCamelCaseGradleName("generate", featureName, "mappingsArtifact"),
    ) {
        destinationDirectory.set(temporaryDir)
        archiveBaseName.set("$featureName-mappings")
        archiveVersion.set(null as String?)

        from(loadMappingsTask.flatMap(LoadMappings::output)) {
            into("mappings")
        }
    }

    val writeRemapClasspathTask: TaskProvider<WriteClasspathFile> = project.tasks.register<WriteClasspathFile>(
        lowerCamelCaseGradleName("write", featureName, "remapClasspath"),
    ) {
        classpath.from(commonLibrariesConfiguration)
        classpath.from(clientLibrariesConfiguration)
        classpath.from(remapCommonMinecraftIntermediary.flatMap(RemapTask::outputFile))

        separator.set(File.pathSeparator)
    }

    override val finalJar: Provider<out Jar>
        get() = client.value.flatMap {
            it.includeJarTask!!
        }.orElse(main.includeJarTask!!)

    final override val main: FabricCompilationImpl

    final override val client: LazyConfigurableInternal<FabricClientSecondarySourceSets> = project.lazyConfigurable {
        if (includedClient.isConfiguredValue) {
            throw InvalidUserCodeException("Used `client()` in $name after previously using `includedClient()`")
        }

        val sideProvider = project.provider {
            ModDistribution.client
        }

        val client =
            project.objects.newInstance<FabricClientSecondarySourceSets>(
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
            extendsFrom(clientLibrariesConfiguration)
        }

        val mainJarTask = project.tasks.named<Jar>(main.sourceSet.jarTaskName)

        project.tasks.named<Jar>(client.sourceSet.jarTaskName) {
            val mainJarFile = mainJarTask.flatMap(Jar::getArchiveFile)

            from(project.zipTree(mainJarFile)) {
                exclude(JarFile.MANIFEST_NAME)
            }

            manifest.fromJars(project.serviceOf(), mainJarFile)
        }

        client
    }

    // TODO Use FabricIncludedClient::mixins
    final override val includedClient: LazyConfigurableInternal<FabricIncludedClient> = project.lazyConfigurable {
        if (client.isConfiguredValue) {
            throw InvalidUserCodeException("Used 'includedClient' in target $name after already configuring client compilation")
        }

        clientLibrariesConfiguration.shouldResolveConsistentlyWith(project.configurations.getByName(sourceSet.runtimeClasspathConfigurationName))

        project.configurations.named(sourceSet.localImplementationConfigurationName) {
            extendsFrom(clientLibrariesConfiguration)
        }

        project.objects.newInstance<FabricIncludedClient>()
    }

    final override val data: LazyConfigurableInternal<FabricCompilationImpl> = project.lazyConfigurable {
        registerCommonCompilation(ClochePlugin.DATA_COMPILATION_NAME)
    }

    final override val test: LazyConfigurableInternal<FabricCompilationImpl> = project.lazyConfigurable {
        registerCommonCompilation(SourceSet.TEST_SOURCE_SET_NAME)
    }

    override val metadata = project.objects.newInstance<FabricMetadata>(this)

    protected abstract val providerFactory: ProviderFactory
        @Inject get

    final override val minecraftRemapNamespace: Provider<String>
        get() = providerFactory.provider { MinecraftCodevFabricPlugin.INTERMEDIARY_MAPPINGS_NAMESPACE }

    override val hasSeparateClient = client.isConfigured

    override val runs = project.objects.newInstance<FabricRunConfigurations>(this)

    private val mainTargetMinecraftName = featureName ?: SourceSet.MAIN_SOURCE_SET_NAME
    private val clientTargetMinecraftName = lowerCamelCaseGradleName(featureName, "client")

    private val fabricLoader = loaderVersion.map {
        project.dependencyFactory.create("net.fabricmc", "fabric-loader", it)
    }

    init {
        val commonStub =
            project.dependencies.enforcedPlatform(ClochePlugin.STUB_DEPENDENCY) as ExternalModuleDependency

        val clientStub =
            project.dependencies.enforcedPlatform(ClochePlugin.STUB_DEPENDENCY) as ExternalModuleDependency

        project.dependencies.add(commonLibrariesConfiguration.name, commonStub.apply {
            capabilities {
                requireCapability("net.msrandom:$mainTargetMinecraftName")
            }
        })

        project.dependencies.add(clientLibrariesConfiguration.name, clientStub.apply {
            capabilities {
                requireCapability("net.msrandom:$clientTargetMinecraftName")
            }
        })

        main = registerCommonCompilation(SourceSet.MAIN_SOURCE_SET_NAME)

        project.dependencies.add(
            main.sourceSet.runtimeOnlyConfigurationName,
            project.files(generateMappingsArtifact.flatMap(Zip::getArchiveFile)),
        )

        commonLibrariesConfiguration.shouldResolveConsistentlyWith(project.configurations.getByName(sourceSet.runtimeClasspathConfigurationName))

        project.configurations.named(sourceSet.localImplementationConfigurationName) {
            extendsFrom(commonLibrariesConfiguration)
        }

        mappings.fabricIntermediary()

        registerMappings()

        // afterEvaluate needed because of the component rules using providers
        project.afterEvaluate {
            dependencies.components {
                withModule(
                    ClochePlugin.STUB_MODULE,
                    MinecraftComponentMetadataRule::class,
                ) {
                    params(
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

        main.dependencies {
            implementation.add(fabricLoader)
        }
    }

    private fun output(suffix: String) = outputDirectory.zip(minecraftVersion) { dir, version ->
        dir.file("minecraft-$version-$suffix.jar")
    }

    private fun registerCommonCompilation(name: String): FabricCompilationImpl {
        fun <T : Any> clientAlternative(normal: Provider<T>, client: Provider<T>) =
            includedClient.isConfigured.flatMap {
                if (it) {
                    client
                } else {
                    normal
                }
            }

        fun list(vararg values: Provider<RegularFile>) = project.objects.listProperty<RegularFile>().apply {
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
            accessWideners.from(this@FabricTargetImpl.accessWideners)
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
                ModDistribution.client
            } else {
                ModDistribution.common
            }
        }

        return project.objects.newInstance<FabricCompilationImpl>(
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

        val task = project.tasks.register<MergeAccessWideners>(
            lowerCamelCaseGradleName("merge", name, compilation.featureName, "accessWideners"),
        ) {
            input.from(accessWideners)
            accessWidenerName.set(modId)

            val output = modId.zip(project.layout.buildDirectory.dir("generated")) { modId, directory ->
                directory.dir("mergedAccessWideners").dir(compilation.sourceSet.name).file("$modId.accessWidener")
            }

            this.output.set(output)
        }

        project.tasks.named<Jar>(compilation.sourceSet.jarTaskName) {
            from(task.flatMap(MergeAccessWideners::output))

            doLast {
                this as Jar

                zipFileSystem(archiveFile.get().asFile.toPath()).use {
                    val accessWidenerFileName = "${modId.get()}.accessWidener"
                    val accessWidenerPath = it.getPath(accessWidenerFileName)
                    val modJsonPath = it.getPath(MinecraftCodevFabricPlugin.MOD_JSON)

                    if (!accessWidenerPath.exists() || !modJsonPath.exists()) {
                        return@use
                    }

                    val metadata: JsonObject = modJsonPath.inputStream().use(json::decodeFromStream)

                    if (metadata["accessWidener"] != null) {
                        return@use
                    }

                    val newMetadata = buildJsonObject {
                        for ((key, value) in metadata) {
                            if (key != "accessWidener") {
                                put(key, value)
                            } else {
                                put(key, JsonPrimitive(accessWidenerFileName))
                            }
                        }
                    }

                    modJsonPath.outputStream().use {
                        json.encodeToStream(newMetadata, it)
                    }
                }
            }
        }
    }

    override fun addJarInjects(compilation: CompilationInternal) {
        project.tasks.named<Jar>(compilation.sourceSet.jarTaskName) {
            manifest {
                attributes["Fabric-Loom-Mixin-Remap-Type"] = "static"
            }
        }
    }

    override fun addAnnotationProcessors(compilation: CompilationInternal) {
        compilation.dependencyHandler.annotationProcessor.add(fabricLoader)
        compilation.dependencyHandler.annotationProcessor.add(project.files(generateMappingsArtifact.flatMap(Zip::getArchiveFile)))
        compilation.dependencyHandler.annotationProcessor.add(project.dependencyFactory.create("net.fabricmc", "fabric-mixin-compile-extensions", "0.6.0"))

        project.tasks.named<JavaCompile>(compilation.sourceSet.compileJavaTaskName) {
            val inMapFile = lowerCamelCaseGradleName(
                "inMapFile",
                MinecraftCodevRemapperPlugin.NAMED_MAPPINGS_NAMESPACE,
                MinecraftCodevFabricPlugin.INTERMEDIARY_MAPPINGS_NAMESPACE
            )

            val inMapFileArgument = loadMappingsTask.flatMap(LoadMappings::output).map {
                "-A$inMapFile=${it}"
            }

            options.compilerArgumentProviders.add(CommandLineArgumentProvider {
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
