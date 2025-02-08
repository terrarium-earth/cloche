package earth.terrarium.cloche

import earth.terrarium.cloche.api.target.FabricTarget
import earth.terrarium.cloche.api.target.compilation.TargetSecondarySourceSets
import earth.terrarium.cloche.target.*
import earth.terrarium.cloche.target.fabric.FabricTargetImpl
import net.msrandom.minecraftcodev.accesswidener.accessWidenersConfigurationName
import net.msrandom.minecraftcodev.core.MinecraftComponentMetadataRule
import net.msrandom.minecraftcodev.core.VERSION_MANIFEST_URL
import net.msrandom.minecraftcodev.core.utils.getGlobalCacheDirectory
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.intersection.JarIntersection
import net.msrandom.minecraftcodev.mixins.mixinsConfigurationName
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.compile.JavaCompile

private const val GENERATE_JAVA_EXPECT_STUBS_OPTION = "generateExpectStubs"

internal class CommonInfo(
    val dependants: Set<MinecraftTargetInternal<*>>,
    val type: String?,
    val version: String?,
)

context(Project) internal fun createCommonTarget(
    commonTarget: CommonTargetInternal,
    commonInfo: Provider<CommonInfo>,
    onlyCommonOfType: Provider<Boolean>
) {
    val featureName = commonTarget.featureName
    val clientTargetMinecraftName = lowerCamelCaseGradleName(featureName, "client")

    project.afterEvaluate {
        it.dependencies.components.withModule(
            ClochePlugin.STUB_MODULE,
            MinecraftComponentMetadataRule::class.java
        ) {
            it.params(
                getGlobalCacheDirectory(project),
                commonInfo.get().dependants.map { it.minecraftVersion.get() },
                VERSION_MANIFEST_URL,
                project.gradle.startParameter.isOffline,
                featureName,
                clientTargetMinecraftName,
            )
        }
    }

    fun intersection(
        compilationName: String,
        compilations: Provider<Map<MinecraftTargetInternal<*>, TargetCompilation>>
    ): FileCollection {
        val compilationName = compilationName.takeUnless { it == SourceSet.MAIN_SOURCE_SET_NAME }

        val name = lowerCamelCaseGradleName("create", commonTarget.name, compilationName, "intersection")

        val createIntersection = if (name in tasks.names) {
            tasks.named(name, JarIntersection::class.java)
        } else {
            tasks.register(project.addSetupTask(name), JarIntersection::class.java) {
                it.group = "minecraft-stubs"

                val jarName = if (compilationName == null) {
                    commonTarget.capabilityName
                } else {
                    "${commonTarget.capabilityName}-$compilationName"
                }

                it.output.set(it.temporaryDir.resolve("$jarName-minecraft-stub.jar"))

                it.files.from(compilations.map { it.map { it.value.finalMinecraftFile } }.orElse(listOf()))
            }
        }

        return files(createIntersection.flatMap(JarIntersection::output))
    }

    fun dependencyHolder(compilation: CommonCompilation) = project.configurations.maybeCreate(
        lowerCamelCaseGradleName(
            commonTarget.featureName,
            compilation.namePart,
            "intersectionDependencies"
        )
    ).apply {
        isCanBeConsumed = false
        isCanBeResolved = false
    }

    fun addCompilation(
        compilation: CommonCompilation,
        variant: PublicationSide,
        intersection: FileCollection,
        targetIntersection: String
    ) {
        val sourceSet = with(commonTarget) {
            compilation.sourceSet
        }

        project.createCompilationVariants(
            compilation,
            sourceSet,
            commonTarget.name == COMMON || commonTarget.publish
        )

        configureSourceSet(sourceSet, commonTarget, compilation, false)

        project.components.named("java") { java ->
            java as AdhocComponentWithVariants

            java.addVariantsFromConfiguration(project.configurations.getByName(sourceSet.runtimeElementsConfigurationName)) { variant ->
                // Common compilations are not runnable.
                variant.skip()
            }
        }

        val dependencyHolder = dependencyHolder(compilation)

        val stub = project.dependencies.enforcedPlatform(ClochePlugin.STUB_DEPENDENCY) as ExternalModuleDependency

        stub.capabilities {
            it.requireCapability("net.msrandom:$targetIntersection")
        }

        project.dependencies.add(dependencyHolder.name, stub)
        project.dependencies.add(dependencyHolder.name, intersection)

        compilation.attributes {
            it.attribute(SIDE_ATTRIBUTE, variant)

            // afterEvaluate needed as the attributes existing(not just their values) depend on configurable info
            afterEvaluate { project ->
                val commonInfo = commonInfo.get()

                if (commonInfo.type != null) {
                    it.attribute(CommonTargetAttributes.TYPE, commonInfo.type)
                }

                if (commonInfo.version != null) {
                    it.attribute(TargetAttributes.MINECRAFT_VERSION, commonInfo.version)
                }

                if (!onlyCommonOfType.get() && commonTarget.name != COMMON && !commonTarget.publish) {
                    it.attribute(CommonTargetAttributes.NAME, commonTarget.name)
                }
            }
        }

        project.configurations.named(sourceSet.compileClasspathConfigurationName) {
            it.extendsFrom(dependencyHolder)

            it.attributes(compilation::attributes)
        }

        for (name in listOf(
            sourceSet.apiElementsConfigurationName,
            sourceSet.runtimeElementsConfigurationName,
            sourceSet.javadocElementsConfigurationName,
            sourceSet.sourcesElementsConfigurationName
        )) {
            project.configurations.findByName(name)?.attributes(compilation::attributes)
        }

        project.dependencies.add(
            sourceSet.compileOnlyConfigurationName,
            "net.msrandom:java-expect-actual-annotations:1.0.0"
        )

        project.dependencies.add(
            sourceSet.annotationProcessorConfigurationName,
            JAVA_EXPECT_ACTUAL_ANNOTATION_PROCESSOR
        )
        project.dependencies.add(sourceSet.accessWidenersConfigurationName, compilation.accessWideners)
        project.dependencies.add(sourceSet.mixinsConfigurationName, compilation.mixins)

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
        dataGetter: (MinecraftTargetInternal<*>) -> TargetCompilation,
        testGetter: (MinecraftTargetInternal<*>) -> TargetCompilation,
        variant: PublicationSide,
        intersection: FileCollection,
        targetIntersection: String,
    ) {
        addCompilation(compilation, variant, intersection, targetIntersection)

        if (compilation.name != SourceSet.MAIN_SOURCE_SET_NAME) {
            compilation.linkDynamically(commonTarget.main)

            dependencyHolder(compilation).extendsFrom(dependencyHolder(commonTarget.main))
        }

        compilation.data.onConfigured {
            addCompilation(
                it,
                variant,
                intersection(
                    it.name,
                    commonInfo.map {
                        it.dependants.associateWith {
                            dataGetter(it)
                        }
                    }
                ),
                targetIntersection,
            )

            it.linkDynamically(compilation)
            it.linkDynamically(commonTarget.main)

            if (compilation.name != SourceSet.MAIN_SOURCE_SET_NAME) {
                commonTarget.main.data.onConfigured { data ->
                    it.linkDynamically(data)

                    dependencyHolder(it).extendsFrom(dependencyHolder(data))
                }
            }
        }

        compilation.test.onConfigured {
            addCompilation(
                it,
                variant,
                intersection(
                    it.name,
                    commonInfo.map {
                        it.dependants.associateWith {
                            testGetter(it)
                        }
                    }
                ),
                targetIntersection,
            )

            it.linkDynamically(compilation)
            it.linkDynamically(commonTarget.main)

            if (compilation.name != SourceSet.MAIN_SOURCE_SET_NAME) {
                commonTarget.main.test.onConfigured { test ->
                    it.linkDynamically(test)

                    dependencyHolder(it).extendsFrom(dependencyHolder(test))
                }
            }
        }
    }

    add(
        commonTarget.main,
        { it.data.internalValue ?: it.main },
        { it.test.internalValue ?: it.main },
        // TODO Can potentially be joined if all targets are forge/includedClient targets
        PublicationSide.Common,
        intersection(
            commonTarget.main.name,
            commonInfo.map { it.dependants.associateWith(MinecraftTargetInternal<*>::main) }),
        featureName,
    )

    commonTarget.client.onConfigured {
        add(
            it,
            { (it as? FabricTargetImpl)?.client?.internalValue?.data?.internalValue ?: it.data.internalValue ?: it.main },
            { (it as? FabricTargetImpl)?.client?.internalValue?.test?.internalValue ?: it.test.internalValue ?: it.main },
            PublicationSide.Client,
            intersection(it.name, commonInfo.map {
                it.dependants.associateWith {
                    (it as? FabricTargetImpl)?.client?.internalValue as? TargetCompilation ?: it.main
                }
            }),
            clientTargetMinecraftName,
        )
    }
}
