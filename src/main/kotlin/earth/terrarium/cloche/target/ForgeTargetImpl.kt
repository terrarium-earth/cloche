package earth.terrarium.cloche.target

import earth.terrarium.cloche.*
import earth.terrarium.cloche.metadata.ForgeMetadata
import earth.terrarium.cloche.tasks.GenerateFabricModJson
import earth.terrarium.cloche.tasks.GenerateForgeModsToml
import net.msrandom.minecraftcodev.accesswidener.accessWidenersConfigurationName
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.forge.MinecraftCodevForgePlugin
import net.msrandom.minecraftcodev.forge.patchesConfigurationName
import net.msrandom.minecraftcodev.forge.runs.ForgeRunsDefaultsContainer
import net.msrandom.minecraftcodev.forge.task.GenerateAccessTransformer
import net.msrandom.minecraftcodev.forge.task.ResolvePatchedMinecraft
import net.msrandom.minecraftcodev.mixins.mixinsConfigurationName
import net.msrandom.minecraftcodev.remapper.mappingsConfigurationName
import net.msrandom.minecraftcodev.remapper.task.LoadMappings
import net.msrandom.minecraftcodev.remapper.task.RemapTask
import net.msrandom.minecraftcodev.runs.downloadAssetsTaskName
import net.msrandom.minecraftcodev.runs.extractNativesTaskName
import net.msrandom.minecraftcodev.runs.task.DownloadAssets
import net.msrandom.minecraftcodev.runs.task.ExtractNatives
import org.gradle.api.Action
import org.gradle.api.DomainObjectCollection
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.attributes.Usage
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.nativeplatform.OperatingSystemFamily
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.spongepowered.asm.mixin.MixinEnvironment.Side
import javax.inject.Inject

