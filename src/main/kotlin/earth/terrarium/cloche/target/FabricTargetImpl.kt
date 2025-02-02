package earth.terrarium.cloche.target

import earth.terrarium.cloche.*
import earth.terrarium.cloche.ClochePlugin.Companion.STUB_DEPENDENCY
import earth.terrarium.cloche.metadata.FabricMetadata
import earth.terrarium.cloche.metadata.ForgeMetadata
import earth.terrarium.cloche.tasks.GenerateFabricModJson
import net.msrandom.minecraftcodev.accesswidener.accessWidenersConfigurationName
import net.msrandom.minecraftcodev.core.MinecraftComponentMetadataRule
import net.msrandom.minecraftcodev.core.VERSION_MANIFEST_URL
import net.msrandom.minecraftcodev.core.task.ResolveMinecraftClient
import net.msrandom.minecraftcodev.core.task.ResolveMinecraftCommon
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.getGlobalCacheDirectory
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.fabric.MinecraftCodevFabricPlugin
import net.msrandom.minecraftcodev.fabric.runs.FabricRunsDefaultsContainer
import net.msrandom.minecraftcodev.fabric.task.MergeAccessWideners
import net.msrandom.minecraftcodev.mixins.mixinsConfigurationName
import net.msrandom.minecraftcodev.remapper.mappingsConfigurationName
import net.msrandom.minecraftcodev.remapper.task.LoadMappings
import net.msrandom.minecraftcodev.remapper.task.RemapTask
import net.msrandom.minecraftcodev.runs.downloadAssetsTaskName
import net.msrandom.minecraftcodev.runs.extractNativesTaskName
import net.msrandom.minecraftcodev.runs.task.DownloadAssets
import net.msrandom.minecraftcodev.runs.task.ExtractNatives
import org.gradle.api.Action
import org.gradle.api.DomainObjectCollection
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.nativeplatform.OperatingSystemFamily
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.spongepowered.asm.mixin.MixinEnvironment.Side
import javax.inject.Inject

