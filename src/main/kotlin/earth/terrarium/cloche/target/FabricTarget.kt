package earth.terrarium.cloche.target

import earth.terrarium.cloche.ClocheDependencyHandler
import earth.terrarium.cloche.ClocheExtension
import earth.terrarium.cloche.ClochePlugin
import net.msrandom.minecraftcodev.accesswidener.accessWidenersConfigurationName
import net.msrandom.minecraftcodev.core.MinecraftCodevExtension
import net.msrandom.minecraftcodev.core.task.DownloadMinecraftMappings
import net.msrandom.minecraftcodev.core.task.ResolveMinecraftClient
import net.msrandom.minecraftcodev.core.task.ResolveMinecraftCommon
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.fabric.MinecraftCodevFabricExtension
import net.msrandom.minecraftcodev.fabric.MinecraftCodevFabricPlugin
import net.msrandom.minecraftcodev.fabric.runs.FabricRunsDefaultsContainer
import net.msrandom.minecraftcodev.fabric.task.SplitModClients
import net.msrandom.minecraftcodev.mixins.mixinsConfigurationName
import net.msrandom.minecraftcodev.remapper.mappingsConfigurationName
import net.msrandom.minecraftcodev.remapper.task.Remap
import net.msrandom.minecraftcodev.runs.MinecraftRunConfigurationBuilder
import net.msrandom.minecraftcodev.runs.downloadAssetsTaskName
import net.msrandom.minecraftcodev.runs.extractNativesTaskName
import net.msrandom.minecraftcodev.runs.task.DownloadAssets
import net.msrandom.minecraftcodev.runs.task.ExtractNatives
import org.gradle.api.Action
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.SourceSet
import org.spongepowered.asm.mixin.MixinEnvironment.Side
import java.util.*

abstract class FabricTarget(private val name: String) : MinecraftTarget, ClientTarget {
    private val minecraftCommonClasspath = project.configurations.create(lowerCamelCaseGradleName("minecraft", name, "commonClasspath")) {
        it.isCanBeConsumed = false
    }

    private val minecraftClientClasspath = project.configurations.create(lowerCamelCaseGradleName("minecraft", name, "clientClasspath")) {
        it.isCanBeConsumed = false
    }

    private val fabricLoaderConfiguration = project.configurations.create(lowerCamelCaseGradleName(name, "loader")) {
        it.isCanBeConsumed = false
    }

    private val downloadClientMappings = project.tasks.register(lowerCamelCaseGradleName("download", name, "clientMappings"), DownloadMinecraftMappings::class.java) {
        it.version.set(minecraftVersion)
        it.server.set(false)
    }

    private val resolveCommonMinecraft = project.tasks.register(lowerCamelCaseGradleName("resolve", name, "common"), ResolveMinecraftCommon::class.java) {
        it.version.set(minecraftVersion)
    }

    private val resolveClientMinecraft = project.tasks.register(lowerCamelCaseGradleName("resolve", name, "client"), ResolveMinecraftClient::class.java) {
        it.version.set(minecraftVersion)
    }

    private val remapCommonMinecraftIntermediary = project.tasks.register(lowerCamelCaseGradleName("remap", name, "commonMinecraft", remapNamespace), Remap::class.java) {
        it.inputFiles.from(resolveCommonMinecraft.flatMap(ResolveMinecraftCommon::output))
        it.sourceNamespace.set("obf")
        it.targetNamespace.set(remapNamespace)
        it.classpath.from(minecraftCommonClasspath)
        it.filterMods.set(false)
    }

