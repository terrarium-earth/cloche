package earth.terrarium.cloche.target

import earth.terrarium.cloche.ClocheDependencyHandler
import earth.terrarium.cloche.ClocheExtension
import earth.terrarium.cloche.ClochePlugin
import net.msrandom.minecraftcodev.accesswidener.accessWidenersConfigurationName
import net.msrandom.minecraftcodev.core.MinecraftCodevExtension
import net.msrandom.minecraftcodev.core.task.DownloadMinecraftMappings
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseName
import net.msrandom.minecraftcodev.forge.MinecraftCodevForgeExtension
import net.msrandom.minecraftcodev.forge.MinecraftCodevForgePlugin
import net.msrandom.minecraftcodev.forge.patchesConfigurationName
import net.msrandom.minecraftcodev.forge.runs.ForgeRunsDefaultsContainer
import net.msrandom.minecraftcodev.forge.task.ResolvePatchedMinecraft
import net.msrandom.minecraftcodev.mixins.mixinsConfigurationName
import net.msrandom.minecraftcodev.remapper.MinecraftCodevRemapperPlugin
import net.msrandom.minecraftcodev.remapper.mappingsConfigurationName
import net.msrandom.minecraftcodev.remapper.task.RemapJar
import net.msrandom.minecraftcodev.runs.MinecraftRunConfigurationBuilder
import net.msrandom.minecraftcodev.runs.nativesConfigurationName
import org.gradle.api.Action
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.SourceSet
import org.gradle.language.jvm.tasks.ProcessResources
import java.io.File

abstract class ForgeTarget(private val name: String) : MinecraftTarget {
    private val minecraftForgeClasspath = project.configurations.create(lowerCamelCaseName(name, "minecraftForgeClasspath")) {
        it.isCanBeConsumed = false
    }

    private val downloadClientMappings = project.tasks.register(lowerCamelCaseName("download", name, "clientMappings"), DownloadMinecraftMappings::class.java) {
        it.version.set(minecraftVersion)
        it.server.set(false)
    }

    private val resolvePatchedMinecraft = project.tasks.register(lowerCamelCaseName("resolve", name, "patchedMinecraft"), ResolvePatchedMinecraft::class.java) {
        it.version.set(minecraftVersion)
        it.clientMappings.set(downloadClientMappings.flatMap(DownloadMinecraftMappings::output))

        with(project) {
            it.patches.from(project.configurations.named(main.sourceSet.patchesConfigurationName))
        }
    }

