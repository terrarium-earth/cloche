package earth.terrarium.cloche.target.forge

import earth.terrarium.cloche.ClochePlugin
import earth.terrarium.cloche.FORGE
import earth.terrarium.cloche.PublicationSide
import earth.terrarium.cloche.api.attributes.IncludeTransformationStateAttribute
import earth.terrarium.cloche.api.metadata.CommonMetadata
import earth.terrarium.cloche.api.metadata.ForgeMetadata
import earth.terrarium.cloche.api.target.ForgeLikeTarget
import earth.terrarium.cloche.target.CompilationInternal
import earth.terrarium.cloche.target.LazyConfigurableInternal
import earth.terrarium.cloche.target.MinecraftTargetInternal
import earth.terrarium.cloche.target.TargetCompilationInfo
import earth.terrarium.cloche.target.lazyConfigurable
import earth.terrarium.cloche.target.localImplementationConfigurationName
import earth.terrarium.cloche.tasks.data.MetadataFileProvider
import net.msrandom.minecraftcodev.core.MinecraftOperatingSystemAttribute
import net.msrandom.minecraftcodev.core.operatingSystemName
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.forge.patchesConfigurationName
import net.msrandom.minecraftcodev.forge.task.GenerateAccessTransformer
import net.msrandom.minecraftcodev.forge.task.JarJar
import net.msrandom.minecraftcodev.forge.task.ResolvePatchedMinecraft
import net.msrandom.minecraftcodev.remapper.MinecraftCodevRemapperPlugin
import net.msrandom.minecraftcodev.remapper.mappingsConfigurationName
import net.msrandom.minecraftcodev.remapper.task.LoadMappings
import net.msrandom.minecraftcodev.remapper.task.RemapTask
import net.peanuuutz.tomlkt.TomlTable
import org.gradle.api.Action
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.attributes.Usage
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.register
import javax.inject.Inject