    private val remapClientMinecraftIntermediary = project.tasks.register(lowerCamelCaseGradleName("remap", name, "clientMinecraft", remapNamespace), Remap::class.java) {
        it.inputFiles.from(resolveClientMinecraft.flatMap(ResolveMinecraftClient::output))
        it.sourceNamespace.set("obf")
        it.targetNamespace.set(remapNamespace)
        it.classpath.from(minecraftClientClasspath)
        it.classpath.from(resolveCommonMinecraft.flatMap(ResolveMinecraftCommon::output))
        it.filterMods.set(false)
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
        val registerJarSplitter = { classpath: FileCollection, nameParts: Array<String> ->
            val splitTask = project.tasks.register(lowerCamelCaseGradleName("split", *nameParts, "classpath"), SplitModClients::class.java) {
                it.inputFiles.from(classpath)
            }

            project.files(splitTask.map(SplitModClients::outputFiles))
        }

        val registerClientJarSplitter = { classpath: FileCollection, nameParts: Array<String> ->
            val splitTask = project.tasks.register(lowerCamelCaseGradleName("split", *nameParts, "classpath"), SplitModClients::class.java) {
                it.inputFiles.from(classpath)
            }

            project.files(splitTask.map(SplitModClients::outputFiles))
        }

        main = project.objects.newInstance(
            TargetCompilation::class.java,
            SourceSet.MAIN_SOURCE_SET_NAME,
            this,
            project.files(remapCommonMinecraftIntermediary.map(Remap::outputFiles)),
            Optional.empty<TargetCompilation>(),
            registerJarSplitter,
            Side.SERVER,
            remapNamespace,
            minecraftCommonClasspath,
        )

        client = project.objects.newInstance(
            TargetCompilation::class.java,
            ClochePlugin.CLIENT_COMPILATION_NAME,
            this,
            project.files(remapClientMinecraftIntermediary.map(Remap::outputFiles)),
            Optional.of(main),
            registerClientJarSplitter,
            Side.CLIENT,
            remapNamespace,
            minecraftCommonClasspath + minecraftClientClasspath + project.files(remapCommonMinecraftIntermediary.map(Remap::outputFiles)),
        )

        data = project.objects.newInstance(
            TargetCompilation::class.java,
            ClochePlugin.DATA_COMPILATION_NAME,
            this,
            project.files(remapCommonMinecraftIntermediary.map(Remap::outputFiles)),
            Optional.of(main),
            registerJarSplitter,
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
            with(project) {
                project.dependencies.addProvider(
                    main.sourceSet.mappingsConfigurationName,
                    minecraftVersion.map { "net.fabricmc:${MinecraftCodevFabricPlugin.INTERMEDIARY_MAPPINGS_NAMESPACE}:$it:v2" })

                if (!hasMappings) {
                    project.dependencies.add(main.sourceSet.mappingsConfigurationName, project.files(downloadClientMappings.flatMap(DownloadMinecraftMappings::output)))
                }

                project.dependencies.add(main.sourceSet.mixinsConfigurationName, mixins)
                project.dependencies.add(main.sourceSet.accessWidenersConfigurationName, accessWideners)
            }

            val fabric = project.extension<MinecraftCodevExtension>().extension<MinecraftCodevFabricExtension>()

            minecraftCommonClasspath.dependencies.addAllLater(
                minecraftVersion.flatMap {
                    fabric.fabricCommonDependencies(it, fabricLoaderConfiguration)
                }
            )

            project.configurations.named(dependencies.implementation.configurationName) {
                it.extendsFrom(minecraftCommonClasspath)
            }

            project.dependencies.addProvider(fabricLoaderConfiguration.name, loaderVersion.map { version -> "net.fabricmc:fabric-loader:$version" })

            project.configurations.named(dependencies.implementation.configurationName) {
                it.extendsFrom(fabricLoaderConfiguration)
            }
        }

        client.dependencies { dependencies ->
            val fabric = project.extension<MinecraftCodevExtension>().extension<MinecraftCodevFabricExtension>()

            minecraftClientClasspath.dependencies.addAllLater(
                minecraftVersion.flatMap {
                    fabric.fabricClientDependencies(it, fabricLoaderConfiguration)
                }
            )

            project.configurations.named(dependencies.implementation.configurationName) {
                it.extendsFrom(minecraftClientClasspath)
            }

            with(project) {
                project.dependencies.add(client.sourceSet.mixinsConfigurationName, client.mixins)
                project.dependencies.add(client.sourceSet.accessWidenersConfigurationName, client.accessWideners)

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
                project.dependencies.add(data.sourceSet.mixinsConfigurationName, data.mixins)
                project.dependencies.add(data.sourceSet.accessWidenersConfigurationName, data.accessWideners)

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
        modImplementation(group = "net.fabricmc.fabric-api", name = "fabric-api", version = apiVersion)
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
                    project.dependencies.addProvider(main.sourceSet.mappingsConfigurationName, minecraftVersion.map(mapping))
                }
            }
        }
    }
}
