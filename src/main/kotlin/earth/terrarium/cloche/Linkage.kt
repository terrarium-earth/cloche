package earth.terrarium.cloche

import earth.terrarium.cloche.target.*
import net.msrandom.minecraftcodev.accesswidener.accessWidenersConfigurationName
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.mixins.mixinsConfigurationName
import net.msrandom.virtualsourcesets.VirtualExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.SourceSet

const val JAVA_EXPECT_ACTUAL_ANNOTATION_PROCESSOR = "net.msrandom:java-expect-actual-processor:1.0.8"

fun Project.asDependency(configuration: String? = null) =
    dependencies.project(mapOf("path" to path, "configuration" to configuration)) as ProjectDependency

/**
 * Depend on the compiled output of [sourceSet], by requesting the capability to allow for resolution to the proper variants
 */
context(Project, ClocheTarget)
private fun SourceSet.linkDynamically(sourceSet: SourceSet, dependency: ProjectDependency, configurationName: String) {
    project.extend(mixinsConfigurationName, sourceSet.mixinsConfigurationName)
    project.extend(accessWidenersConfigurationName, sourceSet.accessWidenersConfigurationName)

    project.dependencies.add(configurationName, dependency)
}

/**
 * Depend on the api configuration of [compilation]
 */
context(Project, CommonTarget)
internal fun SourceSet.linkDynamically(compilation: CommonCompilation, dependencyScope: Configuration) {
    val dependency = project.asDependency().apply {
        capabilities {
            it.requireCapability(compilation.capability)
        }

        attributes(compilation::attributes)
    }

    linkDynamically(compilation.sourceSet, dependency, dependencyScope.name)
}

/**
 * Depend on the variant of [compilation]
 */
context(Project, MinecraftTarget)
internal fun SourceSet.linkDynamically(compilation: RunnableCompilationInternal) {
    val dependency = project.asDependency().apply {
        capabilities {
            it.requireCapability(compilation.capability)
        }

        attributes(compilation::attributes)
    }

    val configurationName = if (project.configurations.findByName(apiConfigurationName) == null) {
        implementationConfigurationName
    } else {
        apiConfigurationName
    }

    linkDynamically(compilation.sourceSet, dependency, configurationName)
}

/**
 * Include [dependency] to be compiled alongside the current source, allowing Java platform annotations and Kotlin Multiplatform to function
 */
context(Project)
internal fun SourceSet.linkStatically(dependency: SourceSet) {
    extension<VirtualExtension>().dependsOn.add(dependency)

    project.dependencies.add(annotationProcessorConfigurationName, JAVA_EXPECT_ACTUAL_ANNOTATION_PROCESSOR)
}
