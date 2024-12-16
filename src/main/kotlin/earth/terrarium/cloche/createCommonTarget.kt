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
    val type: String?,
    val version: String?,
)

context(Project) internal fun createCommonTarget(common: CommonInfo, onlyCommonOfType: Boolean) {
    val hasClient = common.dependants.any { it is FabricTarget }

    val main = common.dependants.associateWith(MinecraftTargetInternal::main)

    val client = if (hasClient) {
        common.dependants.associateWith {
            ((it as? FabricTarget)?.client ?: it.main) as RunnableCompilationInternal
        }
    } else {
        null
    }

    val data: Map<MinecraftTargetInternal, RunnableCompilationInternal> = common.dependants.associateWith(MinecraftTargetInternal::data)

    fun intersection(compilations: Map<MinecraftTargetInternal, RunnableCompilationInternal>): FileCollection {
        if (compilations.size == 1) {
            return project.files(compilations.values.first().finalMinecraftFile)
        }

        val compilationName = compilations.values.first().name.takeUnless { it == SourceSet.MAIN_SOURCE_SET_NAME }

        val name = lowerCamelCaseGradleName("create", *compilations.map { (target) -> target.featureName }.sorted().toTypedArray(), compilationName, "intersection")

        val createIntersection = project.tasks.withType(JarIntersection::class.java).findByName(name) ?: run {
            project.tasks.create(project.addSetupTask(name), JarIntersection::class.java) {
                for ((_, compilation) in compilations) {
                    it.files.from(compilation.finalMinecraftFile)
                }
            }
        }

        return files(createIntersection.output)
    }

    fun add(compilation: CommonCompilation, variant: PublicationVariant, intersection: FileCollection) {
        val sourceSet = with(common.target) {
            compilation.sourceSet
        }

        if (common.target.name != COMMON || compilation.name != SourceSet.MAIN_SOURCE_SET_NAME) {
            project.extension<JavaPluginExtension>().registerFeature(sourceSet.name) { spec ->
                spec.usingSourceSet(sourceSet)
                spec.capability(compilation.capabilityGroup, compilation.capabilityName, project.version.toString())

                if (common.target.name != COMMON && !common.target.publish) {
                    spec.disablePublication()
                }

                for (featureAction in compilation.javaFeatureActions) {
                    featureAction.execute(spec)
                }
            }
        }

        configureSourceSet(sourceSet, common.target, compilation, false)

        project.components.named("java") { java ->
            java as AdhocComponentWithVariants

            java.addVariantsFromConfiguration(project.configurations.getByName(sourceSet.runtimeElementsConfigurationName)) { variant ->
                // Common compilations are not runnable.
                variant.skip()
            }
        }

        val dependencyHolder = project.configurations.create(
            lowerCamelCaseGradleName(
                common.target.featureName,
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

            if (common.type != null) {
                it.attribute(CommonTargetAttributes.TYPE, common.type)
            }

           if (common.version != null) {
               it.attribute(TargetAttributes.MINECRAFT_VERSION, common.version)
           }

            if (!onlyCommonOfType && common.target.name != COMMON && !common.target.publish) {
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

        compilation.dependencySetupActions.all {
            it.execute(dependencyHandler)
        }

        if (compilation.name == SourceSet.MAIN_SOURCE_SET_NAME) {
            return
        }

        val compilationDependencyScope = configurations.create(lowerCamelCaseGradleName(common.target.featureName, compilation.name, "dependencyScope")) {
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
            compilation.linkDynamically(common.target.main, compilationDependencyScope)
        }
    }

    add(common.target.main, PublicationVariant.Common, intersection(main))
    add(common.target.data, PublicationVariant.Data, intersection(data))

    client?.let {
        add(common.target.client, PublicationVariant.Client, intersection(it))
    }
}
