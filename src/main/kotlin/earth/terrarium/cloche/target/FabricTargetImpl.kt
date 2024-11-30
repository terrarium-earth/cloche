package earth.terrarium.cloche.target

import earth.terrarium.cloche.*
import earth.terrarium.cloche.ClochePlugin.Companion.STUB_DEPENDENCY
import net.msrandom.minecraftcodev.core.MinecraftComponentMetadataRule
import net.msrandom.minecraftcodev.core.VERSION_MANIFEST_URL
import net.msrandom.minecraftcodev.core.task.ResolveMinecraftClient
import net.msrandom.minecraftcodev.core.task.ResolveMinecraftCommon
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.getCacheDirectory
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseName
import net.msrandom.minecraftcodev.fabric.MinecraftCodevFabricPlugin
import net.msrandom.minecraftcodev.fabric.runs.FabricRunsDefaultsContainer
import net.msrandom.minecraftcodev.remapper.mappingsConfigurationName
import net.msrandom.minecraftcodev.remapper.task.RemapTask
import net.msrandom.minecraftcodev.runs.downloadAssetsTaskName
import net.msrandom.minecraftcodev.runs.extractNativesTaskName
import net.msrandom.minecraftcodev.runs.task.DownloadAssets
import net.msrandom.minecraftcodev.runs.task.ExtractNatives
import org.gradle.api.Action
import org.gradle.api.tasks.SourceSet
import org.spongepowered.asm.mixin.MixinEnvironment.Side
import java.util.*
import javax.inject.Inject

internal abstract class FabricTargetImpl @Inject constructor(private val name: String) : MinecraftTargetInternal, FabricTarget {
    private val resolveCommonMinecraft =
        project.tasks.register(lowerCamelCaseGradleName("resolve", name, "common"), ResolveMinecraftCommon::class.java) {
            it.version.set(minecraftVersion)
        }

    private val resolveClientMinecraft =
        project.tasks.register(lowerCamelCaseGradleName("resolve", name, "client"), ResolveMinecraftClient::class.java) {
            it.version.set(minecraftVersion)
        }

    private val remapCommonMinecraftIntermediary =
        project.tasks.register(
            project.addSetupTask(lowerCamelCaseGradleName("remap", name, "commonMinecraft", remapNamespace)),
            RemapTask::class.java,
        ) {
            it.inputFile.set(resolveCommonMinecraft.flatMap(ResolveMinecraftCommon::output))
            it.sourceNamespace.set("obf")
            it.targetNamespace.set(remapNamespace)
        }

    private val remapClientMinecraftIntermediary =
        project.tasks.register(
            project.addSetupTask(lowerCamelCaseGradleName("remap", name, "clientMinecraft", remapNamespace)),
            RemapTask::class.java,
        ) {
            it.inputFile.set(resolveClientMinecraft.flatMap(ResolveMinecraftClient::output))
            it.sourceNamespace.set("obf")
            it.targetNamespace.set(remapNamespace)

            // TODO Incorrect classpath. perhaps these should be also transforms? but then we have two layers of remaps in the same transform chain, which ig is fine?
            it.classpath.from(resolveCommonMinecraft.flatMap(ResolveMinecraftCommon::output))
        }

    private val minecraftCommonConfiguration = MinecraftConfiguration(this, name, remapCommonMinecraftIntermediary.flatMap(RemapTask::outputFile), featureName)
    private val minecraftClientConfiguration = MinecraftConfiguration(this, lowerCamelCaseName(name, "client"), remapClientMinecraftIntermediary.flatMap(RemapTask::outputFile), featureName, "client")

    final override lateinit var main: TargetCompilation
    final override lateinit var client: TargetCompilation
    final override lateinit var data: TargetCompilation

    final override val remapNamespace: String
        get() = MinecraftCodevFabricPlugin.INTERMEDIARY_MAPPINGS_NAMESPACE

    private var clientMode = ClientMode.Separate
    private var hasMappings = false

    override val loaderAttributeName get() = FABRIC
    override val commonType get() = FABRIC

    override val compilations: List<RunnableCompilationInternal>
        get() = if (clientMode == ClientMode.Separate) {
            listOf(main, client, data)
        } else {
            listOf(main, data)
        }

    override val runnables: List<RunnableInternal>
        get() = if (clientMode == ClientMode.None) {
            listOf(main, data)
        } else {
            listOf(main, client, data)
        }

    init {
        project.dependencies.add(minecraftCommonConfiguration.configurationName, project.dependencies.platform(STUB_DEPENDENCY))
        project.dependencies.add(minecraftClientConfiguration.configurationName, project.dependencies.platform(STUB_DEPENDENCY))

        // project.dependencies.add(minecraftClientConfiguration.configurationName, minecraftCommonConfiguration.dependency)
    }

