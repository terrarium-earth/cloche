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
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.compile.JavaCompile

private const val GENERATE_JAVA_EXPECT_STUBS_OPTION = "generateExpectStubs"

internal class CommonInfo(
    val target: CommonTargetInternal,
    val dependants: Set<MinecraftTargetInternal>,
    val dependencies: List<CommonTarget>,
    val type: String,
)

context(Project) internal fun createCommonTarget(common: CommonInfo, onlyCommonOfType: Boolean) {
    val main = common.dependants.map { it to it.main }
    val client = common.dependants.takeIf { it.any { it is FabricTarget } }?.map { it to ((it as? FabricTarget)?.client ?: it.main) as RunnableCompilationInternal }
    val data = common.dependants.map { it to it.data }

    fun intersection(compilations: List<Pair<MinecraftTarget, RunnableCompilationInternal>>): FileCollection {
        if (compilations.size == 1) {
            return compilations.first().second.finalMinecraftFiles
        }

        val name = lowerCamelCaseGradleName("create", *compilations.map { (target) -> target.name }.sorted().toTypedArray(), "intersection")

        val createIntersection = project.tasks.withType(JarIntersection::class.java).findByName(name) ?: run {
            project.tasks.create(project.addSetupTask(name), JarIntersection::class.java) {
                for ((_, compilation) in compilations) {
                    it.files.from(compilation.finalMinecraftFiles)
                }
            }
        }

        return files(createIntersection.output)
    }

    fun add(compilation: CommonCompilation, variant: PublicationVariant, edgeCompilations: List<Pair<MinecraftTarget, RunnableCompilationInternal>>) {
        val sourceSet = with(common.target) {
            compilation.sourceSet
        }

        if (common.target.name != ClocheExtension::common.name || compilation.name != SourceSet.MAIN_SOURCE_SET_NAME) {
            project.extension<JavaPluginExtension>().registerFeature(sourceSet.name) { spec ->
                spec.usingSourceSet(sourceSet)
                spec.capability(compilation.capabilityGroup, compilation.capabilityName, project.version.toString())

                if (common.target.name != ClocheExtension::common.name && !common.target.publish) {
                    spec.disablePublication()
                }

                for (featureAction in compilation.javaFeatureActions) {
                    featureAction.execute(spec)
                }
            }
        }

        configureSourceSet(sourceSet, common.target, compilation)

        project.components.named("java") { java ->
            java as AdhocComponentWithVariants

            java.addVariantsFromConfiguration(project.configurations.getByName(sourceSet.runtimeElementsConfigurationName)) { variant ->
                // Common compilations are not runnable.
                variant.skip()
            }
        }

        val dependencyHolder = project.configurations.create(
            lowerCamelCaseGradleName(
                common.target.name,
                compilation.name.takeUnless { it == SourceSet.MAIN_SOURCE_SET_NAME },
                "intersectionDependencies"
            )
        ) { configuration ->
            configuration.isCanBeResolved = false
            configuration.isCanBeConsumed = false
        }

        project.dependencies.add(dependencyHolder.name, intersection(edgeCompilations))

        compilation.attributes {
            it
                .attribute(VARIANT_ATTRIBUTE, variant)
                .attribute(CommonTargetAttributes.TYPE, common.type)

            if (!onlyCommonOfType && common.target.name != ClocheExtension::common.name && !common.target.publish) {
                it.attribute(CommonTargetAttributes.NAME, common.target.name)
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

        for (dependencySetupAction in compilation.dependencySetupActions) {
            dependencySetupAction.execute(dependencyHandler)
        }

        for (edge in common.dependants) {
            val edgeCompilation = when (compilation.name) {
                ClochePlugin.CLIENT_COMPILATION_NAME -> (edge as? FabricTarget)?.client ?: edge.main
                ClochePlugin.DATA_COMPILATION_NAME -> edge.data
                else -> edge.main
            } as RunnableCompilationInternal

            val edgeDependant = with(edge) {
                edgeCompilation.sourceSet
            }

            edgeDependant.linkStatically(sourceSet)
        }

        for (dependency in common.dependencies) {
            val dependencyCompilation = when (compilation.name) {
                ClochePlugin.CLIENT_COMPILATION_NAME -> dependency.client
                ClochePlugin.DATA_COMPILATION_NAME -> dependency.data
                else -> dependency.main
            } as CommonCompilation

            val dependencySourceSet = with(dependency) {
                dependencyCompilation.sourceSet
            }

            sourceSet.linkStatically(dependencySourceSet)
        }

        if (compilation.name == SourceSet.MAIN_SOURCE_SET_NAME) {
            return
        }

        val compilationDependencyScope = configurations.create(lowerCamelCaseGradleName(common.target.name, compilation.name, "dependencyScope")) {
            it.isCanBeResolved = false
            it.isCanBeConsumed = false
        }

        project.configurations.named(sourceSet.apiElementsConfigurationName) {
            it.extendsFrom(compilationDependencyScope)
        }

        project.configurations.named(sourceSet.compileClasspathConfigurationName) {
            it.extendsFrom(compilationDependencyScope)
        }

        with(common.target) {
            sourceSet.linkDynamically(common.target.main, compilationDependencyScope)
        }
    }

    add(common.target.main, PublicationVariant.Common, main)
    add(common.target.data, PublicationVariant.Data, data)

    client?.let {
        add(common.target.client, PublicationVariant.Client, it)
    }
}
