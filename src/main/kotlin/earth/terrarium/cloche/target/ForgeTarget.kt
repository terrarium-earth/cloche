package earth.terrarium.cloche.target

import earth.terrarium.cloche.ClocheDependencyHandler
import earth.terrarium.cloche.ClocheExtension
import earth.terrarium.cloche.ClochePlugin
import net.msrandom.minecraftcodev.accesswidener.dependency.accessWidenersConfigurationName
import net.msrandom.minecraftcodev.core.MinecraftCodevExtension
import net.msrandom.minecraftcodev.core.MinecraftType
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.forge.MinecraftCodevForgePlugin
import net.msrandom.minecraftcodev.forge.PatchedMinecraftCodevExtension
import net.msrandom.minecraftcodev.forge.patchesConfigurationName
import net.msrandom.minecraftcodev.forge.runs.ForgeRunsDefaultsContainer
import net.msrandom.minecraftcodev.mixins.dependency.mixinsConfigurationName
import net.msrandom.minecraftcodev.remapper.dependency.mappingsConfigurationName
import net.msrandom.minecraftcodev.runs.MinecraftRunConfigurationBuilder
import net.msrandom.minecraftcodev.runs.nativesConfigurationName
import org.gradle.api.Action
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.SourceSet
import org.gradle.language.jvm.tasks.ProcessResources
import java.io.File

abstract class ForgeTarget : MinecraftTarget {
    final override val main: TargetCompilation = run {
        project.objects.newInstance(TargetCompilation::class.java, SourceSet.MAIN_SOURCE_SET_NAME, baseDependency, { true })
    }

    final override val test: TargetCompilation = run {
        project.objects.newInstance(TargetCompilation::class.java, SourceSet.TEST_SOURCE_SET_NAME, baseDependency, { true })
    }

    private val client: TargetCompilation = run {
        project.objects.newInstance(TargetCompilation::class.java, ClochePlugin.CLIENT_COMPILATION_NAME, baseDependency, { false })
    }

    final override val data: TargetCompilation = run {
        project.objects.newInstance(TargetCompilation::class.java, ClochePlugin.DATA_COMPILATION_NAME, baseDependency, { true })
    }

    private var natives = run {
        minecraftVersion.map {
            project.extension<MinecraftCodevExtension>()(MinecraftType.ClientNatives, it)
        }
    }

    private val baseDependency: Provider<ModuleDependency>
        get() = minecraftVersion.map {
            project.extensions
                .getByType(MinecraftCodevExtension::class.java)
                .extensions
                .getByType(PatchedMinecraftCodevExtension::class.java)(it, patchesConfiguration = main.sourceSet.patchesConfigurationName)
        }

    override val accessWideners get() = main.accessWideners
    override val mixins get() = main.mixins

    override val sourceSet get() = main.sourceSet

    private var hasMappings = false

    override val remapNamespace: String
        get() = MinecraftCodevForgePlugin.SRG_MAPPINGS_NAMESPACE

    abstract val userdevClassifier: Property<String>
        @Input
        @Optional
        get

    open val group
        @Internal
        get() = "net.minecraftforge"

    open val artifact
        @Internal
        get() = "forge"

    override val loaderAttributeName get() = "forge"

    init {
        main.dependencies { dependencies ->
            val userdev = minecraftVersion.flatMap { minecraftVersion ->
                loaderVersion.flatMap { forgeVersion ->
                    userdevClassifier.orElse("userdev").map { userdev ->
                        "$group:$artifact:${version(minecraftVersion, forgeVersion)}:$userdev"
                    }
                }
            }

            project.dependencies.addProvider(main.sourceSet.mappingsConfigurationName, userdev)
            project.dependencies.addProvider(main.sourceSet.patchesConfigurationName, userdev)

            dependencies.implementation(main.dependency)

            project.dependencies.addProvider(main.sourceSet.nativesConfigurationName, natives)

            project.dependencies.add(main.sourceSet.mixinsConfigurationName, main.mixins)
            project.dependencies.add(main.sourceSet.accessWidenersConfigurationName, main.accessWideners)

            if (!hasMappings) {
                val codev = project.extension<MinecraftCodevExtension>()

                project.dependencies.addProvider(main.sourceSet.mappingsConfigurationName, minecraftVersion.map { codev(MinecraftType.ClientMappings, it) })
            }

            project.tasks.named(main.sourceSet.processResourcesTaskName, ProcessResources::class.java) {
                it.from(project.configurations.named(main.sourceSet.mixinsConfigurationName))
            }
        }

        test.dependencies {
            project.dependencies.add(test.sourceSet.mixinsConfigurationName, test.mixins)
            project.dependencies.add(test.sourceSet.accessWidenersConfigurationName, test.accessWideners)
        }

        data.dependencies {
            project.dependencies.add(data.sourceSet.mixinsConfigurationName, data.mixins)
            project.dependencies.add(data.sourceSet.accessWidenersConfigurationName, data.accessWideners)

            it.implementation(data.dependency)
        }

        main.runConfiguration {
            it.defaults.extension<ForgeRunsDefaultsContainer>().server { serverConfig ->
                serverConfig.mixinConfigs.set(project.configurations.named(main.sourceSet.mixinsConfigurationName).map { it.files.map(File::getName) })
            }
        }

        client.runConfiguration {
            it.defaults.extension<ForgeRunsDefaultsContainer>().client { clientConfig ->
                clientConfig.mixinConfigs.set(project.configurations.named(main.sourceSet.mixinsConfigurationName).map { it.files.map(File::getName) })
            }
        }

        test.runConfiguration {
            // it.defaults.extension<ForgeRunsDefaultsContainer>().gameTestServer()
            it.mainClass("test")
        }

        data.runConfiguration {
            it.defaults.extension<ForgeRunsDefaultsContainer>().data { datagen ->
                datagen.mixinConfigs.set(project.configurations.named(main.sourceSet.mixinsConfigurationName).map { it.files.map(File::getName) })

                datagen.modId.set(project.extension<ClocheExtension>().metadata.modId)

                datagen.outputDirectory.set(datagenDirectory)
            }
        }
    }

    open fun version(minecraftVersion: String, loaderVersion: String) =
        "$minecraftVersion-$loaderVersion"

    override fun dependencies(action: Action<ClocheDependencyHandler>) = main.dependencies(action)
    override fun runConfiguration(action: Action<MinecraftRunConfigurationBuilder>) = main.runConfiguration(action)

    override fun data(action: Action<RunnableCompilation>?) {
        action?.execute(data)
    }

    override fun test(action: Action<RunnableCompilation>?) {
        action?.execute(test)
    }

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
