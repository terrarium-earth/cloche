package earth.terrarium.cloche.target

import earth.terrarium.cloche.ClocheDependencyHandler
import earth.terrarium.cloche.ClocheExtension
import earth.terrarium.cloche.ClochePlugin
import net.msrandom.minecraftcodev.accesswidener.dependency.accessWidenersConfigurationName
import net.msrandom.minecraftcodev.core.MinecraftCodevExtension
import net.msrandom.minecraftcodev.core.MinecraftType
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.fabric.MinecraftCodevFabricPlugin
import net.msrandom.minecraftcodev.fabric.runs.FabricRunsDefaultsContainer
import net.msrandom.minecraftcodev.mixins.dependency.mixinsConfigurationName
import net.msrandom.minecraftcodev.remapper.dependency.mappingsConfigurationName
import net.msrandom.minecraftcodev.runs.MinecraftRunConfigurationBuilder
import net.msrandom.minecraftcodev.runs.nativesConfigurationName
import org.gradle.api.Action
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.SourceSet

abstract class FabricTarget : MinecraftTarget, ClientTarget {
    final override val main: TargetCompilation = run {
        project.objects.newInstance(TargetCompilation::class.java, SourceSet.MAIN_SOURCE_SET_NAME, baseDependency, { true })
    }

    final override val test: TargetCompilation = run {
        project.objects.newInstance(TargetCompilation::class.java, SourceSet.TEST_SOURCE_SET_NAME, baseDependency, { true })
    }

    final override val client: TargetCompilation
        get() =
            project.objects.newInstance(TargetCompilation::class.java, ClochePlugin.CLIENT_COMPILATION_NAME, clientDependency, { clientMode == ClientMode.Separate })

    final override var data: TargetCompilation = run {
        project.objects.newInstance(TargetCompilation::class.java, ClochePlugin.DATA_COMPILATION_NAME, baseDependency, { true })
    }

    private var natives = run {
        minecraftVersion.map {
            project.extension<MinecraftCodevExtension>()(MinecraftType.ClientNatives, it)
        }
    }

    private val baseDependency: Provider<ModuleDependency>
        get() = minecraftVersion.map {
            project.extension<MinecraftCodevExtension>()(MinecraftType.Common, it)
        }

    private val clientDependency: Provider<ModuleDependency>
        get() = minecraftVersion.map {
            project.extension<MinecraftCodevExtension>()(MinecraftType.Client, it)
        }

    override val remapNamespace: String?
        get() = MinecraftCodevFabricPlugin.INTERMEDIARY_MAPPINGS_NAMESPACE

    override val accessWideners get() = main.accessWideners
    override val mixins get() = main.mixins

    override val sourceSet get() = main.sourceSet

    private var clientMode = ClientMode.Separate
    private var hasMappings = false

    override val jarCompilations
        get() = listOfNotNull(client)

    abstract val apiVersion: Property<String>
        @Input get

    override val loaderAttributeName get() = "fabric"

    init {
        main.dependencies { dependencies ->
            project.dependencies.addProvider(sourceSet.mappingsConfigurationName, minecraftVersion.map { "net.fabricmc:${MinecraftCodevFabricPlugin.INTERMEDIARY_MAPPINGS_NAMESPACE}:$it:v2" })

            if (clientMode == ClientMode.Included) {
                project.configurations.named(dependencies.implementation.configurationName) {
                    it.dependencies.addLater(client.dependency)
                }

                project.dependencies.addProvider(main.sourceSet.nativesConfigurationName, natives)
            } else {
                project.configurations.named(dependencies.implementation.configurationName) {
                    it.dependencies.addLater(main.dependency)
                }
            }

            project.dependencies.add(main.sourceSet.mixinsConfigurationName, mixins)
            project.dependencies.add(main.sourceSet.accessWidenersConfigurationName, accessWideners)

            if (!hasMappings) {
                val codev = project.extension<MinecraftCodevExtension>()

                project.dependencies.addProvider(main.sourceSet.mappingsConfigurationName, minecraftVersion.map { codev(MinecraftType.ClientMappings, it) })
            }

            dependencies.modImplementation(loaderVersion.map { version -> "net.fabricmc:fabric-loader:$version" })
            dependencies.modImplementation(apiVersion.map { api -> "net.fabricmc.fabric-api:fabric-api:$api" })
        }

        client.dependencies { dependencies ->
            project.configurations.named(dependencies.implementation.configurationName) {
                it.dependencies.addLater(client.dependency)
            }

            project.dependencies.addProvider(client.sourceSet.nativesConfigurationName, natives)

            project.dependencies.add(client.sourceSet.mixinsConfigurationName, client.mixins)
            project.dependencies.add(client.sourceSet.accessWidenersConfigurationName, client.accessWideners)
        }

        test.dependencies {
            project.dependencies.add(test.sourceSet.mixinsConfigurationName, test.mixins)
            project.dependencies.add(test.sourceSet.accessWidenersConfigurationName, test.accessWideners)
        }

        data.dependencies { dependencies ->
            project.dependencies.add(data.sourceSet.mixinsConfigurationName, data.mixins)
            project.dependencies.add(data.sourceSet.accessWidenersConfigurationName, data.accessWideners)

            project.configurations.named(dependencies.implementation.configurationName) {
                it.dependencies.addLater(data.dependency)
            }
        }

        main.runConfiguration {
            it.defaults.extension<FabricRunsDefaultsContainer>().server()
        }

        client.runConfiguration {
            it.defaults.extension<FabricRunsDefaultsContainer>().client()
        }

        test.runConfiguration {
            it.defaults.extension<FabricRunsDefaultsContainer>().gameTestServer()
        }

        data.runConfiguration {
            it.defaults.extension<FabricRunsDefaultsContainer>().data { datagen ->
                datagen.modId.set(project.extension<ClocheExtension>().metadata.modId)

                datagen.outputDirectory.set(datagenDirectory)
            }
        }
    }

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
                project.dependencies.addProvider(main.sourceSet.mappingsConfigurationName, minecraftVersion.map(mapping))
            }
        }
    }
}
