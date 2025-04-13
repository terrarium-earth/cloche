package earth.terrarium.cloche.target.fabric

import earth.terrarium.cloche.ClocheExtension
import earth.terrarium.cloche.ClochePlugin
import earth.terrarium.cloche.FABRIC
import earth.terrarium.cloche.PublicationSide
import earth.terrarium.cloche.api.metadata.FabricMetadata
import earth.terrarium.cloche.api.target.FabricTarget
import earth.terrarium.cloche.target.*
import earth.terrarium.cloche.tasks.GenerateFabricModJson
import earth.terrarium.cloche.tasks.GenerateModJsonJarsEntry
import net.msrandom.minecraftcodev.accesswidener.AccessWiden
import net.msrandom.minecraftcodev.core.MinecraftComponentMetadataRule
import net.msrandom.minecraftcodev.core.MinecraftOperatingSystemAttribute
import net.msrandom.minecraftcodev.core.VERSION_MANIFEST_URL
import net.msrandom.minecraftcodev.core.operatingSystemName
import net.msrandom.minecraftcodev.core.task.ResolveMinecraftClient
import net.msrandom.minecraftcodev.core.task.ResolveMinecraftCommon
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.getGlobalCacheDirectory
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.fabric.MinecraftCodevFabricPlugin
import net.msrandom.minecraftcodev.fabric.task.JarInJar
import net.msrandom.minecraftcodev.fabric.task.MergeAccessWideners
import net.msrandom.minecraftcodev.fabric.task.ProcessIncludedJars
import net.msrandom.minecraftcodev.mixins.mixinsConfigurationName
import net.msrandom.minecraftcodev.remapper.task.LoadMappings
import net.msrandom.minecraftcodev.remapper.task.RemapTask
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Zip
import org.gradle.jvm.tasks.Jar
import javax.inject.Inject

