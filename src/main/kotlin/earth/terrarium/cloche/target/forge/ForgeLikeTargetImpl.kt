package earth.terrarium.cloche.target.forge

import earth.terrarium.cloche.ClocheExtension
import earth.terrarium.cloche.ClochePlugin
import earth.terrarium.cloche.FORGE
import earth.terrarium.cloche.PublicationSide
import earth.terrarium.cloche.api.metadata.ForgeMetadata
import earth.terrarium.cloche.api.metadata.ModMetadata
import earth.terrarium.cloche.api.target.ForgeLikeTarget
import earth.terrarium.cloche.target.CompilationInternal
import earth.terrarium.cloche.target.LazyConfigurableInternal
import earth.terrarium.cloche.target.MinecraftTargetInternal
import earth.terrarium.cloche.target.TargetCompilation
import earth.terrarium.cloche.target.lazyConfigurable
import earth.terrarium.cloche.tasks.GenerateForgeModsToml
import net.msrandom.minecraftcodev.core.MinecraftOperatingSystemAttribute
import net.msrandom.minecraftcodev.core.operatingSystemName
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.forge.patchesConfigurationName
import net.msrandom.minecraftcodev.forge.task.GenerateAccessTransformer
import net.msrandom.minecraftcodev.forge.task.GenerateLegacyClasspath
import net.msrandom.minecraftcodev.forge.task.JarJar
import net.msrandom.minecraftcodev.forge.task.ResolvePatchedMinecraft
import net.msrandom.minecraftcodev.remapper.mappingsConfigurationName
import net.msrandom.minecraftcodev.remapper.task.LoadMappings
import net.msrandom.minecraftcodev.remapper.task.RemapTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.attributes.Usage
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.spongepowered.asm.mixin.MixinEnvironment
import javax.inject.Inject

