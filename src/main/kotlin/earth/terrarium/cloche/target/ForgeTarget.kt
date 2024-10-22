package earth.terrarium.cloche.target

import earth.terrarium.cloche.*
import net.msrandom.minecraftcodev.accesswidener.accessWidenersConfigurationName
import net.msrandom.minecraftcodev.core.VERSION_MANIFEST_URL
import net.msrandom.minecraftcodev.core.task.ResolveMinecraftMappings
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.getCacheDirectory
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.forge.*
import net.msrandom.minecraftcodev.forge.mappings.mcpConfigDependency
import net.msrandom.minecraftcodev.forge.runs.ForgeRunsDefaultsContainer
import net.msrandom.minecraftcodev.forge.task.ResolvePatchedMinecraft
import net.msrandom.minecraftcodev.mixins.mixinsConfigurationName
import net.msrandom.minecraftcodev.remapper.mappingsConfigurationName
import net.msrandom.minecraftcodev.runs.MinecraftRunConfigurationBuilder
import net.msrandom.minecraftcodev.runs.downloadAssetsTaskName
import net.msrandom.minecraftcodev.runs.extractNativesTaskName
import net.msrandom.minecraftcodev.runs.task.DownloadAssets
import net.msrandom.minecraftcodev.runs.task.ExtractNatives
import org.gradle.api.Action
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.SourceSet
import org.gradle.language.jvm.tasks.ProcessResources
import org.spongepowered.asm.mixin.MixinEnvironment.Side
import java.io.File

abstract class ForgeTarget(private val name: String) : MinecraftTarget {
    private val minecraftForgeClasspath = project.configurations.create(lowerCamelCaseGradleName(name, "minecraftForgeClasspath")) {
        it.isCanBeConsumed = false

        it.attributes { attributes ->
            attributes.attribute(
                TARGET_MINECRAFT_ATTRIBUTE,
                this@ForgeTarget.name,
            )
        }
    }

    private val resolveClientMappings = project.tasks.register(lowerCamelCaseGradleName("resolve", name, "clientMappings"), ResolveMinecraftMappings::class.java) {
        it.version.set(minecraftVersion)
        it.server.set(false)
    }

    private val resolvePatchedMinecraft = project.tasks.register(project.addSetupTask(lowerCamelCaseGradleName("resolve", name, "patchedMinecraft")), ResolvePatchedMinecraft::class.java) {
        it.version.set(minecraftVersion)
        it.clientMappings.set(resolveClientMappings.flatMap(ResolveMinecraftMappings::output))
    }

    final override val main: TargetCompilation