    private val remapJarTask = project.tasks.register(lowerCamelCaseName("remap", name, "minecraft"), RemapJar::class.java) {
        it.destinationDirectory.set(it.temporaryDir)
        it.archiveBaseName.set("patched-minecraft")
        it.archiveVersion.set(name)
        it.archiveClassifier.set(MinecraftCodevRemapperPlugin.NAMED_MAPPINGS_NAMESPACE)
        it.sourceNamespace.set(remapNamespace)
        it.targetNamespace.set(MinecraftCodevRemapperPlugin.NAMED_MAPPINGS_NAMESPACE)

        with(project) {
            it.mappings.from(project.configurations.named(main.sourceSet.mappingsConfigurationName))
        }

        it.input.set(resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::output))
        it.classpath.from(minecraftForgeClasspath)
    }

    final override val main: TargetCompilation

    /*
    private val client: TargetCompilation = run {
        project.objects.newInstance(TargetCompilation::class.java, name, ClochePlugin.CLIENT_COMPILATION_NAME, remapJarTask.flatMap(RemapJar::getArchiveFile))
    }
    */

    final override val data: TargetCompilation

    final override val accessWideners get() = main.accessWideners
    final override val mixins get() = main.mixins

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
        val mappingClasspath = project.files()

        main = run {
            project.objects.newInstance(
                TargetCompilation::class.java,
                SourceSet.MAIN_SOURCE_SET_NAME,
                this,
                remapJarTask.flatMap(RemapJar::getArchiveFile),
                java.util.Optional.empty<TargetCompilation>(),
                remapNamespace,
                mappingClasspath,
            )
        }

        data = run {
            project.objects.newInstance(
                TargetCompilation::class.java,
                ClochePlugin.DATA_COMPILATION_NAME,
                this,
                remapJarTask.flatMap(RemapJar::getArchiveFile),
                java.util.Optional.of(main),
                remapNamespace,
                mappingClasspath,
            )
        }

        val remapPatchedMinecraftIntermediaryJar = project.tasks.register(lowerCamelCaseName("remap", name, "patchedMinecraft", remapNamespace, "jar"), RemapJar::class.java) {
            it.destinationDirectory.set(it.temporaryDir)
            it.archiveBaseName.set("patched-minecraft")
            it.archiveVersion.set(name)
            it.archiveClassifier.set(MinecraftCodevRemapperPlugin.NAMED_MAPPINGS_NAMESPACE)
            it.sourceNamespace.set("obf")
            it.targetNamespace.set(remapNamespace)

            with(project) {
                it.mappings.from(project.configurations.named(main.sourceSet.mappingsConfigurationName))
            }

            it.input.set(resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::output))
            it.classpath.from(minecraftForgeClasspath)
        }

        mappingClasspath.from(remapPatchedMinecraftIntermediaryJar.flatMap(RemapJar::getArchiveFile))

        main.dependencies { dependencies ->
            val userdev = minecraftVersion.flatMap { minecraftVersion ->
                loaderVersion.flatMap { forgeVersion ->
                    userdevClassifier.orElse("userdev").map { userdev ->
                        "$group:$artifact:${version(minecraftVersion, forgeVersion)}:$userdev"
                    }
                }
            }

            with(project) {
                project.dependencies.addProvider(main.sourceSet.mappingsConfigurationName, userdev)
                project.dependencies.addProvider(main.sourceSet.patchesConfigurationName, userdev)

                minecraftForgeClasspath.dependencies.addAllLater(minecraftVersion.flatMap {
                    val forge = project.extension<MinecraftCodevExtension>().extension<MinecraftCodevForgeExtension>()

                    forge.dependencies(it, project.configurations.getByName(main.sourceSet.patchesConfigurationName))
                })

                project.configurations.named(main.sourceSet.nativesConfigurationName) {
                    val codev = project.extension<MinecraftCodevExtension>()

                    it.dependencies.addAllLater(minecraftVersion.flatMap(codev::nativeDependencies))
                }

                project.dependencies.add(main.sourceSet.mixinsConfigurationName, main.mixins)
                project.dependencies.add(main.sourceSet.accessWidenersConfigurationName, main.accessWideners)

                if (!hasMappings) {
                    project.dependencies.add(main.sourceSet.mappingsConfigurationName, project.files(downloadClientMappings.flatMap(DownloadMinecraftMappings::output)))
                }

                project.tasks.named(main.sourceSet.processResourcesTaskName, ProcessResources::class.java) {
                    it.from(project.configurations.named(main.sourceSet.mixinsConfigurationName))
                }
            }

            dependencies.implementation(main.dependency)

            project.configurations.named(dependencies.implementation.configurationName) {
                it.extendsFrom(minecraftForgeClasspath)
            }
        }

        data.dependencies {
            with(project) {
                project.dependencies.add(data.sourceSet.mixinsConfigurationName, data.mixins)
                project.dependencies.add(data.sourceSet.accessWidenersConfigurationName, data.accessWideners)
            }

            it.implementation(data.dependency)
        }

        main.runConfiguration {
            it.defaults.extension<ForgeRunsDefaultsContainer>().server(minecraftVersion) { serverConfig ->
                with(project) {
                    serverConfig.mixinConfigs.set(project.configurations.named(main.sourceSet.mixinsConfigurationName).map { it.files.map(File::getName) })
                }
            }
        }

        /*
        client.runConfiguration {
            it.defaults.extension<ForgeRunsDefaultsContainer>().client(minecraftVersion) { clientConfig ->
                clientConfig.mixinConfigs.set(project.configurations.named(main.sourceSet.get().mixinsConfigurationName).map { it.files.map(File::getName) })
            }
        }
        */

        data.runConfiguration {
            it.defaults.extension<ForgeRunsDefaultsContainer>().data(minecraftVersion) { datagen ->
                with(project) {
                    datagen.mixinConfigs.set(project.configurations.named(main.sourceSet.mixinsConfigurationName).map { it.files.map(File::getName) })
                }

                datagen.modId.set(project.extension<ClocheExtension>().metadata.modId)

                datagen.outputDirectory.set(datagenDirectory)
            }
        }
    }

    override fun getName() = name

    protected open fun version(minecraftVersion: String, loaderVersion: String) =
        "$minecraftVersion-$loaderVersion"

    override fun dependencies(action: Action<ClocheDependencyHandler>) = main.dependencies(action)
    override fun runConfiguration(action: Action<MinecraftRunConfigurationBuilder>) = main.runConfiguration(action)

    override fun data(action: Action<RunnableCompilation>?) {
        action?.execute(data)
    }

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