@Suppress("UnstableApiUsage")
internal abstract class ForgeLikeTargetImpl @Inject constructor(name: String) :
    MinecraftTargetInternal(name), ForgeLikeTarget {
    protected val minecraftLibrariesConfiguration: Configuration =
        project.configurations.create(lowerCamelCaseGradleName(featureName, "minecraftLibraries")) {
            it.isCanBeConsumed = false

            it.attributes.attribute(
                MinecraftOperatingSystemAttribute.attribute,
                project.objects.named(
                    MinecraftOperatingSystemAttribute::class.java,
                    operatingSystemName(),
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

    protected val resolvePatchedMinecraft: TaskProvider<ResolvePatchedMinecraft> = project.tasks.register(
        lowerCamelCaseGradleName("resolve", featureName, "patchedMinecraft"),
        ResolvePatchedMinecraft::class.java
    ) {
        it.group = "minecraft-resolution"

        it.version.set(minecraftVersion)
        it.universal.from(universal)
    }

    internal abstract val generateLegacyClasspath: TaskProvider<GenerateLegacyClasspath>
    internal abstract val generateLegacyDataClasspath: TaskProvider<GenerateLegacyClasspath>
    internal abstract val generateLegacyTestClasspath: TaskProvider<GenerateLegacyClasspath>

    final override lateinit var main: TargetCompilation

    final override val data: LazyConfigurableInternal<TargetCompilation> = project.lazyConfigurable {
        val data = run {
            project.objects.newInstance(
                TargetCompilation::class.java,
                ClochePlugin.DATA_COMPILATION_NAME,
                this,
                project.files(resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::output)),
                minecraftFile,
                project.provider { emptyList<RegularFile>() },
                PublicationSide.Joined,
                MixinEnvironment.Side.UNKNOWN,
                isSingleTarget,
            )
        }

        data.dependencies { dependencies ->
            dependencies.runtimeOnly.add(project.files(resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::clientExtra)))
        }

        data
    }

    final override val test: LazyConfigurableInternal<TargetCompilation> = project.lazyConfigurable {
        val data = run {
            project.objects.newInstance(
                TargetCompilation::class.java,
                SourceSet.TEST_SOURCE_SET_NAME,
                this,
                project.files(resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::output)),
                minecraftFile,
                project.provider { emptyList<RegularFile>() },
                PublicationSide.Joined,
                MixinEnvironment.Side.UNKNOWN,
                isSingleTarget,
            )
        }

        data.dependencies { dependencies ->
            dependencies.runtimeOnly.add(project.files(resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::clientExtra)))
        }

        data
    }

    override lateinit var includeJarTask: TaskProvider<JarJar>

    protected abstract val providerFactory: ProviderFactory
        @Inject get

    override val runs: ForgeRunConfigurations = project.objects.newInstance(ForgeRunConfigurations::class.java, this)

    abstract val group: String
    abstract val artifact: String

    override val commonType get() = FORGE

    override val metadata: ForgeMetadata = project.objects.newInstance(ForgeMetadata::class.java)

    private val remapTask = project.tasks.register(
        lowerCamelCaseGradleName("remap", name, "minecraftNamed"),
        RemapTask::class.java,
    ) {
        it.group = "minecraft-transforms"

        it.inputFile.set(resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::output))

        it.classpath.from(minecraftLibrariesConfiguration)

        it.mappings.set(loadMappingsTask.flatMap(LoadMappings::output))

        it.sourceNamespace.set(minecraftRemapNamespace)
    }

    protected val generateModsToml: TaskProvider<GenerateForgeModsToml> = project.tasks.register(
        lowerCamelCaseGradleName("generate", featureName, "modsToml"),
        GenerateForgeModsToml::class.java
    ) {
        it.output.set(metadataDirectory.map {
            it.dir("META-INF").file("mods.toml")
        })

        it.commonMetadata.set(project.extension<ClocheExtension>().metadata)
        it.targetMetadata.set(metadata)

        it.loaderName.set(loaderName)
    }

    private val minecraftFile = minecraftRemapNamespace.flatMap {
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

    protected fun loaderVersionRange(version: String): ModMetadata.VersionRange = objectFactory.newInstance(ModMetadata.VersionRange::class.java).apply {
        start.set(version)
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

        main = project.objects.newInstance(
            TargetCompilation::class.java,
            SourceSet.MAIN_SOURCE_SET_NAME,
            this,
            project.files(resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::output)),
            minecraftFile,
            project.provider { emptyList<RegularFile>() },
            PublicationSide.Joined,
            MixinEnvironment.Side.UNKNOWN,
            isSingleTarget,
        )

        includeJarTask = project.tasks.register(
            lowerCamelCaseGradleName(sourceSet.takeUnless(SourceSet::isMain)?.name, "jarJar"),
            JarJar::class.java,
        ) {
            val jar = project.tasks.named(sourceSet.jarTaskName, Jar::class.java)
            val remapJar = main.remapJarTask

            if (!isSingleTarget) {
                it.archiveClassifier.set(classifierName)
            }

            it.destinationDirectory.set(project.extension<BasePluginExtension>().distsDirectory)

            it.input.set(modRemapNamespace.flatMap {
                val jarTask = if (it.isEmpty()) {
                    jar
                } else {
                    remapJar
                }

                jarTask.flatMap(Jar::getArchiveFile)
            })

            it.includesConfiguration.set(includeConfiguration)
        }

        sourceSet.resources.srcDir(metadataDirectory)

        project.tasks.named(sourceSet.processResourcesTaskName) {
            it.dependsOn(generateModsToml)
        }

        minecraftLibrariesConfiguration.shouldResolveConsistentlyWith(project.configurations.getByName(sourceSet.runtimeClasspathConfigurationName))

        project.configurations.named(sourceSet.implementationConfigurationName) {
            it.extendsFrom(minecraftLibrariesConfiguration)
        }

        val userdev = forgeDependency {
            capabilities {
                it.requireFeature("moddev-bundle")
            }
        }

        project.dependencies.add(
            sourceSet.runtimeOnlyConfigurationName,
            project.files(resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::clientExtra)),
        )

        project.dependencies.addProvider(sourceSet.patchesConfigurationName, userdev)

        resolvePatchedMinecraft.configure {
            it.patches.from(project.configurations.named(sourceSet.patchesConfigurationName))
            it.libraries.from(minecraftLibrariesConfiguration)
        }

        project.dependencies.addProvider(sourceSet.mappingsConfigurationName, userdev)

        registerMappings()
    }

    protected abstract fun version(minecraftVersion: String, loaderVersion: String): String

    override fun registerAccessWidenerMergeTask(compilation: CompilationInternal) {
        val task = project.tasks.register(
            lowerCamelCaseGradleName("generate", name, compilation.featureName, "accessTransformer"),
            GenerateAccessTransformer::class.java
        ) {
            it.input.from(accessWideners)

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

    override fun addJarInjects(compilation: CompilationInternal) {}

    override fun onClientIncluded(action: () -> Unit) {
        action()
    }
}