@Suppress("UnstableApiUsage")
internal abstract class ForgeLikeTargetImpl @Inject constructor(name: String) :
    MinecraftTargetInternal(name), ForgeLikeTarget {
    internal val minecraftLibrariesConfiguration: Configuration =
        project.configurations.create(lowerCamelCaseGradleName(featureName, "minecraftLibraries")) {
            isCanBeConsumed = false

            attributes.attribute(
                MinecraftOperatingSystemAttribute.attribute,
                objectFactory.named(operatingSystemName()),
            )

            attributes.attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.JAVA_RUNTIME))
        }

    private val universal = project.configurations.create(lowerCamelCaseGradleName(featureName, "forgeUniversal")) {
        isCanBeConsumed = false
    }

    private val sideProvider = project.provider {
        PublicationSide.Joined
    }

    internal val resolvePatchedMinecraft = project.tasks.register<ResolvePatchedMinecraft>(
        lowerCamelCaseGradleName("resolve", featureName, "patchedMinecraft"),
    ) {
        group = "minecraft-resolution"

        minecraftVersion.set(this@ForgeLikeTargetImpl.minecraftVersion)
        universal.from(this@ForgeLikeTargetImpl.universal)

        output.set(output(minecraftRemapNamespace.map {
            it.ifEmpty {
                MinecraftCodevRemapperPlugin.NAMED_MAPPINGS_NAMESPACE
            }
        }))
    }

    override val finalJar
        get() = main.includeJarTask!!

    private val remapTask = project.tasks.register<RemapTask>(
        lowerCamelCaseGradleName("remap", name, "minecraftNamed"),
    ) {
        group = "minecraft-transforms"

        inputFile.set(resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::output))

        classpath.from(minecraftLibrariesConfiguration)

        mappings.set(loadMappingsTask.flatMap(LoadMappings::output))

        sourceNamespace.set(minecraftRemapNamespace)

        outputFile.set(output(project.provider { MinecraftCodevRemapperPlugin.NAMED_MAPPINGS_NAMESPACE }))
    }

    private val minecraftFile = minecraftRemapNamespace.flatMap {
        if (it.isEmpty()) {
            resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::output)
        } else {
            remapTask.flatMap(RemapTask::outputFile)
        }
    }

    final override val main: ForgeCompilationImpl = objectFactory.newInstance<ForgeCompilationImpl>(
        TargetCompilationInfo(
            SourceSet.MAIN_SOURCE_SET_NAME,
            this,
            project.files(resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::output)),
            minecraftFile,
            project.provider { emptyList() },
            sideProvider,
            data = false,
            test = false,
            includeState = IncludeTransformationStateAttribute.None,
            includeJarType = JarJar::class.java,
        ),
    )

    final override val data: LazyConfigurableInternal<ForgeCompilationImpl> = project.lazyConfigurable {
        val data = objectFactory.newInstance<ForgeCompilationImpl>(
            TargetCompilationInfo(
                ClochePlugin.DATA_COMPILATION_NAME,
                this,
                project.files(resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::output)),
                minecraftFile,
                project.provider { emptyList<RegularFile>() },
                sideProvider,
                data = true,
                test = false,
                includeState = IncludeTransformationStateAttribute.None,
                includeJarType = JarJar::class.java,
            ),
        )

        data.dependencies {
            runtimeOnly.add(project.files(resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::clientExtra)))
        }

        data
    }

    final override val test: LazyConfigurableInternal<ForgeCompilationImpl> = project.lazyConfigurable {
        val data = objectFactory.newInstance<ForgeCompilationImpl>(
            TargetCompilationInfo(
                SourceSet.TEST_SOURCE_SET_NAME,
                this,
                project.files(resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::output)),
                minecraftFile,
                project.provider { emptyList() },
                sideProvider,
                data = false,
                test = true,
                includeState = IncludeTransformationStateAttribute.None,
                includeJarType = JarJar::class.java,
            ),
        )

        data.dependencies {
            runtimeOnly.add(project.files(resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::clientExtra)))
        }

        data
    }

    protected abstract val providerFactory: ProviderFactory
        @Inject get

    override val hasSeparateClient: Provider<Boolean> =
        providerFactory.provider { false }

    override val runs = objectFactory.newInstance<ForgeRunConfigurations<out ForgeLikeTargetImpl>>(this)

    abstract val group: String
    abstract val artifact: String

    override val commonType get() = FORGE

    override val metadata = objectFactory.newInstance<ForgeMetadata>(this)
    override val legacyClasspath = main.legacyClasspath

    init {
        project.dependencies.add(minecraftLibrariesConfiguration.name, forgeDependency {
            capabilities {
                requireFeature("dependencies")
            }
        })

        project.dependencies.add(universal.name, forgeDependency {})

        metadata.modLoader.set("javafml")

        minecraftLibrariesConfiguration.shouldResolveConsistentlyWith(project.configurations.getByName(sourceSet.runtimeClasspathConfigurationName))

        project.configurations.named(sourceSet.localImplementationConfigurationName) {
            extendsFrom(minecraftLibrariesConfiguration)
        }

        project.dependencies.add(
            sourceSet.runtimeOnlyConfigurationName,
            project.files(resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::clientExtra)),
        )

        val userdev = forgeDependency {
            capabilities {
                requireFeature("moddev-bundle")
            }
        }

        project.dependencies.addProvider(sourceSet.patchesConfigurationName, userdev)

        resolvePatchedMinecraft.configure {
            patches.from(project.configurations.named(sourceSet.patchesConfigurationName))
            libraries.from(minecraftLibrariesConfiguration)
        }

        project.dependencies.addProvider(sourceSet.mappingsConfigurationName, userdev)

        registerMappings()
    }

    private fun output(suffix: Provider<String>) = suffix.flatMap { suffix ->
        outputDirectory.zip(minecraftVersion.zip(loaderVersion) { a, b -> "$a-$b" }) { dir, version ->
            dir.file("$loaderName-$version-$suffix.jar")
        }
    }

    internal fun loaderVersionRange(version: String): CommonMetadata.VersionRange =
        objectFactory.newInstance<CommonMetadata.VersionRange>().apply {
            start.set(version)
        }

    private fun forgeDependency(configure: ExternalModuleDependency.() -> Unit): Provider<ExternalModuleDependency> =
        minecraftVersion.flatMap { minecraftVersion ->
            loaderVersion.map { forgeVersion ->
                module(group, artifact, null).apply {
                    version {
                        strictly(version(minecraftVersion, forgeVersion))
                    }

                    configure()
                }
            }
        }

    protected abstract fun version(minecraftVersion: String, loaderVersion: String): String

    override fun registerAccessWidenerMergeTask(compilation: CompilationInternal) {
        if (compilation.isTest) {
            return
        }

        val task = project.tasks.register<GenerateAccessTransformer>(
            lowerCamelCaseGradleName("generate", name, compilation.featureName, "accessTransformer"),
        ) {
            input.from(accessWideners)

            val output = project.layout.buildDirectory.dir("generated")
                .map { directory ->
                    directory.dir("accessTransformers").dir(compilation.sourceSet.name).file("accesstransformer.cfg")
                }

            this.output.set(output)
        }

        project.tasks.named<Jar>(compilation.sourceSet.jarTaskName) {
            from(task.flatMap(GenerateAccessTransformer::output)) {
                into("META-INF")
            }
        }
    }

    override fun addAnnotationProcessors(compilation: CompilationInternal) {
        project.configurations.named(compilation.sourceSet.annotationProcessorConfigurationName) {
            extendsFrom(minecraftLibrariesConfiguration)
        }

        // TODO Add forge mixin arguments
    }

    override fun onClientIncluded(action: () -> Unit) {
        action()
    }

    override fun withMetadataToml(action: Action<MetadataFileProvider<TomlTable>>) = main.withMetadataToml(action)
}
