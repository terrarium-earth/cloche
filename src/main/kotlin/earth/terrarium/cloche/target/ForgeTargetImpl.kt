package earth.terrarium.cloche.target

import earth.terrarium.cloche.*
import earth.terrarium.cloche.ClochePlugin.Companion.STUB_DEPENDENCY
import net.msrandom.minecraftcodev.core.VERSION_MANIFEST_URL
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.getGlobalCacheDirectory
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.forge.MinecraftCodevForgePlugin
import net.msrandom.minecraftcodev.forge.MinecraftForgeComponentMetadataRule
import net.msrandom.minecraftcodev.forge.UserdevPath
import net.msrandom.minecraftcodev.forge.mappings.mcpConfigDependency
import net.msrandom.minecraftcodev.forge.mappings.mcpConfigExtraRemappingFiles
import net.msrandom.minecraftcodev.forge.patchesConfigurationName
import net.msrandom.minecraftcodev.forge.runs.ForgeRunsDefaultsContainer
import net.msrandom.minecraftcodev.forge.task.ResolvePatchedMinecraft
import net.msrandom.minecraftcodev.mixins.mixinsConfigurationName
import net.msrandom.minecraftcodev.remapper.mappingsConfigurationName
import net.msrandom.minecraftcodev.remapper.task.RemapTask
import net.msrandom.minecraftcodev.runs.downloadAssetsTaskName
import net.msrandom.minecraftcodev.runs.extractNativesTaskName
import net.msrandom.minecraftcodev.runs.task.DownloadAssets
import net.msrandom.minecraftcodev.runs.task.ExtractNatives
import org.gradle.api.Action
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.SourceSet
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.nativeplatform.OperatingSystemFamily
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.spongepowered.asm.mixin.MixinEnvironment.Side
import javax.inject.Inject