@Suppress("UnstableApiUsage")
internal abstract class FabricTargetImpl @Inject constructor(name: String) :
    MinecraftTargetInternal(name),
    FabricTarget {
    private val commonLibrariesConfiguration =
        project.configurations.create(lowerCamelCaseGradleName(featureName, "commonMinecraftLibraries")) {
            it.isCanBeConsumed = false

            it.attributes.attribute(
                MinecraftOperatingSystemAttribute.attribute,
                project.objects.named(
                    MinecraftOperatingSystemAttribute::class.java,
                    operatingSystemName(),
                ),
            )
        }

    private val clientLibrariesConfiguration =
        project.configurations.create(lowerCamelCaseGradleName(featureName, "clientMinecraftLibraries")) {
            it.isCanBeConsumed = false

            it.attributes.attribute(
                MinecraftOperatingSystemAttribute.attribute,
                project.objects.named(
                    MinecraftOperatingSystemAttribute::class.java,
                    operatingSystemName(),
                ),
            )
        }

    private val resolveCommonMinecraft =
        project.tasks.register(
            lowerCamelCaseGradleName("resolve", name, "common"),
            ResolveMinecraftCommon::class.java
        ) {
            it.group = "minecraft-resolution"

            it.version.set(minecraftVersion)
        }

    private val resolveClientMinecraft =
        project.tasks.register(
            lowerCamelCaseGradleName("resolve", name, "client"),
            ResolveMinecraftClient::class.java
        ) {
            it.group = "minecraft-resolution"

            it.version.set(minecraftVersion)
        }

    private val remapCommonMinecraftIntermediary =
        project.tasks.register(
            lowerCamelCaseGradleName(
                "remap",
                name,
                "commonMinecraft",
                MinecraftCodevFabricPlugin.INTERMEDIARY_MAPPINGS_NAMESPACE,
            ),
            RemapTask::class.java,
        ) {
            it.group = "minecraft-transforms"

            it.inputFile.set(resolveCommonMinecraft.flatMap(ResolveMinecraftCommon::output))
            it.sourceNamespace.set("obf")
            it.targetNamespace.set(minecraftRemapNamespace)

            it.classpath.from(commonLibrariesConfiguration)

            it.mappings.set(loadMappingsTask.flatMap(LoadMappings::output))
        }

    private val remapClientMinecraftIntermediary =
        project.tasks.register(
            lowerCamelCaseGradleName(
                "remap",
                name,
                "clientMinecraft",
                MinecraftCodevFabricPlugin.INTERMEDIARY_MAPPINGS_NAMESPACE,
            ),
            RemapTask::class.java,
        ) {
            it.group = "minecraft-transforms"

            it.inputFile.set(resolveClientMinecraft.flatMap(ResolveMinecraftClient::output))
            it.sourceNamespace.set("obf")
            it.targetNamespace.set(minecraftRemapNamespace)

            it.classpath.from(commonLibrariesConfiguration)
            it.classpath.from(clientLibrariesConfiguration)
            it.classpath.from(resolveCommonMinecraft.flatMap(ResolveMinecraftCommon::output))

            it.mappings.set(loadMappingsTask.flatMap(LoadMappings::output))
        }

    private val remapCommon = project.tasks.register(
        lowerCamelCaseGradleName("remap", featureName, "commonMinecraftNamed"),
        RemapTask::class.java
    ) {
        it.group = "minecraft-transforms"

        it.inputFile.set(remapCommonMinecraftIntermediary.flatMap(RemapTask::outputFile))

        it.classpath.from(commonLibrariesConfiguration)

        it.mappings.set(loadMappingsTask.flatMap(LoadMappings::output))

        it.sourceNamespace.set(minecraftRemapNamespace)
    }

    private val remapClient = project.tasks.register(
        lowerCamelCaseGradleName("remap", featureName, "clientMinecraftNamed"),
        RemapTask::class.java
    ) {
        it.group = "minecraft-transforms"

        it.inputFile.set(remapClientMinecraftIntermediary.flatMap(RemapTask::outputFile))

        it.classpath.from(commonLibrariesConfiguration)
        it.classpath.from(clientLibrariesConfiguration)
        it.classpath.from(remapCommonMinecraftIntermediary.flatMap(RemapTask::outputFile))

        it.mappings.set(loadMappingsTask.flatMap(LoadMappings::output))

        it.sourceNamespace.set(minecraftRemapNamespace)
    }

    private val generateModJson = project.tasks.register(
        lowerCamelCaseGradleName("generate", featureName, "ModJson"),
        GenerateFabricModJson::class.java
    ) {
        it.loaderDependencyVersion.set(loaderVersion.map {
            it.substringBefore('.')
        })

        it.output.set(metadataDirectory.map {
            it.file("fabric.mod.json")
        })

        it.commonMetadata.set(project.extension<ClocheExtension>().metadata)
        it.targetMetadata.set(metadata)
        it.mixinConfigs.from(project.configurations.named(sourceSet.mixinsConfigurationName))
    }

    private val generateMappingsArtifact = project.tasks.register(
        lowerCamelCaseGradleName("generate", featureName, "mappingsArtifact"),
        Zip::class.java,
    ) {
        it.destinationDirectory.set(it.temporaryDir)
        it.archiveBaseName.set("$featureName-mappings")
        it.archiveVersion.set(null as String?)

        it.from(loadMappingsTask.flatMap(LoadMappings::output)) {
            it.into("mappings")
        }
    }

    lateinit var mergeJarTask: TaskProvider<Jar>
    lateinit var processIncludedJarsTask: TaskProvider<ProcessIncludedJars>
    lateinit var generateJarsModJsonEntry: TaskProvider<GenerateModJsonJarsEntry>
    override lateinit var includeJarTask: TaskProvider<JarInJar>

    private var hasIncludedClientValue = false
    private val hasIncludedClient = project.provider { hasIncludedClientValue }
    private val includedClientActions = mutableListOf<() -> Unit>()

    final override lateinit var main: TargetCompilation

    final override val client: LazyConfigurableInternal<FabricClientSecondarySourceSets> = project.lazyConfigurable {
        if (hasIncludedClientValue) {
            throw InvalidUserCodeException("Used `client()` in $name after previously using `includedClient()`")
        }

        val client =
            project.objects.newInstance(
                FabricClientSecondarySourceSets::class.java,
                ClochePlugin.CLIENT_COMPILATION_NAME,
                this,
                project.files(
                    remapCommonMinecraftIntermediary.flatMap(RemapTask::outputFile),
                    remapClientMinecraftIntermediary.flatMap(RemapTask::outputFile),
                ),
                remapClient.flatMap(RemapTask::outputFile),
                main.finalMinecraftFile.map(::listOf),
                PublicationSide.Client,
                isSingleTarget,
            )

        clientLibrariesConfiguration.shouldResolveConsistentlyWith(project.configurations.getByName(client.sourceSet.runtimeClasspathConfigurationName))

        project.configurations.named(client.sourceSet.compileClasspathConfigurationName) {
            it.extendsFrom(clientLibrariesConfiguration)
        }

        project.configurations.named(client.sourceSet.runtimeClasspathConfigurationName) {
            it.extendsFrom(clientLibrariesConfiguration)
        }

        generateModJson.configure {
            it.clientMixinConfigs.from(project.configurations.named(client.sourceSet.mixinsConfigurationName))
        }

        client
    }

    final override val data: LazyConfigurableInternal<TargetCompilation> = project.lazyConfigurable {
        registerCommonCompilation(ClochePlugin.DATA_COMPILATION_NAME)
    }

    final override val test: LazyConfigurableInternal<TargetCompilation> = project.lazyConfigurable {
        registerCommonCompilation(SourceSet.TEST_SOURCE_SET_NAME)
    }

    override val metadata: FabricMetadata = project.objects.newInstance(FabricMetadata::class.java)

    protected abstract val providerFactory: ProviderFactory
        @Inject get

    final override val minecraftRemapNamespace: Provider<String>
        get() = providerFactory.provider { MinecraftCodevFabricPlugin.INTERMEDIARY_MAPPINGS_NAMESPACE }

    override val runs: FabricRunConfigurations = project.objects.newInstance(FabricRunConfigurations::class.java, this)

    override val commonType get() = FABRIC

    private val clientTargetMinecraftName = lowerCamelCaseGradleName(featureName, "client")

    private var isSingleTarget = false

    init {
        val commonStub =
            project.dependencies.enforcedPlatform(ClochePlugin.STUB_DEPENDENCY) as ExternalModuleDependency

        val clientStub =
            project.dependencies.enforcedPlatform(ClochePlugin.STUB_DEPENDENCY) as ExternalModuleDependency

        project.dependencies.add(commonLibrariesConfiguration.name, commonStub.apply {
            capabilities {
                it.requireCapability("net.msrandom:$featureName")
            }
        })

        project.dependencies.add(clientLibrariesConfiguration.name, clientStub.apply {
            capabilities {
                it.requireCapability("net.msrandom:$clientTargetMinecraftName")
            }
        })
    }

    private fun registerCommonCompilation(name: String): TargetCompilation {
        fun <T> clientAlternative(normal: Provider<T>, client: Provider<T>) =
            hasIncludedClient.flatMap {
                if (it) {
                    client
                } else {
                    normal
                }
            }

        fun list(vararg values: Provider<RegularFile>) = project.objects.listProperty(RegularFile::class.java).apply {
            for (value in values) {
                add(value)
            }
        }

        val commonTask = registerCompilationTransformations(
            this,
            lowerCamelCaseGradleName(name.takeUnless { it == SourceSet.MAIN_SOURCE_SET_NAME }, "common"),
            compilationSourceSet(this, name, isSingleTarget),
            remapCommon.flatMap(RemapTask::outputFile),
            project.provider { emptyList() },
        ).first

        commonTask.configure {
            it.accessWideners.from(accessWideners)
        }

        val commonClasspath = list(commonTask.flatMap(AccessWiden::outputFile))

        // TODO Once client splitting is done, we might not always need the client Jar
        val intermediateClasspath = project.files(
            remapCommonMinecraftIntermediary.flatMap(RemapTask::outputFile),
            remapClientMinecraftIntermediary.flatMap(RemapTask::outputFile),
        )

        val remappedFile = clientAlternative(
            normal = remapCommon.flatMap(RemapTask::outputFile),
            client = remapClient.flatMap(RemapTask::outputFile),
        )

        val extraClasspath = if (name == SourceSet.MAIN_SOURCE_SET_NAME) {
            clientAlternative(
                normal = project.provider { emptyList() },
                client = commonClasspath,
            )
        } else {
            clientAlternative(
                normal = main.finalMinecraftFile.map(::listOf),
                client = commonClasspath.zip(main.finalMinecraftFile, List<RegularFile>::plus),
            )
        }

        return project.objects.newInstance(
            TargetCompilation::class.java,
            name,
            this,
            intermediateClasspath,
            remappedFile,
            extraClasspath,
            PublicationSide.Common,
            isSingleTarget,
        )
    }

    override fun initialize(isSingleTarget: Boolean) {
        this.isSingleTarget = isSingleTarget

        main = registerCommonCompilation(SourceSet.MAIN_SOURCE_SET_NAME)

        project.dependencies.add(
            main.sourceSet.runtimeOnlyConfigurationName,
            project.files(generateMappingsArtifact.flatMap(Zip::getArchiveFile)),
        )

        mergeJarTask = project.tasks.register(
            lowerCamelCaseGradleName(sourceSet.takeUnless(SourceSet::isMain)?.name, "mergeJar"),
            Jar::class.java,
        ) {
            if (isSingleTarget) {
                it.archiveClassifier.set("merged")
            } else {
                it.archiveClassifier.set("$classifierName-merged")
            }

            it.from(project.zipTree(main.remapJarTask.flatMap(Jar::getArchiveFile)))
            it.from(project.zipTree(client.value.flatMap(TargetCompilation::remapJarTask).flatMap(Jar::getArchiveFile)))

            // Needed cause manifest will be duplicated
            it.duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }

        processIncludedJarsTask = project.tasks.register(
            lowerCamelCaseGradleName(sourceSet.takeUnless(SourceSet::isMain)?.name, "processIncludedJars"),
            ProcessIncludedJars::class.java
        ) {
            it.includeConfiguration.set(includeConfiguration)
        }

        generateJarsModJsonEntry = project.tasks.register(
            lowerCamelCaseGradleName(sourceSet.takeUnless(SourceSet::isMain)?.name, "modJsonJarsEntry"),
            GenerateModJsonJarsEntry::class.java
        ) {
            it.jar.set(hasIncludedClient.flatMap {
                val jarTask = if (it) {
                    main.remapJarTask
                } else {
                    mergeJarTask
                }

                jarTask.flatMap(Jar::getArchiveFile)
            })
            it.includedJars.from(processIncludedJarsTask.map { it.outputDirectory.asFileTree })
        }

        includeJarTask = project.tasks.register(
            lowerCamelCaseGradleName(sourceSet.takeUnless(SourceSet::isMain)?.name, "jarInJar"),
            JarInJar::class.java,
        ) {
            it.dependsOn(generateJarsModJsonEntry)

            if (!isSingleTarget) {
                it.archiveClassifier.set(classifierName)
            }

            it.destinationDirectory.set(project.extension<BasePluginExtension>().distsDirectory)

            it.input.set(generateJarsModJsonEntry.flatMap(GenerateModJsonJarsEntry::jar))

            it.includedFiles.from(processIncludedJarsTask.map { it.outputDirectory.asFileTree })
        }

        sourceSet.resources.srcDir(metadataDirectory)

        project.tasks.named(sourceSet.processResourcesTaskName) {
            it.dependsOn(generateModJson)
        }

        main.dependencies { dependencies ->
            commonLibrariesConfiguration.shouldResolveConsistentlyWith(project.configurations.getByName(sourceSet.runtimeClasspathConfigurationName))

            project.configurations.named(sourceSet.implementationConfigurationName) {
                it.extendsFrom(commonLibrariesConfiguration)
            }

            dependencies.implementation.add(loaderVersion.map { version -> project.dependencies.create("net.fabricmc:fabric-loader:$version") })

            mappings.fabricIntermediary()

            registerMappings()

            // afterEvaluate needed because of the component rules using providers
            project.afterEvaluate { project ->
                project.dependencies.components { components ->
                    components.withModule(
                        ClochePlugin.STUB_MODULE,
                        MinecraftComponentMetadataRule::class.java,
                    ) {
                        it.params(
                            getGlobalCacheDirectory(project),
                            listOf(minecraftVersion.get()),
                            VERSION_MANIFEST_URL,
                            project.gradle.startParameter.isOffline,
                            featureName,
                            clientTargetMinecraftName,
                        )
                    }
                }
            }
        }
    }

    override fun registerAccessWidenerMergeTask(compilation: CompilationInternal) {
        val modId = project.extension<ClocheExtension>().metadata.modId

        val task = project.tasks.register(
            lowerCamelCaseGradleName("merge", name, compilation.featureName, "accessWideners"),
            MergeAccessWideners::class.java
        ) {
            it.input.from(accessWideners)
            it.accessWidenerName.set(project.extension<ClocheExtension>().metadata.modId)

            val output = modId.zip(project.layout.buildDirectory.dir("generated")) { modId, directory ->
                directory.dir("mergedAccessWideners").dir(compilation.sourceSet.name).file("$modId.accessWidener")
            }

            it.output.set(output)
        }

        project.tasks.named(compilation.sourceSet.jarTaskName, Jar::class.java) {
            it.from(task.flatMap(MergeAccessWideners::output))
        }
    }

    override fun includedClient() {
        if (client.isConfigured) {
            throw InvalidUserCodeException("Used 'includedClient' in target $name after already configuring client compilation")
        }

        hasIncludedClientValue = true

        clientLibrariesConfiguration.shouldResolveConsistentlyWith(project.configurations.getByName(sourceSet.runtimeClasspathConfigurationName))

        project.configurations.named(sourceSet.compileClasspathConfigurationName) {
            it.extendsFrom(clientLibrariesConfiguration)
        }

        project.configurations.named(sourceSet.runtimeClasspathConfigurationName) {
            it.extendsFrom(clientLibrariesConfiguration)
        }

        for (action in includedClientActions) {
            action()
        }

        includedClientActions.clear()
    }

    override fun onClientIncluded(action: () -> Unit) {
        if (hasIncludedClientValue) {
            action()
        } else {
            includedClientActions.add(action)
        }
    }
}
