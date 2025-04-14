package earth.terrarium.cloche

import earth.terrarium.cloche.ClochePlugin.Companion.KOTLIN_JVM_PLUGIN_ID
import earth.terrarium.cloche.target.*
import earth.terrarium.cloche.target.fabric.FabricTargetImpl
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.mixins.mixinsConfigurationName
import net.msrandom.stubs.GenerateStubApi
import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.compile.JavaCompile

private const val GENERATE_JAVA_EXPECT_STUBS_OPTION = "generateExpectStubs"

private fun convertClasspath(
    compilation: TargetCompilation,
    objects: ObjectFactory,
    configurationContainer: ConfigurationContainer
): Provider<List<GenerateStubApi.ResolvedArtifact>> {
    val minecraftFiles =
        compilation.extraClasspathFiles.zip(compilation.finalMinecraftFile, List<RegularFile>::plus)
            .map {
                it.map {
                    val artifact = objects.newInstance(GenerateStubApi.ResolvedArtifact::class.java)

                    artifact.file.set(it)

                    artifact
                }
            }

    val artifacts = getNonProjectArtifacts(
        configurationContainer,
        compilation.sourceSet.compileClasspathConfigurationName
    ).flatMap {
        it.artifacts.resolvedArtifacts.map { artifacts ->
            artifacts.map {
                val artifact = objects.newInstance(GenerateStubApi.ResolvedArtifact::class.java)

                artifact.id.set(it.id.componentIdentifier)
                artifact.file.set(it.file)

                artifact
            }
        }
    }

    return minecraftFiles.zip(artifacts, List<GenerateStubApi.ResolvedArtifact>::plus)
}

