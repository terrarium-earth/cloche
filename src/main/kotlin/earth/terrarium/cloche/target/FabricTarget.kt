package earth.terrarium.cloche.target

import earth.terrarium.cloche.*
import net.msrandom.minecraftcodev.accesswidener.accessWidenersConfigurationName
import net.msrandom.minecraftcodev.core.MinecraftComponentMetadataRule
import net.msrandom.minecraftcodev.core.VERSION_MANIFEST_URL
import net.msrandom.minecraftcodev.core.task.ResolveMinecraftClient
import net.msrandom.minecraftcodev.core.task.ResolveMinecraftCommon
import net.msrandom.minecraftcodev.core.task.ResolveMinecraftMappings
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.getCacheDirectory
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.fabric.MinecraftCodevFabricPlugin
import net.msrandom.minecraftcodev.fabric.runs.FabricRunsDefaultsContainer
import net.msrandom.minecraftcodev.remapper.mappingsConfigurationName
import net.msrandom.minecraftcodev.remapper.task.RemapTask
import net.msrandom.minecraftcodev.runs.MinecraftRunConfigurationBuilder
import net.msrandom.minecraftcodev.runs.downloadAssetsTaskName
import net.msrandom.minecraftcodev.runs.extractNativesTaskName
import net.msrandom.minecraftcodev.runs.task.DownloadAssets
import net.msrandom.minecraftcodev.runs.task.ExtractNatives
import org.gradle.api.Action
import org.gradle.api.tasks.SourceSet
import org.spongepowered.asm.mixin.MixinEnvironment.Side
import java.util.*