internal abstract class FabricTargetImpl @Inject constructor(private val name: String) : MinecraftTargetInternal<FabricMetadata>,
    FabricTarget {
    private val commonLibrariesConfiguration =
        project.configurations.create(lowerCamelCaseGradleName(featureName, "commonMinecraftLibraries")) {
            it.isCanBeConsumed = false

            it.attributes.attribute(
                OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE,
                project.objects.named(
                    OperatingSystemFamily::class.java,
                    DefaultNativePlatform.host().operatingSystem.toFamilyName()
                ),
            )
        }

    private val clientLibrariesConfiguration =
        project.configurations.create(lowerCamelCaseGradleName(featureName, "clientMinecraftLibraries")) {
            it.isCanBeConsumed = false

            it.attributes.attribute(
                OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE,
                project.objects.named(
                    OperatingSystemFamily::class.java,
                    DefaultNativePlatform.host().operatingSystem.toFamilyName()
                ),
            )
        }

    override val loadMappings: TaskProvider<LoadMappings> =
        project.tasks.register(lowerCamelCaseGradleName("load", name, "mappings"), LoadMappings::class.java) {
            it.mappings.from(project.configurations.named(main.sourceSet.mappingsConfigurationName))

            it.javaExecutable.set(project.javaExecutableFor(minecraftVersion, it.cacheParameters))
        }

    override val mappingsBuildDependenciesHolder: Configuration =
        project.configurations.detachedConfiguration(project.dependencies.create(project.files().builtBy(loadMappings)))

    private val resolveCommonMinecraft =
        project.tasks.register(
            lowerCamelCaseGradleName("resolve", name, "common"),
            ResolveMinecraftCommon::class.java
        ) {
            it.group = "minecraft-resolution"

            it.version.set(minecraftVersion)
        }

    private val resolveClientMinecraft =
        project.tasks.register(
            lowerCamelCaseGradleName("resolve", name, "client"),
            ResolveMinecraftClient::class.java
        ) {
            it.group = "minecraft-resolution"

            it.version.set(minecraftVersion)
        }

    private val remapCommonMinecraftIntermediary =
        project.tasks.register(
            lowerCamelCaseGradleName(
                "remap",
                name,
                "commonMinecraft",
                MinecraftCodevFabricPlugin.INTERMEDIARY_MAPPINGS_NAMESPACE,
            ),
            RemapTask::class.java,
        ) {
            it.group = "minecraft-resolution"

            it.inputFile.set(resolveCommonMinecraft.flatMap(ResolveMinecraftCommon::output))
            it.sourceNamespace.set("obf")
            it.targetNamespace.set(remapNamespace)

            it.classpath.from(commonLibrariesConfiguration)

            it.mappings.set(loadMappings.flatMap(LoadMappings::output))
        }

    private val remapClientMinecraftIntermediary =
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
            it.targetNamespace.set(remapNamespace)

            it.classpath.from(commonLibrariesConfiguration)
            it.classpath.from(clientLibrariesConfiguration)
            it.classpath.from(resolveCommonMinecraft.flatMap(ResolveMinecraftCommon::output))

            it.mappings.set(loadMappings.flatMap(LoadMappings::output))
        }

    private val remapCommon = project.tasks.register(
        lowerCamelCaseGradleName("remap", featureName, "commonMinecraftNamed"),
        RemapTask::class.java
    ) {
        it.group = "minecraft-transforms"

        it.inputFile.set(remapCommonMinecraftIntermediary.flatMap(RemapTask::outputFile))

        it.classpath.from(commonLibrariesConfiguration)

        it.mappings.set(loadMappings.flatMap(LoadMappings::output))

        it.sourceNamespace.set(remapNamespace)
    }

    private val remapClient = project.tasks.register(
        lowerCamelCaseGradleName("remap", featureName, "clientMinecraftNamed"),
        RemapTask::class.java
    ) {
        it.group = "minecraft-transforms"

        it.inputFile.set(remapClientMinecraftIntermediary.flatMap(RemapTask::outputFile))

        it.classpath.from(commonLibrariesConfiguration)
        it.classpath.from(clientLibrariesConfiguration)
        it.classpath.from(remapClientMinecraftIntermediary.flatMap(RemapTask::outputFile))

        it.mappings.set(loadMappings.flatMap(LoadMappings::output))

        it.sourceNamespace.set(remapNamespace)
    }

    private val generateModJson = project.tasks.register(lowerCamelCaseGradleName("generate", featureName, "ModJson"), GenerateFabricModJson::class.java) {
        it.loaderDependencyVersion.set(loaderVersion.map {
            it.substringBefore('.')
        })

        it.output.set(metadataDirectory.map {
            it.file("fabric.mod.json")
        })

        it.commonMetadata.set(project.extension<ClocheExtension>().metadata)
        it.targetMetadata.set(metadata)
        it.mixinConfigs.from(project.configurations.named(main.sourceSet.mixinsConfigurationName))
    }

    final override lateinit var main: TargetCompilation
    final override var client: RunnableInternal? = null
    final override var data: RunnableTargetCompilation? = null

    override val dependsOn: DomainObjectCollection<CommonTarget> =
        project.objects.domainObjectSet(CommonTarget::class.java)

    override val metadata: FabricMetadata = project.objects.newInstance(FabricMetadata::class.java)

    protected abstract val providerFactory: ProviderFactory
        @Inject get

    final override val remapNamespace: Provider<String>
        get() = providerFactory.provider { MinecraftCodevFabricPlugin.INTERMEDIARY_MAPPINGS_NAMESPACE }

    private var hasMappings = project.objects.property(Boolean::class.java).apply {
        convention(false)
    }

    override val loaderName get() = FABRIC
    override val commonType get() = FABRIC

    override val compilations: DomainObjectCollection<TargetCompilation> =
        project.objects.domainObjectSet(TargetCompilation::class.java)

    override val runnables: DomainObjectCollection<RunnableInternal> =
        project.objects.domainObjectSet(RunnableInternal::class.java)

    abstract val mappingProviders: ListProperty<MappingDependencyProvider>
        @Internal get

    private val clientTargetMinecraftName = lowerCamelCaseGradleName(featureName, "client")

    private var isSingleTarget = false

    init {
        val commonStub = project.dependencies.enforcedPlatform(STUB_DEPENDENCY) as ExternalModuleDependency
        val clientStub = project.dependencies.enforcedPlatform(STUB_DEPENDENCY) as ExternalModuleDependency

        project.dependencies.add(commonLibrariesConfiguration.name, commonStub.apply {
            capabilities {
                it.requireCapability("net.msrandom:$featureName")
            }
        })

        project.dependencies.add(clientLibrariesConfiguration.name, clientStub.apply {
            capabilities {
                it.requireCapability("net.msrandom:$clientTargetMinecraftName")
            }
        })
    }

    override fun initialize(isSingleTarget: Boolean) {
        this.isSingleTarget = isSingleTarget

        fun <T> clientAlternative(normal: Provider<T>, client: Provider<T>) =
            project.provider { hasIncludedClient }.flatMap {
                if (it) {
                    client
                } else {
                    normal
                }
            }

        val mainIntermediaryFile = clientAlternative(
            normal = remapCommonMinecraftIntermediary.flatMap(RemapTask::outputFile),
            client = remapClientMinecraftIntermediary.flatMap(RemapTask::outputFile),
        )

        val mainRemappedFile = clientAlternative(
            normal = remapCommon.flatMap(RemapTask::outputFile),
            client = remapClient.flatMap(RemapTask::outputFile),
        )

        main =
            project.objects.newInstance(
                TargetCompilation::class.java,
                SourceSet.MAIN_SOURCE_SET_NAME,
                this,
                mainIntermediaryFile,
                mainRemappedFile,
                project.files(),
                PublicationVariant.Common,
                Side.SERVER,
                isSingleTarget,
                remapNamespace,
            )

        project.tasks.named(main.sourceSet.processResourcesTaskName, ProcessResources::class.java) {
            it.from(metadataDirectory)

            it.dependsOn(generateModJson)
        }

        compilations.add(main)

        main.dependencies { dependencies ->
            commonLibrariesConfiguration.shouldResolveConsistentlyWith(project.configurations.getByName(dependencies.sourceSet.runtimeClasspathConfigurationName))

            project.configurations.named(dependencies.sourceSet.implementationConfigurationName) {
                it.extendsFrom(commonLibrariesConfiguration)
            }

            dependencies.implementation(loaderVersion.map { version -> "net.fabricmc:fabric-loader:$version" })

            project.dependencies.addProvider(
                dependencies.sourceSet.mappingsConfigurationName,
                minecraftVersion.map { "net.fabricmc:${MinecraftCodevFabricPlugin.INTERMEDIARY_MAPPINGS_NAMESPACE}:$it:v2" },
            )

            // afterEvaluate needed because of the component rules using providers
            project.afterEvaluate { project ->
                project.dependencies.components { components ->
                    components.withModule(ClochePlugin.STUB_MODULE, MinecraftComponentMetadataRule::class.java) {
                        it.params(
                            getGlobalCacheDirectory(project),
                            listOf(minecraftVersion.get()),
                            VERSION_MANIFEST_URL,
                            project.gradle.startParameter.isOffline,
                            featureName,
                            clientTargetMinecraftName,
                        )
                    }
                }
            }

            mappingProviders.addAll(hasMappings.map {
                if (it) {
                    emptyList()
                } else {
                    buildList { MappingsBuilder(project, this).official() }
                }
            })

            project.configurations.named(dependencies.sourceSet.mappingsConfigurationName) {
                val mappingDependencyList = minecraftVersion.flatMap { minecraftVersion ->
                    mappingProviders.map { providers ->
                        providers.map { mapping ->
                            mapping(minecraftVersion, featureName)
                        }
                    }
                }

                it.dependencies.addAllLater(mappingDependencyList)
            }
        }
    }

    override fun getName() = name

    override fun registerAccessWidenerMergeTask(compilation: CompilationInternal) {
        val namePart = compilation.name.takeUnless { it == SourceSet.MAIN_SOURCE_SET_NAME }

        val modId = project.extension<ClocheExtension>().metadata.modId

        val task = project.tasks.register(
            lowerCamelCaseGradleName("merge", name, namePart, "accessWideners"),
            MergeAccessWideners::class.java
        ) {
            it.input.from(project.configurations.named(sourceSet.accessWidenersConfigurationName))

            val output = modId.zip(project.layout.buildDirectory.dir("generated"), ::Pair)
                .map { (modId, directory) ->
                    directory.dir("mergedAccessWideners").dir(compilation.sourceSet.name).file("$modId.accessWidener")
                }

            it.output.set(output)
        }

        project.tasks.named(compilation.sourceSet.jarTaskName, Jar::class.java) {
            it.from(task.flatMap(MergeAccessWideners::output))
        }
    }

    override fun createData(): RunnableTargetCompilation {
        val data =
            project.objects.newInstance(
                RunnableTargetCompilation::class.java,
                ClochePlugin.DATA_COMPILATION_NAME,
                this,
                remapCommonMinecraftIntermediary.flatMap(RemapTask::outputFile),
                remapCommon.flatMap(RemapTask::outputFile),
                project.files(),
                PublicationVariant.Data,
                Side.SERVER,
                isSingleTarget,
                remapNamespace,
            )

        data.dependencies { dependencies ->
            project.tasks.named(dependencies.sourceSet.downloadAssetsTaskName, DownloadAssets::class.java) {
                it.version.set(minecraftVersion)
            }
        }

        data.runConfiguration { datagen ->
            datagen.sourceSet(data.sourceSet)

            datagen.defaults {
                it.extension<FabricRunsDefaultsContainer>().data(minecraftVersion) { datagen ->
                    datagen.modId.set(project.extension<ClocheExtension>().metadata.modId)

                    datagen.outputDirectory.set(datagenDirectory)
                }
            }
        }

        return data
    }

    override fun server(action: Action<Runnable>?) {
        if (server == null) {
            val server = project.objects.newInstance(SimpleRunnable::class.java, ClochePlugin.SERVER_RUNNABLE_NAME)

            server.runConfiguration {
                it.sourceSet(main.sourceSet)
                it.defaults {
                    it.extension<FabricRunsDefaultsContainer>().server()
                }
            }

            runnables.add(server)

            this.server = server
        }

        action?.execute(server!!)
    }

    override fun includedClient(action: Action<Runnable>?) {
        if (client == null) {
            val client = project.objects.newInstance(SimpleRunnable::class.java, ClochePlugin.CLIENT_COMPILATION_NAME)

            client.runConfiguration {
                it.sourceSet(main.sourceSet)
                it.defaults {
                    it.extension<FabricRunsDefaultsContainer>().server()
                }
            }

            runnables.add(client)

            main.dependencies { dependencies ->
                clientLibrariesConfiguration.shouldResolveConsistentlyWith(
                    project.configurations.getByName(
                        dependencies.sourceSet.runtimeClasspathConfigurationName
                    )
                )

                project.configurations.named(dependencies.sourceSet.compileClasspathConfigurationName) {
                    it.extendsFrom(clientLibrariesConfiguration)
                }

                project.configurations.named(dependencies.sourceSet.runtimeClasspathConfigurationName) {
                    it.extendsFrom(clientLibrariesConfiguration)
                }

                project.tasks.named(dependencies.sourceSet.downloadAssetsTaskName, DownloadAssets::class.java) {
                    it.version.set(minecraftVersion)
                }

                project.tasks.named(dependencies.sourceSet.extractNativesTaskName, ExtractNatives::class.java) {
                    it.version.set(minecraftVersion)
                }
            }

            client.runConfiguration {
                it.sourceSet(main.sourceSet)

                it.defaults {
                    it.extension<FabricRunsDefaultsContainer>().client(minecraftVersion)
                }
            }

            this.client = client

            this.runnables.add(client)
        } else if (client is RunnableCompilation) {
            throw InvalidUserCodeException("Used 'includedClient' in target $name after already configuring client compilation")
        }
    }

    override fun client(action: Action<RunnableCompilation>?) {
        if (client == null) {
            val client =
                project.objects.newInstance(
                    RunnableTargetCompilation::class.java,
                    ClochePlugin.CLIENT_COMPILATION_NAME,
                    this,
                    remapCommonMinecraftIntermediary.flatMap(RemapTask::outputFile),
                    remapClient.flatMap(RemapTask::outputFile),
                    project.files(main.finalMinecraftFile),
                    PublicationVariant.Client,
                    Side.CLIENT,
                    isSingleTarget,
                    remapNamespace,
                )

            client.dependencies { dependencies ->
                clientLibrariesConfiguration.shouldResolveConsistentlyWith(project.configurations.getByName(dependencies.sourceSet.runtimeClasspathConfigurationName))

                project.configurations.named(dependencies.sourceSet.compileClasspathConfigurationName) {
                    it.extendsFrom(clientLibrariesConfiguration)
                }

                project.configurations.named(dependencies.sourceSet.runtimeClasspathConfigurationName) {
                    it.extendsFrom(clientLibrariesConfiguration)
                }

                project.tasks.named(dependencies.sourceSet.downloadAssetsTaskName, DownloadAssets::class.java) {
                    it.version.set(minecraftVersion)
                }

                project.tasks.named(dependencies.sourceSet.extractNativesTaskName, ExtractNatives::class.java) {
                    it.version.set(minecraftVersion)
                }
            }

            client.runConfiguration {
                it.sourceSet(client.sourceSet)

                it.defaults {
                    it.extension<FabricRunsDefaultsContainer>().client(minecraftVersion)
                }
            }

            generateModJson.configure {
                it.clientMixinConfigs.from(project.configurations.named(client.sourceSet.mixinsConfigurationName))
            }

            this.client = client

            this.compilations.add(client)
            this.runnables.add(client)
        } else if (client !is RunnableCompilation) {
            throw InvalidUserCodeException("Used `client()` on a FabricTarget after previously using `includedClient()`")
        }

        action?.execute(client!! as RunnableTargetCompilation)
    }

    override fun mappings(action: Action<MappingsBuilder>) {
        hasMappings.set(true)

        val mappings = mutableListOf<MappingDependencyProvider>()

        action.execute(MappingsBuilder(project, mappings))

        main.dependencies {
            addMappings(mappings)
        }
    }

    private fun addMappings(providers: List<MappingDependencyProvider>) {
        mappingProviders.addAll(providers)
    }
}
