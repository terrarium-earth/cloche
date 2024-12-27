package earth.terrarium.cloche

import earth.terrarium.cloche.ClochePlugin.Companion.STUB_MODULE
import earth.terrarium.cloche.target.*
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
    val dependants: Set<MinecraftTargetInternal>,
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
            STUB_MODULE,
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
        compilations: Provider<Map<MinecraftTargetInternal, TargetCompilation>>
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

    fun add(
        compilation: CommonCompilation,
        variant: PublicationVariant,
        intersection: FileCollection,
        targetIntersection: String
    ) {
        val sourceSet = with(commonTarget) {
            compilation.sourceSet
        }

        project.createCompilationVariants(
            commonTarget,
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

        val dependencyHolder = project.configurations.create(
            lowerCamelCaseGradleName(
                commonTarget.featureName,
                compilation.name.takeUnless { it == SourceSet.MAIN_SOURCE_SET_NAME },
                "intersectionDependencies"
            )
        ) { configuration ->
            configuration.isCanBeResolved = false
            configuration.isCanBeConsumed = false
        }

        val stub = project.dependencies.enforcedPlatform(ClochePlugin.STUB_DEPENDENCY) as ExternalModuleDependency

        stub.capabilities {
            it.requireCapability("net.msrandom:$targetIntersection")
        }

        project.dependencies.add(dependencyHolder.name, stub)
        project.dependencies.add(dependencyHolder.name, intersection)

        compilation.attributes {
            it.attribute(VARIANT_ATTRIBUTE, variant)

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

        val dependencyHandler = ClocheDependencyHandler(project, sourceSet)

        compilation.dependencySetupActions.all {
            it.execute(dependencyHandler)
        }

        if (compilation.name == SourceSet.MAIN_SOURCE_SET_NAME) {
            return
        }

        with(commonTarget) {
            compilation.linkDynamically(commonTarget.main)
        }
    }

    commonTarget.compilations.all {
        when (it.name) {
            SourceSet.MAIN_SOURCE_SET_NAME -> {
                add(
                    it,
                    PublicationVariant.Common,
                    intersection(
                        it.name,
                        commonInfo.map { it.dependants.associateWith(MinecraftTargetInternal::main) }),
                    featureName,
                )
            }

            ClochePlugin.DATA_COMPILATION_NAME -> {
                add(
                    it,
                    PublicationVariant.Client,
                    intersection(
                        it.name,
                        commonInfo.map { it.dependants.associateWith { it.data ?: it.main } }
                    ),
                    featureName,
                )
            }

            ClochePlugin.CLIENT_COMPILATION_NAME -> {
                add(
                    it,
                    PublicationVariant.Client,
                    intersection(it.name, commonInfo.map {
                        it.dependants.associateWith {
                            (it as? FabricTarget)?.client as? TargetCompilation ?: it.main
                        }
                    }),
                    clientTargetMinecraftName,
                )
            }
        }
    }
}
