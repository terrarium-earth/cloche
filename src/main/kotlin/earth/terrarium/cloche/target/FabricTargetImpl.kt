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

internal abstract class FabricTargetImpl(
    private val name: String,
) : MinecraftTargetInternal,
    FabricTarget {
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

    final override val main: TargetCompilation
    final override val client: TargetCompilation
    final override val data: TargetCompilation

    final override val remapNamespace: String
        get() = MinecraftCodevFabricPlugin.INTERMEDIARY_MAPPINGS_NAMESPACE

    private var clientMode = ClientMode.Separate
    private var hasMappings = false

    override val loaderAttributeName get() = ClocheExtension::fabric.name
    override val commonType get() = ClocheExtension::fabric.name

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
        val minecraftCommonConfiguration = MinecraftConfiguration(this, name, remapCommonMinecraftIntermediary.flatMap(RemapTask::outputFile), name)
        val minecraftClientConfiguration = MinecraftConfiguration(this, lowerCamelCaseName(name, "client"), remapClientMinecraftIntermediary.flatMap(RemapTask::outputFile), name, "client")

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
                project.provider { remapNamespace },
                false,
            )

        project.dependencies.add(minecraftCommonConfiguration.configurationName, project.dependencies.platform(STUB_DEPENDENCY))
        project.dependencies.add(minecraftClientConfiguration.configurationName, project.dependencies.platform(STUB_DEPENDENCY))

        // project.dependencies.add(minecraftClientConfiguration.configurationName, minecraftCommonConfiguration.dependency)

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

            project.dependencies.components { components ->
                components.withModule(ClochePlugin.STUB_MODULE, MinecraftComponentMetadataRule::class.java) {
                    it.params(
                        getCacheDirectory(project),
                        minecraftVersion,
                        project.provider { VERSION_MANIFEST_URL },
                        project.provider { project.gradle.startParameter.isOffline },
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
                            minecraftVersion,
                            project.provider { VERSION_MANIFEST_URL },
                            project.provider { project.gradle.startParameter.isOffline },
                            true,
                            minecraftClientConfiguration.targetMinecraftAttribute,
                            MinecraftAttributes.TARGET_MINECRAFT,
                            minecraftCommonConfiguration.targetMinecraftAttribute,
                            minecraftClientConfiguration.targetMinecraftAttribute,
                        )
                    }
                }
            }

            if (!hasMappings) {
                val providers = buildList { MappingsBuilder(project, this).official() }

                addMappings(providers)
            }
        }

        client.dependencies { dependencies ->
            project.dependencies.components { components ->
                components.withModule(ClochePlugin.STUB_MODULE, MinecraftComponentMetadataRule::class.java) {
                    it.params(
                        getCacheDirectory(project),
                        minecraftVersion,
                        project.provider { VERSION_MANIFEST_URL },
                        project.provider { project.gradle.startParameter.isOffline },
                        true,
                        minecraftClientConfiguration.targetMinecraftAttribute,
                        MinecraftAttributes.TARGET_MINECRAFT,
                        minecraftCommonConfiguration.targetMinecraftAttribute,
                        minecraftClientConfiguration.targetMinecraftAttribute,
                    )
                }
            }

            project.tasks.withType(DownloadAssets::class.java).named(dependencies.sourceSet.downloadAssetsTaskName) {
                it.version.set(minecraftVersion)
            }

            project.tasks.withType(ExtractNatives::class.java).named(dependencies.sourceSet.extractNativesTaskName) {
                it.version.set(minecraftVersion)
            }
        }

        data.dependencies { dependencies ->
            project.tasks.withType(DownloadAssets::class.java).named(dependencies.sourceSet.downloadAssetsTaskName) {
                it.version.set(minecraftVersion)
            }
        }

        main.runConfiguration {
            it.defaults.extension<FabricRunsDefaultsContainer>().server()

            with(project) {
                it.sourceSet(main.sourceSet)
            }
        }

        client.runConfiguration {
            it.defaults.extension<FabricRunsDefaultsContainer>().client(minecraftVersion)

            with(project) {
                if (clientMode == ClientMode.Separate) {
                    it.sourceSet(client.sourceSet)
                } else {
                    it.sourceSet(main.sourceSet)
                }
            }
        }

        data.runConfiguration {
            it.defaults.extension<FabricRunsDefaultsContainer>().data(minecraftVersion) { datagen ->
                with(project) {
                    it.sourceSet(data.sourceSet)
                }

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
        val sourceSet = with(project) {
            main.sourceSet
        }

        for (mapping in providers) {
            project.dependencies.addProvider(sourceSet.mappingsConfigurationName, minecraftVersion.map { mapping(it, this@FabricTargetImpl.name) })
        }
    }
}
