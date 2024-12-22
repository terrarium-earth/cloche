package earth.terrarium.cloche

import earth.terrarium.cloche.target.*
import net.msrandom.minecraftcodev.accesswidener.accessWidenersConfigurationName
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.intersection.JarIntersection
import net.msrandom.minecraftcodev.mixins.mixinsConfigurationName
import org.gradle.api.Project
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.compile.JavaCompile

private const val GENERATE_JAVA_EXPECT_STUBS_OPTION = "generateExpectStubs"

internal class CommonInfo(
    val dependants: Set<MinecraftTargetInternal>,
    val type: String?,
    val version: String?,
)

context(Project) internal fun createCommonTarget(commonTarget: CommonTargetInternal, commonInfo: Provider<CommonInfo>, onlyCommonOfType: Provider<Boolean>) {
    val main = commonInfo.map { it.dependants.associateWith(MinecraftTargetInternal::main) }

    val client: Provider<Map<MinecraftTargetInternal, RunnableCompilationInternal>> = commonInfo.map {
        if (it.dependants.any { it is FabricTarget }) {
            it.dependants.associateWith {
                ((it as? FabricTarget)?.client ?: it.main) as RunnableCompilationInternal
            }
        } else {
            null
        }
    }

    val data: Provider<Map<MinecraftTargetInternal, RunnableCompilationInternal>> = commonInfo.map { it.dependants.associateWith(MinecraftTargetInternal::data) }

    fun intersection(compilationName: String, compilations: Provider<Map<MinecraftTargetInternal, RunnableCompilationInternal>>): FileCollection {
        val compilationName = compilationName.takeUnless { it == SourceSet.MAIN_SOURCE_SET_NAME }

        val name = lowerCamelCaseGradleName("create", commonTarget.name, compilationName, "intersection")

        val createIntersection = if (name in tasks.names) {
            tasks.named(name, JarIntersection::class.java)
        } else {
            tasks.register(project.addSetupTask(name), JarIntersection::class.java) {
                val jarName = if (compilationName == null) {
                    commonTarget.name
                } else {
                    "${commonTarget.name}-$compilationName"
                }

                it.output.set(it.temporaryDir.resolve("$jarName-intersection.jar"))

                it.files.from(compilations.map { it.map { it.value.finalMinecraftFile } }.orElse(listOf()))
            }
        }

        return files(createIntersection.flatMap(JarIntersection::output))
    }

    fun add(compilation: CommonCompilation, variant: PublicationVariant, intersection: FileCollection) {
        val sourceSet = with(commonTarget) {
            compilation.sourceSet
        }

        if (commonTarget.name != COMMON || compilation.name != SourceSet.MAIN_SOURCE_SET_NAME) {
            project.extension<JavaPluginExtension>().registerFeature(sourceSet.name) { spec ->
                spec.usingSourceSet(sourceSet)
                spec.capability(compilation.capabilityGroup, compilation.capabilityName, project.version.toString())

                if (commonTarget.name != COMMON && !commonTarget.publish) {
                    spec.disablePublication()
                }

                for (featureAction in compilation.javaFeatureActions) {
                    featureAction.execute(spec)
                }
            }
        }

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

        project.dependencies.add(dependencyHolder.name, intersection)

        compilation.attributes {
            it.attribute(VARIANT_ATTRIBUTE, variant)

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

        project.dependencies.add(sourceSet.compileOnlyConfigurationName, "net.msrandom:java-expect-actual-annotations:1.0.0")
        project.dependencies.add(sourceSet.annotationProcessorConfigurationName, JAVA_EXPECT_ACTUAL_ANNOTATION_PROCESSOR)
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

        val compilationDependencyScope = configurations.create(lowerCamelCaseGradleName(commonTarget.featureName, compilation.name, "dependencyScope")) {
            it.isCanBeResolved = false
            it.isCanBeConsumed = false
        }

        project.configurations.named(sourceSet.apiElementsConfigurationName) {
            it.extendsFrom(compilationDependencyScope)
        }

        project.configurations.named(sourceSet.compileClasspathConfigurationName) {
            it.extendsFrom(compilationDependencyScope)
        }

        with(commonTarget) {
            compilation.linkDynamically(commonTarget.main, compilationDependencyScope)
        }
    }

    add(commonTarget.main, PublicationVariant.Common, intersection(commonTarget.main.name, main))
    add(commonTarget.data, PublicationVariant.Data, intersection(commonTarget.data.name, data))

    add(commonTarget.client, PublicationVariant.Client, intersection(commonTarget.client.name, client))
}