    final override val client: SimpleRunnable = run {
        project.objects.newInstance(SimpleRunnable::class.java, ClochePlugin.CLIENT_COMPILATION_NAME)
    }

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
        main = run {
            project.objects.newInstance(
                TargetCompilation::class.java,
                SourceSet.MAIN_SOURCE_SET_NAME,
                this,
                resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::output),
                java.util.Optional.empty<TargetCompilation>(),
                Side.UNKNOWN,
                remapNamespace,
                minecraftForgeClasspath,
            )
        }

        data = run {
            project.objects.newInstance(
                TargetCompilation::class.java,
                ClochePlugin.DATA_COMPILATION_NAME,
                this,
                resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::output),
                java.util.Optional.of(main),
                Side.UNKNOWN,
                remapNamespace,
                minecraftForgeClasspath,
            )
        }

        project.afterEvaluate {
            resolvePatchedMinecraft.configure {
                with(project) {
                    it.patches.from(project.configurations.named(main.sourceSet.patchesConfigurationName))
                }
            }
        }

        main.dependencies { dependencies ->
            project.dependencies.add(minecraftForgeClasspath.name, project.dependencies.platform(ClochePlugin.STUB_DEPENDENCY))

            val userdev = minecraftVersion.flatMap { minecraftVersion ->
                loaderVersion.flatMap { forgeVersion ->
                    userdevClassifier.orElse("userdev").map { userdev ->
                        "$group:$artifact:${version(minecraftVersion, forgeVersion)}:$userdev"
                    }
                }
            }

            with(project) {
                project.configurations.named(main.sourceSet.compileClasspathConfigurationName) {
                    minecraftForgeClasspath.shouldResolveConsistentlyWith(it)
                }

                project.configurations.named(main.sourceSet.runtimeClasspathConfigurationName) {
                    minecraftForgeClasspath.shouldResolveConsistentlyWith(it)
                }

                project.dependencies.addProvider(main.sourceSet.patchesConfigurationName, userdev)

                project.dependencies.addProvider(
                    main.sourceSet.mappingsConfigurationName,
                    mcpConfigDependency(project, project.configurations.getByName(main.sourceSet.patchesConfigurationName)),
                )

                project.dependencies.components { components ->
                    components.withModule(ClochePlugin.STUB_MODULE, MinecraftForgeComponentMetadataRule::class.java) {
                        it.params(
                            getCacheDirectory(project),
                            minecraftVersion,
                            project.provider { VERSION_MANIFEST_URL },
                            project.provider { project.gradle.startParameter.isOffline },
                            this@ForgeTarget.group,
                            this@ForgeTarget.artifact,
                            minecraftVersion.flatMap { minecraftVersion ->
                                loaderVersion.map { forgeVersion ->
                                    version(minecraftVersion, forgeVersion)
                                }
                            },
                            userdevClassifier.orElse("userdev"),
                            this@ForgeTarget.name,
                            TARGET_MINECRAFT_ATTRIBUTE,
                            this@ForgeTarget.name,
                        )
                    }
                }

                project.dependencies.add(main.sourceSet.mixinsConfigurationName, main.mixins)
                project.dependencies.add(main.sourceSet.accessWidenersConfigurationName, main.accessWideners)

                if (!hasMappings) {
                    project.dependencies.add(main.sourceSet.mappingsConfigurationName, project.files(resolveClientMappings.flatMap(ResolveMinecraftMappings::output)))
                }

                project.tasks.named(main.sourceSet.processResourcesTaskName, ProcessResources::class.java) {
                    it.from(project.configurations.named(main.sourceSet.mixinsConfigurationName))
                }

                project.tasks.withType(ExtractNatives::class.java).named(main.sourceSet.extractNativesTaskName) {
                    it.version.set(minecraftVersion)
                }

                project.tasks.withType(DownloadAssets::class.java).named(main.sourceSet.downloadAssetsTaskName) {
                    it.version.set(minecraftVersion)
                }
            }

            project.configurations.named(dependencies.implementation.configurationName) {
                it.extendsFrom(minecraftForgeClasspath)
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
            it.defaults.extension<ForgeRunsDefaultsContainer>().server(minecraftVersion) { serverConfig ->
                with(project) {
                    it.sourceSet(main.sourceSet)
                    serverConfig.mixinConfigs.set(project.configurations.named(main.sourceSet.mixinsConfigurationName).map { it.files.map(File::getName) })
                }
            }
        }

        client.runConfiguration {
            it.defaults.extension<ForgeRunsDefaultsContainer>().client(minecraftVersion) { clientConfig ->
                with(project) {
                    it.sourceSet(main.sourceSet)
                    clientConfig.mixinConfigs.set(project.configurations.named(main.sourceSet.mixinsConfigurationName).map { it.files.map(File::getName) })
                }
            }
        }

        data.runConfiguration {
            it.defaults.extension<ForgeRunsDefaultsContainer>().data(minecraftVersion) { datagen ->
                with(project) {
                    it.sourceSet(data.sourceSet)

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

    fun client(action: Action<Runnable>) {
        action.execute(client)
    }

    override fun mappings(action: Action<MappingsBuilder>) {
        hasMappings = true

        val mappings = mutableListOf<MappingDependencyProvider>()

        action.execute(MappingsBuilder(project, mappings))

        main.dependencies {
            with(project) {
                for (mapping in mappings) {
                    project.dependencies.addProvider(main.sourceSet.mappingsConfigurationName, minecraftVersion.map { mapping(it, this@ForgeTarget.name) })
                }
            }
        }
    }
}