internal abstract class ForgeTargetImpl @Inject constructor(private val name: String) : MinecraftTargetInternal,
    ForgeTarget {
    private val minecraftLibrariesConfiguration =
        project.configurations.create(lowerCamelCaseGradleName(featureName, "minecraftLibraries")) {
            it.isCanBeConsumed = false

            it.attributes.attribute(
                OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE,
                project.objects.named(
                    OperatingSystemFamily::class.java,
                    DefaultNativePlatform.host().operatingSystem.toFamilyName()
                ),
            )
        }

    private val resolvePatchedMinecraft = project.tasks.register(
        project.addSetupTask(lowerCamelCaseGradleName("resolve", featureName, "patchedMinecraft")),
        ResolvePatchedMinecraft::class.java
    ) {
        it.version.set(minecraftVersion)

        if (this !is NeoforgeTarget) {
            it.output.set(
                project.layout.file(
                    minecraftVersion.flatMap { mc ->
                        loaderVersion.map { forge ->
                            it.temporaryDir.resolve("forge-$mc-$forge.jar")
                        }
                    })
            )
        }
    }

    final override lateinit var main: TargetCompilation

    final override val client: SimpleRunnable = run {
        project.objects.newInstance(SimpleRunnable::class.java, ClochePlugin.CLIENT_COMPILATION_NAME)
    }

    final override lateinit var data: TargetCompilation

    protected var hasMappings: Property<Boolean> = project.objects.property(Boolean::class.javaObjectType).apply {
        convention(false)
    }

    protected abstract val providerFactory: ProviderFactory
        @Inject get

    override val remapNamespace: Provider<String>
        get() = providerFactory.provider { MinecraftCodevForgePlugin.SRG_MAPPINGS_NAMESPACE }

    open val group
        @Internal
        get() = "net.minecraftforge"

    open val artifact
        @Internal
        get() = "forge"

    override val loaderAttributeName get() = FORGE
    override val commonType get() = FORGE

    override val compilations = project.objects.domainObjectSet(RunnableCompilationInternal::class.java)
    override val runnables = project.objects.domainObjectSet(RunnableInternal::class.java)

    abstract val mappingProviders: ListProperty<MappingDependencyProvider>
        @Internal get

    init {
        val stub = project.dependencies.enforcedPlatform(STUB_DEPENDENCY) as ExternalModuleDependency

        stub.capabilities {
            it.requireCapability("net.msrandom:$featureName")
        }

        project.dependencies.add(minecraftLibrariesConfiguration.name, stub)
    }

    override fun initialize(isSingleTarget: Boolean) {
        val remapTask = project.tasks.register(
            lowerCamelCaseGradleName("remap", name, "minecraftNamed"),
            RemapTask::class.java,
        ) {
            it.inputFile.set(resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::output))

            it.classpath.from(minecraftLibrariesConfiguration)

            it.mappings.from(project.configurations.named(main.sourceSet.mappingsConfigurationName))

            it.sourceNamespace.set(remapNamespace)

            it.extraFiles.set(
                mcpConfigDependency(
                    project,
                    project.configurations.getByName(main.sourceSet.patchesConfigurationName)
                )
                    .flatMap { file ->
                        mcpConfigExtraRemappingFiles(project, file)
                    },
            )
        }

        val minecraftFile = remapNamespace.flatMap {
            if (it.isEmpty()) {
                resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::output)
            } else {
                remapTask.flatMap(RemapTask::outputFile)
            }
        }

        main = run {
            project.objects.newInstance(
                TargetCompilation::class.java,
                SourceSet.MAIN_SOURCE_SET_NAME,
                this,
                resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::output),
                minecraftFile,
                project.provider { name },
                PublicationVariant.Joined,
                Side.UNKNOWN,
                isSingleTarget,
                remapNamespace,
            )
        }

        data = run {
            project.objects.newInstance(
                TargetCompilation::class.java,
                ClochePlugin.DATA_COMPILATION_NAME,
                this,
                resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::output),
                minecraftFile,
                project.provider { name },
                PublicationVariant.Data,
                Side.UNKNOWN,
                isSingleTarget,
                remapNamespace,
            )
        }

        compilations.add(main)
        compilations.add(data)

        runnables.add(main)
        runnables.add(client)
        runnables.add(data)

        main.dependencies { dependencies ->
            minecraftLibrariesConfiguration.shouldResolveConsistentlyWith(project.configurations.getByName(dependencies.sourceSet.runtimeClasspathConfigurationName))

            project.configurations.named(dependencies.sourceSet.compileClasspathConfigurationName) {
                it.extendsFrom(minecraftLibrariesConfiguration)
            }

            project.configurations.named(dependencies.sourceSet.runtimeClasspathConfigurationName) {
                it.extendsFrom(minecraftLibrariesConfiguration)
            }

            val userdev = minecraftVersion.flatMap { minecraftVersion ->
                loaderVersion.flatMap { forgeVersion ->
                    userdevClassifier.orElse("userdev").map { userdev ->
                        project.dependencies.create("$group:$artifact").apply {
                            this as ExternalModuleDependency

                            version { version ->
                                version.strictly(version(minecraftVersion, forgeVersion))
                            }

                            userdevDependency(userdev)
                        }
                    }
                }
            }

            project.dependencies.add(
                dependencies.sourceSet.runtimeOnlyConfigurationName,
                project.files(resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::clientExtra))
            )

            project.dependencies.addProvider(dependencies.sourceSet.patchesConfigurationName, userdev)

            resolvePatchedMinecraft.configure {
                it.patches.from(project.configurations.named(dependencies.sourceSet.patchesConfigurationName))
                it.libraries.from(minecraftLibrariesConfiguration)
            }

            project.dependencies.addProvider(
                dependencies.sourceSet.mappingsConfigurationName,
                mcpConfigDependency(
                    project,
                    project.configurations.getByName(dependencies.sourceSet.patchesConfigurationName)
                ),
            )

            // afterEvaluate needed because of the component rules using providers
            project.afterEvaluate { project ->
                project.dependencies.components { components ->
                    components.withModule(ClochePlugin.STUB_MODULE, MinecraftForgeComponentMetadataRule::class.java) {
                        it.params(
                            getGlobalCacheDirectory(project),
                            minecraftVersion.get(),
                            VERSION_MANIFEST_URL,
                            project.gradle.startParameter.isOffline,
                            getUserdev(),
                            featureName,
                        )
                    }
                }
            }

            mappingProviders.addAll(hasMappings.map {
                if (it) {
                    emptyList()
                } else {
                    buildList { MappingsBuilder(project, this).official() }
                }
            })

            project.configurations.named(dependencies.sourceSet.mappingsConfigurationName) {
                val mappingDependencyList = minecraftVersion.flatMap { minecraftVersion ->
                    mappingProviders.map { providers ->
                        providers.map { mapping ->
                            mapping(minecraftVersion, featureName)
                        }
                    }
                }

                it.dependencies.addAllLater(mappingDependencyList)
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

    protected open fun ExternalModuleDependency.userdevDependency(userdev: String) {
        artifact {
            it.classifier = userdev
        }
        /*        attributes { attributes ->
                    attributes
                        .attribute(CLASSIFIER_ATTRIBUTE, userdev)
                        .attribute(
                            Category.CATEGORY_ATTRIBUTE,
                            project.objects.named(Category::class.java, Category.LIBRARY)
                        )
                        .attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, Usage.JAVA_API))
                }*/
    }

    protected open fun version(minecraftVersion: String, loaderVersion: String) =
        "$minecraftVersion-$loaderVersion"

    override fun mappings(action: Action<MappingsBuilder>) {
        hasMappings.set(true)

        val mappings = mutableListOf<MappingDependencyProvider>()

        action.execute(MappingsBuilder(project, mappings))

        addMappings(mappings)
    }

    fun getUserdev() = UserdevPath(
        this@ForgeTargetImpl.group,
        this@ForgeTargetImpl.artifact,
        version(minecraftVersion.get(), loaderVersion.get()),
        userdevClassifier.getOrElse("userdev"),
    )

    private fun addMappings(providers: List<MappingDependencyProvider>) {
        mappingProviders.addAll(providers)
    }
}