abstract class FabricTarget(
    private val name: String,
) : MinecraftTarget,
    ClientTarget {
    private val minecraftCommonClasspath =
        project.configurations.create(lowerCamelCaseGradleName("minecraft", name, "commonClasspath")) {
            it.isCanBeConsumed = false

            it.attributes { attributes ->
                attributes.attribute(
                    TARGET_MINECRAFT_ATTRIBUTE,
                    this@FabricTarget.name,
                )
            }
        }

    private val minecraftClientClasspath =
        project.configurations.create(lowerCamelCaseGradleName("minecraft", name, "clientClasspath")) {
            it.isCanBeConsumed = false

            it.attributes { attributes ->
                attributes.attribute(
                    TARGET_MINECRAFT_ATTRIBUTE,
                    lowerCamelCaseGradleName(this@FabricTarget.name, ClochePlugin.CLIENT_COMPILATION_NAME),
                )
            }

            it.extendsFrom(minecraftCommonClasspath)
        }

    private val fabricLoaderConfiguration =
        project.configurations.create(lowerCamelCaseGradleName(name, "loader")) {
            it.isCanBeConsumed = false
        }

    private val resolveClientMappings =
        project.tasks.register(lowerCamelCaseGradleName("resolve", name, "clientMappings"), ResolveMinecraftMappings::class.java) {
            it.version.set(minecraftVersion)
            it.server.set(false)
        }

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
            it.classpath.from(minecraftCommonClasspath)
        }

    private val remapClientMinecraftIntermediary =
        project.tasks.register(
            project.addSetupTask(lowerCamelCaseGradleName("remap", name, "clientMinecraft", remapNamespace)),
            RemapTask::class.java,
        ) {
            it.inputFile.set(resolveClientMinecraft.flatMap(ResolveMinecraftClient::output))
            it.sourceNamespace.set("obf")
            it.targetNamespace.set(remapNamespace)
            it.classpath.from(minecraftClientClasspath)
            it.classpath.from(resolveCommonMinecraft.flatMap(ResolveMinecraftCommon::output))
        }

    final override val main: TargetCompilation
    final override val client: TargetCompilation
    final override val data: TargetCompilation

    final override val remapNamespace: String
        get() = MinecraftCodevFabricPlugin.INTERMEDIARY_MAPPINGS_NAMESPACE

    final override val accessWideners get() = main.accessWideners
    final override val mixins get() = main.mixins

    private var clientMode = ClientMode.Separate
    private var hasMappings = false

    override val loaderAttributeName get() = "fabric"

    init {
        main =
            project.objects.newInstance(
                TargetCompilation::class.java,
                SourceSet.MAIN_SOURCE_SET_NAME,
                this,
                remapCommonMinecraftIntermediary.flatMap(RemapTask::outputFile),
                Optional.empty<TargetCompilation>(),
                Side.SERVER,
                remapNamespace,
                minecraftCommonClasspath,
            )

        client =
            project.objects.newInstance(
                TargetCompilation::class.java,
                ClochePlugin.CLIENT_COMPILATION_NAME,
                this,
                remapClientMinecraftIntermediary.flatMap(RemapTask::outputFile),
                Optional.of(main),
                Side.CLIENT,
                remapNamespace,
                minecraftCommonClasspath + minecraftClientClasspath +
                        project.files(remapCommonMinecraftIntermediary.flatMap(RemapTask::outputFile)),
            )

        data =
            project.objects.newInstance(
                TargetCompilation::class.java,
                ClochePlugin.DATA_COMPILATION_NAME,
                this,
                remapCommonMinecraftIntermediary.flatMap(RemapTask::outputFile),
                Optional.of(main),
                Side.SERVER,
                remapNamespace,
                minecraftCommonClasspath,
            )

        project.afterEvaluate {
            remapCommonMinecraftIntermediary.configure {
                with(project) {
                    it.mappings.from(project.configurations.named(main.sourceSet.mappingsConfigurationName))
                }
            }

            remapClientMinecraftIntermediary.configure {
                with(project) {
                    it.mappings.from(project.configurations.named(main.sourceSet.mappingsConfigurationName))
                }
            }
        }

        main.dependencies { dependencies ->
            project.dependencies.add(minecraftCommonClasspath.name, project.dependencies.platform(ClochePlugin.STUB_DEPENDENCY))

            project.configurations.named(dependencies.implementation.configurationName) {
                it.extendsFrom(minecraftCommonClasspath)
            }

            project.dependencies.addProvider(
                fabricLoaderConfiguration.name,
                loaderVersion.map { version -> "net.fabricmc:fabric-loader:$version" },
            )

            project.configurations.named(dependencies.implementation.configurationName) {
                it.extendsFrom(fabricLoaderConfiguration)
            }

            with(project) {
                project.configurations.named(main.sourceSet.compileClasspathConfigurationName) {
                    minecraftCommonClasspath.shouldResolveConsistentlyWith(it)
                }

                project.configurations.named(main.sourceSet.runtimeClasspathConfigurationName) {
                    minecraftCommonClasspath.shouldResolveConsistentlyWith(it)
                }

                project.dependencies.addProvider(
                    main.sourceSet.mappingsConfigurationName,
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
                            this@FabricTarget.name,
                            TARGET_MINECRAFT_ATTRIBUTE,
                            this@FabricTarget.name,
                        )
                    }
                }

                if (!hasMappings) {
                    project.dependencies.add(
                        main.sourceSet.mappingsConfigurationName,
                        project.files(resolveClientMappings.flatMap(ResolveMinecraftMappings::output)),
                    )
                }
            }
        }

        client.dependencies { dependencies ->
            project.configurations.named(dependencies.implementation.configurationName) {
                it.extendsFrom(minecraftClientClasspath)
            }

            with(project) {
                project.configurations.named(client.sourceSet.compileClasspathConfigurationName) {
                    minecraftClientClasspath.shouldResolveConsistentlyWith(it)
                }

                project.configurations.named(client.sourceSet.runtimeClasspathConfigurationName) {
                    minecraftClientClasspath.shouldResolveConsistentlyWith(it)
                }

                project.dependencies.components { components ->
                    components.withModule(ClochePlugin.STUB_MODULE, MinecraftComponentMetadataRule::class.java) {
                        it.params(
                            getCacheDirectory(project),
                            minecraftVersion,
                            project.provider { VERSION_MANIFEST_URL },
                            project.provider { project.gradle.startParameter.isOffline },
                            true,
                            lowerCamelCaseGradleName(this@FabricTarget.name, client.name),
                            TARGET_MINECRAFT_ATTRIBUTE,
                            lowerCamelCaseGradleName(this@FabricTarget.name, client.name),
                        )
                    }
                }

                project.tasks.withType(DownloadAssets::class.java).named(client.sourceSet.downloadAssetsTaskName) {
                    it.version.set(minecraftVersion)
                }

                project.tasks.withType(ExtractNatives::class.java).named(client.sourceSet.extractNativesTaskName) {
                    it.version.set(minecraftVersion)
                }
            }
        }

        data.dependencies {
            with(project) {
                project.tasks.withType(DownloadAssets::class.java).named(data.sourceSet.downloadAssetsTaskName) {
                    it.version.set(minecraftVersion)
                }
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
                it.sourceSet(client.sourceSet)
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

    fun ClocheDependencyHandler.fabricApi(apiVersion: String) {
        val dependency = implementation(group = "net.fabricmc.fabric-api", name = "fabric-api", version = apiVersion)

        project.dependencies.add(sourceSet.accessWidenersConfigurationName, dependency)
    }

    override fun getName() = name

    override fun noClient() {
        clientMode = ClientMode.None
    }

    override fun includeClient() {
        clientMode = ClientMode.Included
    }

    fun client(action: Action<RunnableCompilation>) {
        clientMode = ClientMode.Separate

        action.execute(client)
    }

    override fun data(action: Action<RunnableCompilation>?) {
        action?.execute(data)
    }

    override fun dependencies(action: Action<ClocheDependencyHandler>) = main.dependencies(action)

    override fun runConfiguration(action: Action<MinecraftRunConfigurationBuilder>) = main.runConfiguration(action)

    override fun mappings(action: Action<MappingsBuilder>) {
        hasMappings = true

        val mappings = mutableListOf<MappingDependencyProvider>()

        action.execute(MappingsBuilder(project, mappings))

        main.dependencies {
            with(project) {
                for (mapping in mappings) {
                    project.dependencies.addProvider(
                        main.sourceSet.mappingsConfigurationName,
                        minecraftVersion.map { mapping(it, this@FabricTarget.name) },
                    )
                }
            }
        }
    }
}
