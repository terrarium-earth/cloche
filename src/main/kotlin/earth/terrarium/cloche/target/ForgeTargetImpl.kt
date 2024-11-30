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
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.SourceSet
import org.gradle.language.jvm.tasks.ProcessResources
import org.spongepowered.asm.mixin.MixinEnvironment.Side
import javax.inject.Inject

internal abstract class ForgeTargetImpl @Inject constructor(private val name: String) : MinecraftTargetInternal,
    ForgeTarget {
    private val resolvePatchedMinecraft = project.tasks.register(
        project.addSetupTask(lowerCamelCaseGradleName("resolve", name, "patchedMinecraft")),
        ResolvePatchedMinecraft::class.java
    ) {
        it.version.set(minecraftVersion)

        it.output.set(minecraftVersion.flatMap { mc ->
            project.layout.file(
                loaderVersion.map { forge ->
                    it.temporaryDir.resolve("forge-$mc-$forge.jar")
                }
            )
        })
    }

    private val patchedMinecraftConfiguration =
        MinecraftConfiguration(this, name, resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::output), featureName)

    final override lateinit var main: TargetCompilation

    final override val client: SimpleRunnable = run {
        project.objects.newInstance(SimpleRunnable::class.java, ClochePlugin.CLIENT_COMPILATION_NAME)
    }

    final override lateinit var data: TargetCompilation

    protected var hasMappings = false

    override val remapNamespace: String?
        get() = MinecraftCodevForgePlugin.SRG_MAPPINGS_NAMESPACE

    open val group
        @Internal
        get() = "net.minecraftforge"

    open val artifact
        @Internal
        get() = "forge"

    override val loaderAttributeName get() = FORGE
    override val commonType get() = FORGE

    override val compilations: List<RunnableCompilationInternal>
        get() = listOf(main, data)

    override val runnables: List<RunnableInternal>
        get() = compilations

    override fun initialize(isSingleTarget: Boolean) {
        main = run {
            project.objects.newInstance(
                TargetCompilation::class.java,
                SourceSet.MAIN_SOURCE_SET_NAME,
                this,
                { patchedMinecraftConfiguration },
                PublicationVariant.Joined,
                java.util.Optional.empty<TargetCompilation>(),
                Side.UNKNOWN,
                isSingleTarget,
                project.provider { remapNamespace },
                true,
            )
        }

        data = run {
            project.objects.newInstance(
                TargetCompilation::class.java,
                ClochePlugin.DATA_COMPILATION_NAME,
                this,
                { patchedMinecraftConfiguration },
                PublicationVariant.Data,
                java.util.Optional.of(main),
                Side.UNKNOWN,
                isSingleTarget,
                project.provider { remapNamespace },
                true,
            )
        }

        project.dependencies.add(
            patchedMinecraftConfiguration.configurationName,
            project.dependencies.platform(STUB_DEPENDENCY)
        )

        main.dependencies { dependencies ->
            val userdev = minecraftVersion.flatMap { minecraftVersion ->
                loaderVersion.flatMap { forgeVersion ->
                    userdevClassifier.orElse("userdev").map { userdev ->
                        "$group:$artifact:${version(minecraftVersion, forgeVersion)}:$userdev"
                    }
                }
            }

            project.dependencies.add(
                dependencies.sourceSet.runtimeOnlyConfigurationName,
                project.files(resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::clientExtra))
            )
            project.dependencies.addProvider(dependencies.sourceSet.patchesConfigurationName, userdev)

            resolvePatchedMinecraft.configure {
                val configuration =
                    project.configurations.getByName(dependencies.sourceSet.compileClasspathConfigurationName)

                val libraries = configuration.incoming.artifactView { view ->
                    view.componentFilter { id ->
                        id is ModuleComponentIdentifier && id.group == ClochePlugin.STUB_GROUP && id.module == ClochePlugin.STUB_NAME && id.version == ClochePlugin.STUB_VERSION
                    }
                }

                it.patches.from(project.configurations.named(dependencies.sourceSet.patchesConfigurationName))
                it.libraries.from(libraries.files)
            }

            project.dependencies.addProvider(
                dependencies.sourceSet.mappingsConfigurationName,
                mcpConfigDependency(
                    project,
                    project.configurations.getByName(dependencies.sourceSet.patchesConfigurationName)
                ),
            )

            project.afterEvaluate { project ->
                project.dependencies.components { components ->
                    components.withModule(ClochePlugin.STUB_MODULE, MinecraftForgeComponentMetadataRule::class.java) {
                        it.params(
                            getCacheDirectory(project),
                            minecraftVersion.get(),
                            VERSION_MANIFEST_URL,
                            project.gradle.startParameter.isOffline,
                            this@ForgeTargetImpl.group,
                            this@ForgeTargetImpl.artifact,
                            version(minecraftVersion.get(), loaderVersion.get()),
                            userdevClassifier.getOrElse("userdev"),
                            patchedMinecraftConfiguration.targetMinecraftAttribute,
                            MinecraftAttributes.TARGET_MINECRAFT,
                            patchedMinecraftConfiguration.targetMinecraftAttribute,
                        )
                    }
                }
            }

            if (!hasMappings) {
                val providers = buildList { MappingsBuilder(project, this).official() }

                addMappings(providers)
            }

            project.tasks.named(dependencies.sourceSet.processResourcesTaskName, ProcessResources::class.java) {
                it.from(project.configurations.named(dependencies.sourceSet.mixinsConfigurationName))
            }

            project.tasks.named(dependencies.sourceSet.extractNativesTaskName, ExtractNatives::class.java) {
                it.version.set(minecraftVersion)
            }

            project.tasks.named(dependencies.sourceSet.downloadAssetsTaskName, DownloadAssets::class.java) {
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

            it.defaults.extension<ForgeRunsDefaultsContainer>().server(minecraftVersion) { serverConfig ->
                serverConfig.patches.from(project.configurations.named(main.sourceSet.patchesConfigurationName))
                serverConfig.mixinConfigs.from(project.configurations.named(main.sourceSet.mixinsConfigurationName))

                serverConfig.modId.set(project.extension<ClocheExtension>().metadata.modId)
            }
        }

        client.runConfiguration {
            it.sourceSet(main.sourceSet)

            it.defaults.extension<ForgeRunsDefaultsContainer>().client(minecraftVersion) { clientConfig ->
                clientConfig.patches.from(project.configurations.named(main.sourceSet.patchesConfigurationName))
                clientConfig.mixinConfigs.from(project.configurations.named(main.sourceSet.mixinsConfigurationName))

                clientConfig.modId.set(project.extension<ClocheExtension>().metadata.modId)
            }
        }

        data.runConfiguration {
            it.sourceSet(data.sourceSet)

            it.defaults.extension<ForgeRunsDefaultsContainer>().data(minecraftVersion) { datagen ->
                datagen.patches.from(project.configurations.named(main.sourceSet.patchesConfigurationName))
                datagen.mixinConfigs.from(project.configurations.named(data.sourceSet.mixinsConfigurationName))

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
        val sourceSet = main.sourceSet

        for (mapping in providers) {
            project.dependencies.addProvider(
                sourceSet.mappingsConfigurationName,
                minecraftVersion.map { mapping(it, this@ForgeTargetImpl.name) })
        }
    }
}
