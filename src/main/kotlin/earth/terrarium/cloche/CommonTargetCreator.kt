@file:Suppress("UnstableApiUsage")

package earth.terrarium.cloche

import earth.terrarium.cloche.ClochePlugin.Companion.KOTLIN_JVM_PLUGIN_ID
import earth.terrarium.cloche.api.attributes.CommonTargetAttributes
import earth.terrarium.cloche.api.attributes.CompilationAttributes
import earth.terrarium.cloche.api.attributes.ModDistribution
import earth.terrarium.cloche.api.attributes.MinecraftModLoader
import earth.terrarium.cloche.api.attributes.TargetAttributes
import earth.terrarium.cloche.api.target.targetName
import earth.terrarium.cloche.target.*
import earth.terrarium.cloche.target.fabric.FabricTargetImpl
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.stubs.GenerateStubApi
import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME

private const val GENERATE_JAVA_EXPECT_STUBS_OPTION = "generateExpectStubs"

private fun convertClasspath(
    compilation: TargetCompilation<*>,
    configurations: ConfigurationContainer,
    objects: ObjectFactory,
): Provider<List<GenerateStubApi.ResolvedArtifact>> {
    val minecraftFiles =
        compilation.info.extraClasspathFiles.zip(compilation.finalMinecraftFile, List<RegularFile>::plus)
            .map {
                it.map {
                    val artifact = objects.newInstance<GenerateStubApi.ResolvedArtifact>()

                    artifact.file.set(it)

                    artifact
                }
            }

    val artifacts = getNonProjectArtifacts(configurations.named(compilation.sourceSet.compileClasspathConfigurationName)).flatMap {
        it.artifacts.resolvedArtifacts.map { artifacts ->
            artifacts.map {
                val artifact = objects.newInstance<GenerateStubApi.ResolvedArtifact>()

                artifact.id.set(it.id.componentIdentifier)
                artifact.file.set(it.file)

                artifact
            }
        }
    }

    return minecraftFiles.zip(artifacts, List<GenerateStubApi.ResolvedArtifact>::plus)
}

fun SourceSet.commonBucketConfigurationName(configurationName: String) =
    lowerCamelCaseGradleName(name.takeUnless(SourceSet.MAIN_SOURCE_SET_NAME::equals), "common", configurationName)

