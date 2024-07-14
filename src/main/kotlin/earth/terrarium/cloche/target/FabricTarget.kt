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
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseName
import net.msrandom.minecraftcodev.fabric.MinecraftCodevFabricExtension
import net.msrandom.minecraftcodev.fabric.MinecraftCodevFabricPlugin
import net.msrandom.minecraftcodev.fabric.runs.FabricRunsDefaultsContainer
import net.msrandom.minecraftcodev.mixins.mixinsConfigurationName
import net.msrandom.minecraftcodev.remapper.mappingsConfigurationName
import net.msrandom.minecraftcodev.remapper.task.RemapJar
import net.msrandom.minecraftcodev.runs.MinecraftRunConfigurationBuilder
import net.msrandom.minecraftcodev.runs.nativesConfigurationName
import org.gradle.api.Action
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.SourceSet
import java.util.*

abstract class FabricTarget(private val name: String) : MinecraftTarget, ClientTarget {
    private val minecraftCommonClasspath = project.configurations.create(lowerCamelCaseName(name, "minecraftCommonClasspath")) {
        it.isCanBeConsumed = false
    }

    private val minecraftClientClasspath = project.configurations.create(lowerCamelCaseName(name, "minecraftClientClasspath")) {
        it.isCanBeConsumed = false
    }

    private val fabricLoaderConfiguration = project.configurations.create(lowerCamelCaseName(name, "loader")) {
        it.isCanBeConsumed = false
    }

    private val downloadClientMappings = project.tasks.register(lowerCamelCaseName("download", name, "clientMappings"), DownloadMinecraftMappings::class.java) {
        it.version.set(minecraftVersion)
        it.server.set(false)
    }

    private val resolveCommonMinecraft = project.tasks.register(lowerCamelCaseName("resolveCommon", name), ResolveMinecraftCommon::class.java) {
        it.version.set(minecraftVersion)
    }

    private val resolveClientMinecraft = project.tasks.register(lowerCamelCaseName("resolveClient", name), ResolveMinecraftClient::class.java) {
        it.version.set(minecraftVersion)
    }

    private val remapCommonMinecraftNamedJar = project.tasks.register(lowerCamelCaseName("remap", name, "commonMinecraftNamedJar"), RemapJar::class.java) {
        it.destinationDirectory.set(it.temporaryDir)
        it.archiveBaseName.set("minecraft-common")
        it.archiveVersion.set(name)
        it.archiveClassifier.set("named")
        it.sourceNamespace.set("obf")
        it.targetNamespace.set("named")

        it.mappings.from(main.sourceSet.map(SourceSet::mappingsConfigurationName).flatMap(project.configurations::named))
        it.input.set(resolveCommonMinecraft.flatMap(ResolveMinecraftCommon::output))
        it.classpath.from(minecraftCommonClasspath)
    }

    private val remapClientMinecraftNamedJar = project.tasks.register(lowerCamelCaseName("remap", name, "clientMinecraftNamedJar"), RemapJar::class.java) {
        it.destinationDirectory.set(it.temporaryDir)
        it.archiveBaseName.set("minecraft-client")
        it.archiveVersion.set(name)
        it.archiveClassifier.set("named")
        it.sourceNamespace.set("obf")
        it.targetNamespace.set("named")

        it.mappings.from(main.sourceSet.map(SourceSet::mappingsConfigurationName).flatMap(project.configurations::named))
        it.input.set(resolveClientMinecraft.flatMap(ResolveMinecraftClient::output))
        it.classpath.from(minecraftClientClasspath)
        it.classpath.from(resolveCommonMinecraft.flatMap(ResolveMinecraftCommon::output))
    }

    final override val test: TargetCompilation
    final override val main: TargetCompilation
    final override val client: TargetCompilation
    final override val data: TargetCompilation

    override val remapNamespace: String
        get() = MinecraftCodevFabricPlugin.INTERMEDIARY_MAPPINGS_NAMESPACE