internal abstract class ForgeTargetImpl @Inject constructor(private val name: String) : MinecraftTargetInternal<ForgeMetadata>,
    ForgeTarget {
    protected val minecraftLibrariesConfiguration: Configuration =
        project.configurations.create(lowerCamelCaseGradleName(featureName, "minecraftLibraries")) {
            it.isCanBeConsumed = false

            it.attributes.attribute(
                OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE,
                project.objects.named(
                    OperatingSystemFamily::class.java,
                    DefaultNativePlatform.host().operatingSystem.toFamilyName(),
                ),
            )

            it.attributes.attribute(
                Usage.USAGE_ATTRIBUTE,
                project.objects.named(
                    Usage::class.java,
                    Usage.JAVA_RUNTIME,
                )
            )
        }

    private val universal = project.configurations.create(lowerCamelCaseGradleName(featureName, "forgeUniversal")) {
        it.isCanBeConsumed = false
    }

    private val resolvePatchedMinecraft = project.tasks.register(
        lowerCamelCaseGradleName("resolve", featureName, "patchedMinecraft"),
        ResolvePatchedMinecraft::class.java
    ) {
        it.group = "minecraft-resolution"

        it.version.set(minecraftVersion)
        it.universal.from(universal)

        if (this !is NeoForgeTargetImpl) {
            it.output.set(
                project.layout.file(
                    minecraftVersion.flatMap { mc ->
                        loaderVersion.map { forge ->
                            it.temporaryDir.resolve("forge-$mc-$forge.jar")
                        }
                    }
                )
            )
        }
    }

    final override lateinit var main: TargetCompilation

    final override var client: SimpleRunnable? = null

    final override var data: RunnableTargetCompilation? = null

    override val dependsOn: DomainObjectCollection<CommonTarget> =
        project.objects.domainObjectSet(CommonTarget::class.java)

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

    override val loaderName get() = FORGE
    override val commonType get() = FORGE

    override val hasIncludedClient
        get() = true

    override val metadata: ForgeMetadata = project.objects.newInstance(ForgeMetadata::class.java)

    override val compilations: DomainObjectCollection<TargetCompilation> =
        project.objects.domainObjectSet(TargetCompilation::class.java)

    override val runnables: DomainObjectCollection<RunnableInternal> =
        project.objects.domainObjectSet(RunnableInternal::class.java)

    abstract val mappingProviders: ListProperty<MappingDependencyProvider>
        @Internal get

    override val loadMappings: TaskProvider<LoadMappings> = project.tasks.register(lowerCamelCaseGradleName("load", name, "mappings"), LoadMappings::class.java) {
        it.mappings.from(project.configurations.named(main.sourceSet.mappingsConfigurationName))

        it.javaExecutable.set(project.javaExecutableFor(minecraftVersion, it.cacheParameters))
    }

    override val mappingsBuildDependenciesHolder: Configuration =
        project.configurations.detachedConfiguration(project.dependencies.create(project.files().builtBy(loadMappings)))

    private val remapTask = project.tasks.register(
        lowerCamelCaseGradleName("remap", name, "minecraftNamed"),
        RemapTask::class.java,
    ) {
        it.group = "minecraft-transforms"

        it.inputFile.set(resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::output))

        it.classpath.from(minecraftLibrariesConfiguration)

        it.mappings.set(loadMappings.flatMap(LoadMappings::output))

        it.sourceNamespace.set(remapNamespace)
    }

    protected val generateModsToml: TaskProvider<GenerateForgeModsToml> = project.tasks.register(lowerCamelCaseGradleName("generate", featureName, "modsToml"), GenerateForgeModsToml::class.java) {
        it.loaderDependencyVersion.set(loaderVersion.map {
            it.substringBefore('.')
        })

        it.output.set(metadataDirectory.map {
            it.file("mods.toml")
        })

        it.commonMetadata.set(project.extension<ClocheExtension>().metadata)
        it.targetMetadata.set(metadata)

        it.loaderName.set(loaderName)
    }

    private val minecraftFile = remapNamespace.flatMap {
        if (it.isEmpty()) {
            resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::output)
        } else {
            remapTask.flatMap(RemapTask::outputFile)
        }
    }

    private var isSingleTarget = false

    init {
        project.dependencies.add(minecraftLibrariesConfiguration.name, forgeDependency {
            capabilities {
                it.requireFeature("dependencies")
            }
        })

        project.dependencies.add(universal.name, forgeDependency {})
    }

    private fun forgeDependency(configure: ExternalModuleDependency.() -> Unit): Provider<Dependency> =
        minecraftVersion.flatMap { minecraftVersion ->
            loaderVersion.map { forgeVersion ->
                project.dependencies.create("$group:$artifact").apply {
                    this as ExternalModuleDependency

                    version { version ->
                        version.strictly(version(minecraftVersion, forgeVersion))
                    }

                    configure()
                }
            }
        }

    override fun initialize(isSingleTarget: Boolean) {
        this.isSingleTarget = isSingleTarget

        main = run {
            project.objects.newInstance(
                TargetCompilation::class.java,
                SourceSet.MAIN_SOURCE_SET_NAME,
                this,
                resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::output),
                minecraftFile,
                project.files(),
                PublicationVariant.Joined,
                Side.UNKNOWN,
                isSingleTarget,
                remapNamespace,
            )
        }

        compilations.add(main)

        project.tasks.named(main.sourceSet.processResourcesTaskName, ProcessResources::class.java) {
            it.from(metadataDirectory) {
                it.into("META-INF")
            }

            it.dependsOn(generateModsToml)
        }

        main.dependencies { dependencies ->
            minecraftLibrariesConfiguration.shouldResolveConsistentlyWith(project.configurations.getByName(dependencies.sourceSet.runtimeClasspathConfigurationName))

            project.configurations.named(dependencies.sourceSet.implementationConfigurationName) {
                it.extendsFrom(minecraftLibrariesConfiguration)
            }

            val userdev = forgeDependency {
                capabilities {
                    it.requireFeature("moddev-bundle")
                }
            }

            project.dependencies.add(
                dependencies.sourceSet.runtimeOnlyConfigurationName,
                project.files(resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::clientExtra)),
            )

            project.dependencies.addProvider(dependencies.sourceSet.patchesConfigurationName, userdev)

            resolvePatchedMinecraft.configure {
                it.patches.from(project.configurations.named(dependencies.sourceSet.patchesConfigurationName))
                it.libraries.from(minecraftLibrariesConfiguration)
            }

            project.dependencies.addProvider(dependencies.sourceSet.mappingsConfigurationName, userdev)

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

            project.tasks.named(dependencies.sourceSet.extractNativesTaskName, ExtractNatives::class.java) {
                it.version.set(minecraftVersion)
            }

            project.tasks.named(dependencies.sourceSet.downloadAssetsTaskName, DownloadAssets::class.java) {
                it.version.set(minecraftVersion)
            }
        }
    }

    override fun getName() = name

    protected open fun version(minecraftVersion: String, loaderVersion: String) =
        "$minecraftVersion-$loaderVersion"

    override fun registerAccessWidenerMergeTask(compilation: CompilationInternal) {
        val namePart = compilation.name.takeUnless { it == SourceSet.MAIN_SOURCE_SET_NAME }

        val task = project.tasks.register(
            lowerCamelCaseGradleName("generate", name, namePart, "accessTransformer"),
            GenerateAccessTransformer::class.java
        ) {
            it.input.from(project.configurations.named(sourceSet.accessWidenersConfigurationName))

            val output = project.layout.buildDirectory.dir("generated")
                .map { directory ->
                    directory.dir("accessTransformers").dir(compilation.sourceSet.name).file("accesstransformer.cfg")
                }

            it.output.set(output)
        }

        project.tasks.named(compilation.sourceSet.jarTaskName, Jar::class.java) {
            it.from(task.flatMap(GenerateAccessTransformer::output)) {
                it.into("META-INF")
            }
        }
    }

    override fun addJarInjects(compilation: CompilationInternal) {
        project.tasks.named(compilation.sourceSet.jarTaskName, Jar::class.java) {
            it.manifest {
                it.attributes["MixinConfigs"] = object {
                    override fun toString(): String {
                        return project.configurations.getByName(compilation.sourceSet.mixinsConfigurationName)
                            .joinToString(",") { it.name }
                    }
                }
            }
        }
    }

    override fun createData(): RunnableTargetCompilation {
        val data = run {
            project.objects.newInstance(
                RunnableTargetCompilation::class.java,
                ClochePlugin.DATA_COMPILATION_NAME,
                this,
                resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::output),
                minecraftFile,
                project.files(),
                PublicationVariant.Data,
                Side.UNKNOWN,
                isSingleTarget,
                remapNamespace,
            )
        }

        data.dependencies { dependencies ->
            project.tasks.named(dependencies.sourceSet.downloadAssetsTaskName, DownloadAssets::class.java) {
                it.version.set(minecraftVersion)
            }

            project.dependencies.add(
                dependencies.sourceSet.runtimeOnlyConfigurationName,
                project.files(resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::clientExtra)),
            )
        }

        data.runConfiguration { datagen ->
            datagen.sourceSet(data.sourceSet)

            datagen.defaults {
                it.extension<ForgeRunsDefaultsContainer>().data(minecraftVersion) { datagen ->
                    datagen.patches.from(project.configurations.named(main.sourceSet.patchesConfigurationName))
                    datagen.mixinConfigs.from(project.configurations.named(data.sourceSet.mixinsConfigurationName))

                    datagen.modId.set(project.extension<ClocheExtension>().metadata.modId)

                    datagen.outputDirectory.set(datagenDirectory)

                    datagen.neoforgeClasspathHandling.set(this is NeoforgeTarget)

                    datagen.additionalIncludedSourceSets.add(main.sourceSet)
                }
            }
        }

        return data
    }

    override fun server(action: Action<Runnable>?) {
        if (server == null) {
            val server = project.objects.newInstance(SimpleRunnable::class.java, ClochePlugin.SERVER_RUNNABLE_NAME)

            server.runConfiguration {
                it.sourceSet(main.sourceSet)

                it.defaults {
                    it.extension<ForgeRunsDefaultsContainer>().server(minecraftVersion) { serverConfig ->
                        serverConfig.patches.from(project.configurations.named(main.sourceSet.patchesConfigurationName))
                        serverConfig.mixinConfigs.from(project.configurations.named(main.sourceSet.mixinsConfigurationName))

                        serverConfig.modId.set(project.extension<ClocheExtension>().metadata.modId)

                        serverConfig.neoforgeClasspathHandling.set(this is NeoforgeTarget)
                    }
                }
            }

            runnables.add(server)

            this.server = server
        }

        action?.execute(server!!)
    }

    override fun client(action: Action<Runnable>?) {
        if (client == null) {
            client = project.objects.newInstance(SimpleRunnable::class.java, ClochePlugin.CLIENT_COMPILATION_NAME)

            client!!.runConfiguration {
                it.sourceSet(main.sourceSet)

                it.defaults {
                    it.extension<ForgeRunsDefaultsContainer>().client(minecraftVersion) { clientConfig ->
                        clientConfig.patches.from(project.configurations.named(main.sourceSet.patchesConfigurationName))
                        clientConfig.mixinConfigs.from(project.configurations.named(main.sourceSet.mixinsConfigurationName))

                        clientConfig.modId.set(project.extension<ClocheExtension>().metadata.modId)

                        clientConfig.neoforgeClasspathHandling.set(this is NeoforgeTarget)
                    }
                }
            }

            runnables.add(client)
        }

        action?.execute(client!!)
    }

    override fun mappings(action: Action<MappingsBuilder>) {
        hasMappings.set(true)

        val mappings = mutableListOf<MappingDependencyProvider>()

        action.execute(MappingsBuilder(project, mappings))

        addMappings(mappings)
    }

    private fun addMappings(providers: List<MappingDependencyProvider>) {
        mappingProviders.addAll(providers)
    }
}