context(Project) internal fun createCommonTarget(
    commonTarget: CommonTargetInternal,
    onlyCommonOfType: Provider<Boolean>,
) {
    fun intersection(
        compilation: CommonCompilation,
        compilations: Provider<List<TargetCompilation>>,
    ): FileCollection {
        val name = lowerCamelCaseGradleName("create", commonTarget.name, compilation.featureName, "api-stub")

        val generateStub = tasks.register(name, GenerateStubApi::class.java) {
            it.group = "minecraft-stubs"

            val jarName = compilation.capabilityName?.let {
                "${commonTarget.classifierName}-$it"
            } ?: commonTarget.classifierName

            it.apiFileName.set("$jarName-api-stub.jar")

            val objects = objects
            val configurations = configurations

            val classpaths = compilations.flatMap {
                @Suppress("UNCHECKED_CAST")
                val classpath =
                    objects.listProperty(List::class.java) as ListProperty<List<GenerateStubApi.ResolvedArtifact>>

                for (compilation in it) {
                    classpath.add(convertClasspath(compilation, objects, configurations))
                }

                classpath
            }

            it.classpaths.set(classpaths)

            it.dependsOn(compilations.map { it.map { it.extraClasspathFiles } })
            it.dependsOn(compilations.map { it.map { it.finalMinecraftFile } })

            it.dependsOn(files(compilations.map {
                it.map {
                    getRelevantSyncArtifacts(it.sourceSet.compileClasspathConfigurationName)
                }
            }))
        }

        return fileTree(generateStub.flatMap(GenerateStubApi::outputDirectory)).builtBy(generateStub)
    }

    fun dependencyHolder(compilation: CommonCompilation) = configurations.maybeCreate(
        lowerCamelCaseGradleName(
            commonTarget.featureName,
            compilation.featureName,
            "intersectionDependencies",
        )
    ).apply {
        isCanBeConsumed = false
        isCanBeResolved = false
    }

    fun addCompilation(
        compilation: CommonCompilation,
        variant: PublicationSide,
        intersection: FileCollection,
    ) {
        val sourceSet = with(commonTarget) {
            compilation.sourceSet
        }

        createCompilationVariants(
            compilation,
            sourceSet,
            commonTarget.name == COMMON || commonTarget.publish
        )

        configureSourceSet(sourceSet, commonTarget, compilation, false)

        components.named("java") { java ->
            java as AdhocComponentWithVariants

            java.addVariantsFromConfiguration(configurations.getByName(sourceSet.runtimeElementsConfigurationName)) { variant ->
                // Common compilations are not runnable.
                variant.skip()
            }
        }

        val dependencyHolder = dependencyHolder(compilation)

        dependencies.add(dependencyHolder.name, intersection)

        compilation.attributes {
            it.attribute(SIDE_ATTRIBUTE, variant)

            // afterEvaluate needed as the attributes existing(not just their values) depend on configurable info
            afterEvaluate { project ->
                val commonType = commonTarget.commonType.getOrNull()
                val minecraftVersion = commonTarget.minecraftVersion.getOrNull()

                if (commonType != null) {
                    it.attribute(CommonTargetAttributes.TYPE, commonType)
                }

                if (minecraftVersion != null) {
                    it.attribute(TargetAttributes.MINECRAFT_VERSION, minecraftVersion)
                }

                if (!onlyCommonOfType.get() && commonTarget.name != COMMON && !commonTarget.publish) {
                    it.attribute(CommonTargetAttributes.NAME, commonTarget.name)
                }
            }
        }

        configurations.named(sourceSet.compileClasspathConfigurationName) {
            it.extendsFrom(dependencyHolder)

            it.attributes(compilation::attributes)
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
            sourceSet.compileOnlyConfigurationName,
            "net.msrandom:java-expect-actual-annotations:1.0.0"
        )

        dependencies.add(
            sourceSet.annotationProcessorConfigurationName,
            JAVA_EXPECT_ACTUAL_ANNOTATION_PROCESSOR
        )

        plugins.withId(KOTLIN_JVM_PLUGIN_ID) {
            val kspConfigurationName = if (SourceSet.isMain(sourceSet)) {
                "ksp"
            } else {
                lowerCamelCaseGradleName("ksp", sourceSet.name)
            }

            dependencies.add(
                kspConfigurationName,
                KOTLIN_MULTIPLATFORM_STUB_SYMBOL_PROCESSOR,
            )
        }

        dependencies.add(sourceSet.mixinsConfigurationName, compilation.mixins)

        tasks.named(sourceSet.compileJavaTaskName, JavaCompile::class.java) {
            it.options.compilerArgs.add("-A$GENERATE_JAVA_EXPECT_STUBS_OPTION")
        }

        plugins.withId("org.jetbrains.kotlin.jvm") {
            project.dependencies.add(
                sourceSet.compileOnlyConfigurationName,
                "net.msrandom:kmp-stub-annotations:1.0.0",
            )
        }
    }

    fun add(
        compilation: CommonTopLevelCompilation,
        dataGetter: (MinecraftTargetInternal) -> TargetCompilation,
        testGetter: (MinecraftTargetInternal) -> TargetCompilation,
        variant: PublicationSide,
        intersection: FileCollection,
    ) {
        addCompilation(compilation, variant, intersection)

        if (compilation.name != SourceSet.MAIN_SOURCE_SET_NAME) {
            compilation.addClasspathDependency(commonTarget.main)
        }

        compilation.data.onConfigured {
            addCompilation(
                it,
                variant,
                intersection(
                    it,
                    commonTarget.dependents.map {
                        it.map { dataGetter(it as MinecraftTargetInternal) }
                    }
                ),
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
                intersection(
                    it,
                    commonTarget.dependents.map {
                        it.map { testGetter(it as MinecraftTargetInternal) }
                    },
                ),
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
        PublicationSide.Common,
        intersection(
            commonTarget.main,
            commonTarget.dependents.map {
                it.map { (it as MinecraftTargetInternal).main }
            },
        ),
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
            PublicationSide.Client,
            intersection(
                it,
                commonTarget.dependents.map {
                    it.map {
                        (it as? FabricTargetImpl)?.client?.internalValue as? TargetCompilation
                            ?: (it as MinecraftTargetInternal).main
                    }
                },
            ),
        )
    }
}
