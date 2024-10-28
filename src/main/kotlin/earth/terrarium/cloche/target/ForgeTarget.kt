package earth.terrarium.cloche.target

import earth.terrarium.cloche.*
import earth.terrarium.cloche.ClochePlugin.Companion.STUB_DEPENDENCY
import net.msrandom.minecraftcodev.core.VERSION_MANIFEST_URL
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.getCacheDirectory
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.forge.MinecraftCodevForgePlugin
import net.msrandom.minecraftcodev.forge.MinecraftForgeComponentMetadataRule
import net.msrandom.minecraftcodev.forge.mappings.mcpConfigDependency
import net.msrandom.minecraftcodev.forge.patchesConfigurationName
import net.msrandom.minecraftcodev.forge.runs.ForgeRunsDefaultsContainer
import net.msrandom.minecraftcodev.forge.task.ResolvePatchedMinecraft
import net.msrandom.minecraftcodev.mixins.mixinsConfigurationName
import net.msrandom.minecraftcodev.remapper.mappingsConfigurationName
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

internal abstract class ForgeTarget(private val name: String) : MinecraftTargetInternal, MinecraftNoClientTarget {
    private val resolvePatchedMinecraft = project.tasks.register(project.addSetupTask(lowerCamelCaseGradleName("resolve", name, "patchedMinecraft")), ResolvePatchedMinecraft::class.java) {
        it.version.set(minecraftVersion)
    }

    final override val main: TargetCompilation

    final override val client: SimpleRunnable = run {
        project.objects.newInstance(SimpleRunnable::class.java, ClochePlugin.CLIENT_COMPILATION_NAME)
    }

    final override val data: TargetCompilation

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

    override val loaderAttributeName get() = ClocheExtension::forge.name
    override val commonType get() = ClocheExtension::forge.name

    override val compilations: List<RunnableCompilationInternal>
        get() = listOf(main, data)

    init {
        val patchedMinecraftConfiguration = MinecraftConfiguration(this, name, resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::output), name)

        main = run {
            project.objects.newInstance(
                TargetCompilation::class.java,
                SourceSet.MAIN_SOURCE_SET_NAME,
                this,
                patchedMinecraftConfiguration,
                PublicationVariant.Joined,
                java.util.Optional.empty<TargetCompilation>(),
                Side.UNKNOWN,
                remapNamespace,
            )
        }

        data = run {
            project.objects.newInstance(
                TargetCompilation::class.java,
                ClochePlugin.DATA_COMPILATION_NAME,
                this,
                patchedMinecraftConfiguration,
                PublicationVariant.Data,
                java.util.Optional.of(main),
                Side.UNKNOWN,
                remapNamespace,
            )
        }

        project.dependencies.add(patchedMinecraftConfiguration.configurationName, project.dependencies.platform(STUB_DEPENDENCY))

        main.dependencies { dependencies ->
            val userdev = minecraftVersion.flatMap { minecraftVersion ->
                loaderVersion.flatMap { forgeVersion ->
                    userdevClassifier.orElse("userdev").map { userdev ->
                        "$group:$artifact:${version(minecraftVersion, forgeVersion)}:$userdev"
                    }
                }
            }

            project.dependencies.addProvider(dependencies.sourceSet.patchesConfigurationName, userdev)

            resolvePatchedMinecraft.configure {
                it.patches.from(project.configurations.named(dependencies.sourceSet.patchesConfigurationName))
            }

            project.dependencies.addProvider(
                dependencies.sourceSet.mappingsConfigurationName,
                mcpConfigDependency(project, project.configurations.getByName(dependencies.sourceSet.patchesConfigurationName)),
            )

            project.configurations.named(dependencies.sourceSet.compileClasspathConfigurationName) {
                patchedMinecraftConfiguration.useIn(it)
            }

            project.configurations.named(dependencies.sourceSet.runtimeClasspathConfigurationName) {
                patchedMinecraftConfiguration.useIn(it)
            }

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
                        patchedMinecraftConfiguration.targetMinecraftAttribute,
                        TARGET_MINECRAFT_ATTRIBUTE,
                        patchedMinecraftConfiguration.targetMinecraftAttribute,
                    )
                }
            }

            if (!hasMappings) {
                val providers = buildList { MappingsBuilder(project, this).official() }

                addMappings(providers)
            }

            project.tasks.named(dependencies.sourceSet.processResourcesTaskName, ProcessResources::class.java) {
                it.from(project.configurations.named(dependencies.sourceSet.mixinsConfigurationName))
            }

            project.tasks.withType(ExtractNatives::class.java).named(dependencies.sourceSet.extractNativesTaskName) {
                it.version.set(minecraftVersion)
            }

            project.tasks.withType(DownloadAssets::class.java).named(dependencies.sourceSet.downloadAssetsTaskName) {
                it.version.set(minecraftVersion)
            }
        }

        data.dependencies { dependencies ->
            project.tasks.withType(DownloadAssets::class.java).named(dependencies.sourceSet.downloadAssetsTaskName) {
                it.version.set(minecraftVersion)
            }
        }

        main.runConfiguration {
            it.defaults.extension<ForgeRunsDefaultsContainer>().server(minecraftVersion) { serverConfig ->
                with(project) {
                    it.sourceSet(main.sourceSet)
                    serverConfig.mixinConfigs.from(project.configurations.named(main.sourceSet.mixinsConfigurationName))
                }
            }
        }

        client.runConfiguration {
            it.defaults.extension<ForgeRunsDefaultsContainer>().client(minecraftVersion) { clientConfig ->
                with(project) {
                    it.sourceSet(main.sourceSet)

                    clientConfig.patches.from(project.configurations.named(main.sourceSet.patchesConfigurationName))
                    clientConfig.mixinConfigs.from(project.configurations.named(main.sourceSet.mixinsConfigurationName))
                }
            }
        }

        data.runConfiguration {
            it.defaults.extension<ForgeRunsDefaultsContainer>().data(minecraftVersion) { datagen ->
                with(project) {
                    it.sourceSet(data.sourceSet)

                    datagen.patches.from(project.configurations.named(main.sourceSet.patchesConfigurationName))
                    datagen.mixinConfigs.from(project.configurations.named(main.sourceSet.mixinsConfigurationName))
                }

                datagen.modId.set(project.extension<ClocheExtension>().metadata.modId)

                datagen.outputDirectory.set(datagenDirectory)
            }
        }
    }

    override fun getName() = name

    protected open fun version(minecraftVersion: String, loaderVersion: String) =
        "$minecraftVersion-$loaderVersion"

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
            project.dependencies.addProvider(sourceSet.mappingsConfigurationName, minecraftVersion.map { mapping(it, this@ForgeTarget.name) })
        }
    }
}
