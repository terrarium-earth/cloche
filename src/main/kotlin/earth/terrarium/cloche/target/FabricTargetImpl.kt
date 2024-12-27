package earth.terrarium.cloche.target

import earth.terrarium.cloche.*
import earth.terrarium.cloche.ClochePlugin.Companion.STUB_DEPENDENCY
import net.msrandom.minecraftcodev.core.MinecraftComponentMetadataRule
import net.msrandom.minecraftcodev.core.VERSION_MANIFEST_URL
import net.msrandom.minecraftcodev.core.task.ResolveMinecraftClient
import net.msrandom.minecraftcodev.core.task.ResolveMinecraftCommon
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.getGlobalCacheDirectory
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.fabric.MinecraftCodevFabricPlugin
import net.msrandom.minecraftcodev.fabric.runs.FabricRunsDefaultsContainer
import net.msrandom.minecraftcodev.remapper.mappingsConfigurationName
import net.msrandom.minecraftcodev.remapper.task.RemapTask
import net.msrandom.minecraftcodev.runs.downloadAssetsTaskName
import net.msrandom.minecraftcodev.runs.extractNativesTaskName
import net.msrandom.minecraftcodev.runs.task.DownloadAssets
import net.msrandom.minecraftcodev.runs.task.ExtractNatives
import org.gradle.api.Action
import org.gradle.api.DomainObjectCollection
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.SourceSet
import org.gradle.nativeplatform.OperatingSystemFamily
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.spongepowered.asm.mixin.MixinEnvironment.Side
import javax.inject.Inject

internal abstract class FabricTargetImpl @Inject constructor(private val name: String) : MinecraftTargetInternal,
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
        }

    private val remapCommon = project.tasks.register(
        lowerCamelCaseGradleName("remap", featureName, "commonMinecraftNamed"),
        RemapTask::class.java
    ) {
        it.group = "minecraft-transforms"

        it.inputFile.set(remapCommonMinecraftIntermediary.flatMap(RemapTask::outputFile))

        it.classpath.from(commonLibrariesConfiguration)

        it.mappings.from(project.configurations.named(main.sourceSet.mappingsConfigurationName))

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

        it.mappings.from(project.configurations.named(main.sourceSet.mappingsConfigurationName))

        it.sourceNamespace.set(remapNamespace)
    }

    final override lateinit var main: TargetCompilation
    final override var client: RunnableInternal? = null
    final override var data: RunnableTargetCompilation? = null

    override val dependsOn: DomainObjectCollection<CommonTarget> =
        project.objects.domainObjectSet(CommonTarget::class.java)

    protected abstract val providerFactory: ProviderFactory
        @Inject get

    final override val remapNamespace: Provider<String>
        get() = providerFactory.provider { MinecraftCodevFabricPlugin.INTERMEDIARY_MAPPINGS_NAMESPACE }

    private var clientMode = project.objects.property(ClientMode::class.java).apply {
        convention(ClientMode.None)
    }

    private var hasMappings = project.objects.property(Boolean::class.java).apply {
        convention(false)
    }

    override val loaderAttributeName get() = FABRIC
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

        fun <T> clientAlternative(normal: Provider<T>, client: Provider<T>) = clientMode.flatMap {
            if (it == ClientMode.Included) {
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

        val mainTargetMinecraft = clientMode.map {
            if (it == ClientMode.Included) {
                clientTargetMinecraftName
            } else {
                featureName
            }
        }

        main =
            project.objects.newInstance(
                TargetCompilation::class.java,
                SourceSet.MAIN_SOURCE_SET_NAME,
                this,
                mainIntermediaryFile,
                mainRemappedFile,
                mainTargetMinecraft,
                PublicationVariant.Common,
                Side.SERVER,
                isSingleTarget,
                remapNamespace,
            )

        compilations.addAllLater(clientMode.map {
            if (it == ClientMode.Separate) {
                listOf(main, client as RunnableTargetCompilation)
            } else {
                listOf(main)
            }
        })

        runnables.addAllLater(clientMode.map {
            if (it == ClientMode.None) {
                emptyList()
            } else {
                listOf(client as RunnableInternal)
            }
        })

        main.dependencies { dependencies ->
            commonLibrariesConfiguration.shouldResolveConsistentlyWith(project.configurations.getByName(dependencies.sourceSet.runtimeClasspathConfigurationName))

            project.configurations.named(dependencies.sourceSet.compileClasspathConfigurationName) {
                it.extendsFrom(commonLibrariesConfiguration)
            }

            project.configurations.named(dependencies.sourceSet.runtimeClasspathConfigurationName) {
                it.extendsFrom(commonLibrariesConfiguration)
            }

            dependencies.implementation(loaderVersion.map { version -> "net.fabricmc:fabric-loader:$version" })

            remapCommonMinecraftIntermediary.configure {
                it.mappings.from(project.configurations.named(dependencies.sourceSet.mappingsConfigurationName))
            }

            remapClientMinecraftIntermediary.configure {
                it.mappings.from(project.configurations.named(dependencies.sourceSet.mappingsConfigurationName))
            }

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

                if (clientMode.get() == ClientMode.Included) {
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

    override fun createData(): RunnableTargetCompilation {
        val data =
            project.objects.newInstance(
                RunnableTargetCompilation::class.java,
                ClochePlugin.DATA_COMPILATION_NAME,
                this,
                remapCommonMinecraftIntermediary.flatMap(RemapTask::outputFile),
                remapCommon.flatMap(RemapTask::outputFile),
                project.provider { featureName },
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

        data.runConfiguration {
            it.sourceSet(data.sourceSet)

            it.defaults.extension<FabricRunsDefaultsContainer>().data(minecraftVersion) { datagen ->
                datagen.modId.set(project.extension<ClocheExtension>().metadata.modId)

                datagen.outputDirectory.set(datagenDirectory)
            }
        }

        return data
    }

    override fun server(action: Action<Runnable>?) {
        if (server == null) {
            val server = project.objects.newInstance(SimpleRunnable::class.java, ClochePlugin.SERVER_RUNNABLE_NAME)

            server.runConfiguration {
                it.sourceSet(main.sourceSet)
                it.defaults.extension<FabricRunsDefaultsContainer>().server()
            }

            runnables.add(server)

            this.server = server
        }

        action?.execute(server!!)
    }

    override fun includedClient(action: Action<Runnable>?) {
        if (clientMode.isPresent && clientMode.get() == ClientMode.Separate) {
            throw InvalidUserCodeException("Used 'includedClient' in target $name after already configuring client compilation")
        }

        clientMode.set(ClientMode.Included)
    }

    override fun client(action: Action<RunnableCompilation>?) {
        if (client == null) {
            clientMode.set(ClientMode.Separate)

            val client =
                project.objects.newInstance(
                    RunnableTargetCompilation::class.java,
                    ClochePlugin.CLIENT_COMPILATION_NAME,
                    this,
                    remapCommonMinecraftIntermediary.flatMap(RemapTask::outputFile),
                    remapClient.flatMap(RemapTask::outputFile),
                    project.provider { clientTargetMinecraftName },
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
                it.sourceSet(clientMode.map {
                    if (it == ClientMode.Separate) {
                        client.sourceSet
                    } else {
                        main.sourceSet
                    }
                })

                it.defaults.extension<FabricRunsDefaultsContainer>().client(minecraftVersion)
            }

            this.client = client
        } else if (client !is RunnableInternal) {
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