    override fun initialize(isSingleTarget: Boolean) {
        main =
            project.objects.newInstance(
                TargetCompilation::class.java,
                SourceSet.MAIN_SOURCE_SET_NAME,
                this,
                {
                    if (clientMode == ClientMode.Included) {
                        minecraftClientConfiguration
                    } else {
                        minecraftCommonConfiguration
                    }
                },
                PublicationVariant.Common,
                Optional.empty<TargetCompilation>(),
                Side.SERVER,
                isSingleTarget,
                project.provider { remapNamespace },
                false,
            )

        client =
            project.objects.newInstance(
                TargetCompilation::class.java,
                ClochePlugin.CLIENT_COMPILATION_NAME,
                this,
                { minecraftClientConfiguration },
                PublicationVariant.Client,
                Optional.of(main),
                Side.CLIENT,
                isSingleTarget,
                project.provider { remapNamespace },
                false,
            )

        data =
            project.objects.newInstance(
                TargetCompilation::class.java,
                ClochePlugin.DATA_COMPILATION_NAME,
                this,
                { minecraftCommonConfiguration },
                PublicationVariant.Data,
                Optional.of(main),
                Side.SERVER,
                isSingleTarget,
                project.provider { remapNamespace },
                false,
            )

        main.dependencies { dependencies ->
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

            project.afterEvaluate { project ->
                project.dependencies.components { components ->
                    components.withModule(ClochePlugin.STUB_MODULE, MinecraftComponentMetadataRule::class.java) {
                        it.params(
                            getCacheDirectory(project),
                            minecraftVersion.get(),
                            VERSION_MANIFEST_URL,
                            project.gradle.startParameter.isOffline,
                            false,
                            minecraftCommonConfiguration.targetMinecraftAttribute,
                            MinecraftAttributes.TARGET_MINECRAFT,
                            minecraftCommonConfiguration.targetMinecraftAttribute,
                            minecraftClientConfiguration.targetMinecraftAttribute,
                        )
                    }
                }

                if (clientMode == ClientMode.Included) {
                    project.dependencies.components { components ->
                        components.withModule(ClochePlugin.STUB_MODULE, MinecraftComponentMetadataRule::class.java) {
                            it.params(
                                getCacheDirectory(project),
                                minecraftVersion.get(),
                                VERSION_MANIFEST_URL,
                                project.gradle.startParameter.isOffline,
                                true,
                                minecraftClientConfiguration.targetMinecraftAttribute,
                                MinecraftAttributes.TARGET_MINECRAFT,
                                minecraftCommonConfiguration.targetMinecraftAttribute,
                                minecraftClientConfiguration.targetMinecraftAttribute,
                            )
                        }
                    }
                }
            }

            if (!hasMappings) {
                val providers = buildList { MappingsBuilder(project, this).official() }

                addMappings(providers)
            }
        }

        client.dependencies { dependencies ->
            project.afterEvaluate { project ->
                project.dependencies.components { components ->
                    components.withModule(ClochePlugin.STUB_MODULE, MinecraftComponentMetadataRule::class.java) {
                        it.params(
                            getCacheDirectory(project),
                            minecraftVersion.get(),
                            VERSION_MANIFEST_URL,
                            project.gradle.startParameter.isOffline,
                            true,
                            minecraftClientConfiguration.targetMinecraftAttribute,
                            MinecraftAttributes.TARGET_MINECRAFT,
                            minecraftCommonConfiguration.targetMinecraftAttribute,
                            minecraftClientConfiguration.targetMinecraftAttribute,
                        )
                    }
                }
            }

            project.tasks.named(dependencies.sourceSet.downloadAssetsTaskName, DownloadAssets::class.java) {
                it.version.set(minecraftVersion)
            }

            project.tasks.named(dependencies.sourceSet.extractNativesTaskName, ExtractNatives::class.java) {
                it.version.set(minecraftVersion)
            }
        }

        data.dependencies { dependencies ->
            project.tasks.named(dependencies.sourceSet.downloadAssetsTaskName, DownloadAssets::class.java) {
                it.version.set(minecraftVersion)
            }
        }

        main.runConfiguration {
            it.sourceSet(main.sourceSet)
            it.defaults.extension<FabricRunsDefaultsContainer>().server()
        }

        client.runConfiguration {
            if (clientMode == ClientMode.Separate) {
                it.sourceSet(client.sourceSet)
            } else {
                it.sourceSet(main.sourceSet)
            }

            it.defaults.extension<FabricRunsDefaultsContainer>().client(minecraftVersion)
        }

        data.runConfiguration {
            it.sourceSet(data.sourceSet)

            it.defaults.extension<FabricRunsDefaultsContainer>().data(minecraftVersion) { datagen ->
                datagen.modId.set(project.extension<ClocheExtension>().metadata.modId)

                datagen.outputDirectory.set(datagenDirectory)
            }
        }
    }

    override fun getName() = name

    override fun noClient() {
        clientMode = ClientMode.None
    }

    override fun includeClient() {
        clientMode = ClientMode.Included
    }

    override fun client(action: Action<RunnableCompilation>) {
        action.execute(client)
    }

    override fun mappings(action: Action<MappingsBuilder>) {
        hasMappings = true

        val mappings = mutableListOf<MappingDependencyProvider>()

        action.execute(MappingsBuilder(project, mappings))

        main.dependencies {
            addMappings(mappings)
        }
    }

    private fun addMappings(providers: List<MappingDependencyProvider>) {
        val sourceSet = main.sourceSet

        for (mapping in providers) {
            project.dependencies.addProvider(sourceSet.mappingsConfigurationName, minecraftVersion.map { mapping(it, this@FabricTargetImpl.name) })
        }
    }
}