context(Project)
internal fun createCommonTarget(
    commonTarget: CommonTargetInternal,
    onlyCommonOfType: Provider<Boolean>,
) {
    fun intersection(
        compilation: CommonCompilation,
        compilations: Provider<List<TargetCompilation<*>>>,
    ): FileCollection {
        val name = lowerCamelCaseGradleName("create", commonTarget.targetName, compilation.featureName, "apiStub")

        val generateStub = tasks.register<GenerateStubApi>(name) {
            group = "minecraft-stubs"

            val jarName = compilation.capabilitySuffix.map {
                "${commonTarget.capabilitySuffix}-$it"
            }.orElse(commonTarget.capabilitySuffix)

            apiFileName.set(jarName.map { "$it-api-stub.jar" } )

            val objects = objects
            val configurations = configurations

            val classpaths = compilations.flatMap {
                @Suppress("UNCHECKED_CAST")
                val classpath =
                    objects.listProperty<List<GenerateStubApi.ResolvedArtifact>>()

                for (compilation in it) {
                    classpath.add(convertClasspath(compilation, configurations, objects))
                }

                classpath
            }

            this.classpaths.set(classpaths)

            dependsOn(compilations.map { it.map { it.info.extraClasspathFiles } })
            dependsOn(compilations.map { it.map { it.finalMinecraftFile } })

            dependsOn(files(compilations.map {
                it.map {
                    getRelevantSyncArtifacts(it.sourceSet.compileClasspathConfigurationName)
                }
            }))
        }

        return fileTree(generateStub.flatMap(GenerateStubApi::outputDirectory)).builtBy(generateStub)
    }

    fun addCompilation(
        compilation: CommonCompilation,
        variant: ModDistribution,
        data: Boolean,
        targetCompilations: Provider<List<TargetCompilation<*>>>,
    ) {
        val sourceSet = with(commonTarget) {
            compilation.sourceSet
        }

        createCompilationVariants(
            compilation,
            sourceSet,
            commonTarget.targetName == MinecraftModLoader.common.name || commonTarget.publish
        )

        configureSourceSet(sourceSet, commonTarget, compilation)

        components.named("java") {
            this as AdhocComponentWithVariants

            addVariantsFromConfiguration(configurations.getByName(sourceSet.runtimeElementsConfigurationName)) {
                // Common compilations are not runnable.
                skip()
            }
        }

        val modImplementation =
            configurations.dependencyScope(modConfigurationName(sourceSet.implementationConfigurationName)) {
                addCollectedDependencies(compilation.dependencyHandler.modImplementation)
            }

        val modApi = configurations.dependencyScope(modConfigurationName(sourceSet.apiConfigurationName)) {
            addCollectedDependencies(compilation.dependencyHandler.modApi)
        }

        val modCompileOnly =
            configurations.dependencyScope(modConfigurationName(sourceSet.compileOnlyConfigurationName)) {
                addCollectedDependencies(compilation.dependencyHandler.modCompileOnly)
            }

        val modCompileOnlyApi =
            configurations.dependencyScope(modConfigurationName(sourceSet.compileOnlyApiConfigurationName)) {
                addCollectedDependencies(compilation.dependencyHandler.modCompileOnlyApi)
            }

        val modRuntimeOnly =
            configurations.dependencyScope(modConfigurationName(sourceSet.runtimeOnlyConfigurationName)) {
                addCollectedDependencies(compilation.dependencyHandler.modRuntimeOnly)
            }

        val modLocalRuntime =
            configurations.dependencyScope(modConfigurationName(sourceSet.localRuntimeConfigurationName)) {
                addCollectedDependencies(compilation.dependencyHandler.modLocalRuntime)
            }

        val modLocalImplementation =
            configurations.dependencyScope(modConfigurationName(sourceSet.localImplementationConfigurationName)) {
                addCollectedDependencies(compilation.dependencyHandler.modLocalImplementation)
            }

        configurations.dependencyScope(sourceSet.commonBucketConfigurationName(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME)) {
            addCollectedDependencies(compilation.dependencyHandler.implementation)

            extendsFrom(modImplementation.get())
        }

        configurations.dependencyScope(sourceSet.commonBucketConfigurationName(JavaPlugin.API_CONFIGURATION_NAME)) {
            addCollectedDependencies(compilation.dependencyHandler.api)

            extendsFrom(modApi.get())
        }

        val commonCompileOnly =
            configurations.dependencyScope(sourceSet.commonBucketConfigurationName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME)) {
                addCollectedDependencies(compilation.dependencyHandler.compileOnly)

                extendsFrom(modCompileOnly.get())
            }

        configurations.dependencyScope(sourceSet.commonBucketConfigurationName(JavaPlugin.COMPILE_ONLY_API_CONFIGURATION_NAME)) {
            addCollectedDependencies(compilation.dependencyHandler.compileOnlyApi)

            extendsFrom(modCompileOnlyApi.get())
        }

        configurations.named(sourceSet.runtimeOnlyConfigurationName) {
            addCollectedDependencies(compilation.dependencyHandler.runtimeOnly)

            extendsFrom(modRuntimeOnly.get())
        }

        configurations.named(sourceSet.localRuntimeConfigurationName) {
            addCollectedDependencies(compilation.dependencyHandler.localRuntime)

            extendsFrom(modLocalRuntime.get())
        }

        configurations.named(sourceSet.localImplementationConfigurationName) {
            addCollectedDependencies(compilation.dependencyHandler.localImplementation)

            extendsFrom(modLocalImplementation.get())
        }

        configurations.named(sourceSet.annotationProcessorConfigurationName) {
            addCollectedDependencies(compilation.dependencyHandler.annotationProcessor)
        }

        val intersectionResults = configurations.dependencyScope(
            lowerCamelCaseGradleName(
                commonTarget.featureName,
                compilation.featureName,
                "intersectionResults",
            )
        )

        dependencies.add(
            intersectionResults.name,
            intersection(compilation, targetCompilations),
        )

        compilation.attributes {
            attribute(CompilationAttributes.DISTRIBUTION, variant)
            attribute(CompilationAttributes.CLOCHE_SIDE, variant.legacyName)
            attribute(CompilationAttributes.DATA, data)

            // afterEvaluate needed as the attributes existing(not just their values) depend on configurable info
            afterEvaluate {
                val commonType = commonTarget.commonType.getOrNull()
                val minecraftVersion = commonTarget.minecraftVersion.getOrNull()

                if (commonType != null) {
                    attribute(CommonTargetAttributes.TYPE, commonType)
                }

                if (minecraftVersion != null) {
                    attribute(TargetAttributes.MINECRAFT_VERSION, minecraftVersion)
                    attribute(TargetAttributes.CLOCHE_MINECRAFT_VERSION, minecraftVersion)
                }

                if (!onlyCommonOfType.get() && commonTarget.targetName != MinecraftModLoader.common.name && !commonTarget.publish) {
                    attribute(CommonTargetAttributes.NAME, commonTarget.targetName!!)
                }
            }
        }

        configurations.named(sourceSet.compileClasspathConfigurationName) {
            extendsFrom(intersectionResults.get())

            attributes(compilation::attributes)
            attributes(compilation::resolvableAttributes)
        }

        for (name in listOf(
            sourceSet.apiElementsConfigurationName,
            sourceSet.runtimeElementsConfigurationName,
            sourceSet.javadocElementsConfigurationName,
            sourceSet.sourcesElementsConfigurationName
        )) {
            configurations.findByName(name)?.attributes(compilation::attributes)
        }

        dependencies.add(
            commonCompileOnly.name,
            "net.msrandom:java-expect-actual-annotations:1.0.0"
        )

        dependencies.add(
            sourceSet.annotationProcessorConfigurationName,
            JAVA_EXPECT_ACTUAL_ANNOTATION_PROCESSOR
        )

        tasks.named<JavaCompile>(sourceSet.compileJavaTaskName) {
            options.compilerArgs.add("-A$GENERATE_JAVA_EXPECT_STUBS_OPTION")
        }

        plugins.withId(KOTLIN_JVM_PLUGIN_ID) {
            val pluginClasspathConfigurationName = lowerCamelCaseGradleName(PLUGIN_CLASSPATH_CONFIGURATION_NAME, sourceSet.name)

            dependencies.add(
                pluginClasspathConfigurationName,
                KOTLIN_MULTIPLATFORM_STUB_PLUGIN,
            )

            project.dependencies.add(
                sourceSet.compileOnlyConfigurationName,
                "net.msrandom:kmp-stub-annotations:1.0.0",
            )
        }
    }

    fun add(
        compilation: CommonTopLevelCompilation,
        dataGetter: (MinecraftTargetInternal) -> TargetCompilation<*>,
        testGetter: (MinecraftTargetInternal) -> TargetCompilation<*>,
        variant: ModDistribution,
        targetCompilations: Provider<List<TargetCompilation<*>>>,
    ) {
        addCompilation(compilation, variant, false, targetCompilations)

        if (compilation.name != SourceSet.MAIN_SOURCE_SET_NAME) {
            compilation.addClasspathDependency(commonTarget.main)
        }

        compilation.data.onConfigured {
            addCompilation(
                it,
                variant,
                true,
                commonTarget.dependents.map {
                    it.map { dataGetter(it as MinecraftTargetInternal) }
                },
            )

            it.addClasspathDependency(compilation)
            it.addClasspathDependency(commonTarget.main)

            if (compilation.name != SourceSet.MAIN_SOURCE_SET_NAME) {
                commonTarget.main.data.onConfigured { data ->
                    it.addClasspathDependency(data)
                }
            }
        }

        compilation.test.onConfigured {
            addCompilation(
                it,
                variant,
                false,
                commonTarget.dependents.map {
                    it.map { testGetter(it as MinecraftTargetInternal) }
                },
            )

            it.addClasspathDependency(compilation)
            it.addClasspathDependency(commonTarget.main)

            if (compilation.name != SourceSet.MAIN_SOURCE_SET_NAME) {
                commonTarget.main.test.onConfigured { test ->
                    it.addClasspathDependency(test)
                }
            }
        }
    }

    add(
        commonTarget.main,
        { it.data.internalValue ?: it.main },
        { it.test.internalValue ?: it.main },
        ModDistribution.common,
        commonTarget.dependents.map {
            it.map { (it as MinecraftTargetInternal).main }
        },
    )

    commonTarget.client.onConfigured {
        add(
            it,
            {
                (it as? FabricTargetImpl)?.client?.internalValue?.let {
                    it.data.internalValue ?: it
                }
                    ?: it.data.internalValue
                    ?: it.main
            },
            {
                (it as? FabricTargetImpl)?.client?.internalValue?.let {
                    it.test.internalValue ?: it
                }
                    ?: it.test.internalValue
                    ?: it.main
            },
            ModDistribution.client,
            commonTarget.dependents.map {
                it.map {
                    (it as? FabricTargetImpl)?.client?.internalValue as? TargetCompilation<*>
                        ?: (it as MinecraftTargetInternal).main
                }
            },
        )
    }
}