    override val accessWideners get() = main.accessWideners
    override val mixins get() = main.mixins

    private var clientMode = ClientMode.Separate
    private var hasMappings = false

    abstract val apiVersion: Property<String>
        @Input get

    override val loaderAttributeName get() = "fabric"

    init {
        val mappingClasspath = project.files()

        main = project.objects.newInstance(
            TargetCompilation::class.java,
            name,
            SourceSet.MAIN_SOURCE_SET_NAME,
            remapCommonMinecraftNamedJar.flatMap(RemapJar::getArchiveFile),
            Optional.empty<TargetCompilation>(),
            remapNamespace,
            mappingClasspath,
        )

        test = project.objects.newInstance(
            TargetCompilation::class.java,
            name,
            SourceSet.TEST_SOURCE_SET_NAME,
            remapCommonMinecraftNamedJar.flatMap(RemapJar::getArchiveFile),
            Optional.of(main),
            remapNamespace,
            mappingClasspath,
        )

        client = project.objects.newInstance(
            TargetCompilation::class.java,
            name,
            ClochePlugin.CLIENT_COMPILATION_NAME,
            remapClientMinecraftNamedJar.flatMap(RemapJar::getArchiveFile),
            Optional.of(main),
            remapNamespace,
            mappingClasspath,
        )

        data = project.objects.newInstance(
            TargetCompilation::class.java,
            name,
            ClochePlugin.DATA_COMPILATION_NAME,
            remapCommonMinecraftNamedJar.flatMap(RemapJar::getArchiveFile),
            Optional.of(main),
            remapNamespace,
            mappingClasspath,
        )

        val remapCommonMinecraftIntermediaryJar = project.tasks.register(lowerCamelCaseName("remap", name, "commonMinecraftNamed", remapNamespace, "jar"), RemapJar::class.java) {
            it.destinationDirectory.set(it.temporaryDir)
            it.archiveBaseName.set("minecraft-common")
            it.archiveVersion.set(name)
            it.archiveClassifier.set("named")
            it.sourceNamespace.set("obf")
            it.targetNamespace.set(remapNamespace)

            it.mappings.from(main.sourceSet.map(SourceSet::mappingsConfigurationName).flatMap(project.configurations::named))
            it.input.set(resolveCommonMinecraft.flatMap(ResolveMinecraftCommon::output))
            it.classpath.from(minecraftCommonClasspath)
        }

        val remapClientMinecraftIntermediaryJar = project.tasks.register(lowerCamelCaseName("remap", name, "clientMinecraft", remapNamespace, "jar"), RemapJar::class.java) {
            it.destinationDirectory.set(it.temporaryDir)
            it.archiveBaseName.set("minecraft-client")
            it.archiveVersion.set(name)
            it.archiveClassifier.set(remapNamespace)
            it.sourceNamespace.set("obf")
            it.targetNamespace.set(remapNamespace)

            it.mappings.from(main.sourceSet.map(SourceSet::mappingsConfigurationName).flatMap(project.configurations::named))
            it.input.set(resolveClientMinecraft.flatMap(ResolveMinecraftClient::output))
            it.classpath.from(minecraftClientClasspath)
            it.classpath.from(resolveCommonMinecraft.flatMap(ResolveMinecraftCommon::output))
        }

        mappingClasspath.from(remapCommonMinecraftIntermediaryJar.flatMap(RemapJar::getArchiveFile))
        mappingClasspath.from(remapClientMinecraftIntermediaryJar.flatMap(RemapJar::getArchiveFile))

        main.dependencies { dependencies ->
            project.dependencies.addProvider(
                main.sourceSet.get().mappingsConfigurationName,
                minecraftVersion.map { "net.fabricmc:${MinecraftCodevFabricPlugin.INTERMEDIARY_MAPPINGS_NAMESPACE}:$it:v2" })

            if (!hasMappings) {
                project.dependencies.add(main.sourceSet.get().mappingsConfigurationName, project.files(downloadClientMappings.flatMap(DownloadMinecraftMappings::output)))
            }

            dependencies.implementation(main.dependency)
            dependencies.compileOnly(main.compileClasspath)
            dependencies.runtimeOnly(main.runtimeClasspath)

            val fabric = project.extension<MinecraftCodevExtension>().extension<MinecraftCodevFabricExtension>()

            minecraftCommonClasspath.dependencies.addAllLater(
                minecraftVersion.flatMap {
                    fabric.fabricCommonDependencies(it, fabricLoaderConfiguration)
                }
            )

            project.configurations.named(dependencies.implementation.configurationName) {
                it.extendsFrom(minecraftCommonClasspath)
            }

            project.dependencies.add(main.sourceSet.get().mixinsConfigurationName, mixins)
            project.dependencies.add(main.sourceSet.get().accessWidenersConfigurationName, accessWideners)

            project.dependencies.addProvider(fabricLoaderConfiguration.name, loaderVersion.map { version -> "net.fabricmc:fabric-loader:$version" })

            project.configurations.named(dependencies.implementation.configurationName) {
                it.extendsFrom(fabricLoaderConfiguration)
            }

            dependencies.modImplementation(apiVersion.map { api -> "net.fabricmc.fabric-api:fabric-api:$api" })
        }

        client.dependencies { dependencies ->
            dependencies.implementation(client.dependency)
            dependencies.compileOnly(client.compileClasspath)
            dependencies.runtimeOnly(client.runtimeClasspath)

            val fabric = project.extension<MinecraftCodevExtension>().extension<MinecraftCodevFabricExtension>()

            minecraftClientClasspath.dependencies.addAllLater(
                minecraftVersion.flatMap {
                    fabric.fabricClientDependencies(it, fabricLoaderConfiguration)
                }
            )

            project.configurations.named(dependencies.implementation.configurationName) {
                it.extendsFrom(minecraftClientClasspath)
            }

            project.configurations.named(client.sourceSet.get().nativesConfigurationName) {
                val codev = project.extension<MinecraftCodevExtension>()

                it.dependencies.addAllLater(minecraftVersion.flatMap(codev::nativeDependencies))
            }

            project.dependencies.add(client.sourceSet.get().mixinsConfigurationName, client.mixins)
            project.dependencies.add(client.sourceSet.get().accessWidenersConfigurationName, client.accessWideners)
        }

        test.dependencies { dependencies ->
            dependencies.implementation(test.dependency)
            dependencies.compileOnly(test.compileClasspath)
            dependencies.runtimeOnly(test.runtimeClasspath)

            project.dependencies.add(test.sourceSet.get().mixinsConfigurationName, test.mixins)
            project.dependencies.add(test.sourceSet.get().accessWidenersConfigurationName, test.accessWideners)
        }

        data.dependencies { dependencies ->
            dependencies.implementation(data.dependency)
            dependencies.compileOnly(data.compileClasspath)
            dependencies.runtimeOnly(data.runtimeClasspath)

            project.dependencies.add(data.sourceSet.get().mixinsConfigurationName, data.mixins)
            project.dependencies.add(data.sourceSet.get().accessWidenersConfigurationName, data.accessWideners)
        }

        main.runConfiguration {
            it.defaults.extension<FabricRunsDefaultsContainer>().server()
        }

        client.runConfiguration {
            it.defaults.extension<FabricRunsDefaultsContainer>().client(minecraftVersion)
        }

        test.runConfiguration {
            it.defaults.extension<FabricRunsDefaultsContainer>().gameTestServer()
        }

        data.runConfiguration {
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

    fun client(action: Action<RunnableCompilation>) {
        clientMode = ClientMode.Separate

        action.execute(client)
    }

    override fun test(action: Action<RunnableCompilation>?) {
        action?.execute(test)
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
            for (mapping in mappings) {
                project.dependencies.addProvider(main.sourceSet.get().mappingsConfigurationName, minecraftVersion.map(mapping))
            }
        }
    }
}
