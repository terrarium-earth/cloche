package earth.terrarium.cloche

import earth.terrarium.cloche.target.*
import net.msrandom.minecraftcodev.accesswidener.accessWidenersConfigurationName
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.mixins.mixinsConfigurationName
import net.msrandom.virtualsourcesets.VirtualExtension
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.SourceSet

const val JAVA_EXPECT_ACTUAL_ANNOTATION_PROCESSOR = "net.msrandom:java-expect-actual-processor:1.0.8"

internal fun Project.asDependency(configure: ProjectDependency.() -> Unit) =
    (dependencies.project(mapOf("path" to path)) as ProjectDependency).also(configure)

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
internal fun CommonCompilation.linkDynamically(compilation: CommonCompilation, dependencyScope: Configuration) {
    val dependency = project.asDependency {
        because("Dependency from ${target.name}:${this@linkDynamically.name} to ${compilation.target.name}:${compilation.name}")

        capabilities {
            it.requireCapability(compilation.capability)
        }

        attributes(compilation::attributes)
    }

    sourceSet.linkDynamically(compilation.sourceSet, dependency, dependencyScope.name)
}

/**
 * Depend on the variant of [compilation]
 */
context(Project, MinecraftTarget)
internal fun RunnableCompilationInternal.linkDynamically(compilation: RunnableCompilationInternal) {
    val dependency = project.asDependency {
        because("Dependency from ${target.name}:${this@linkDynamically.name} to ${compilation.target.name}:${compilation.name}")

        capabilities {
            it.requireCapability(compilation.capability)
        }

        attributes(compilation::attributes)
    }

    val sourceSet = sourceSet

    val configurationName = if (project.configurations.findByName(sourceSet.apiConfigurationName) == null) {
        sourceSet.implementationConfigurationName
    } else {
        sourceSet.apiConfigurationName
    }

    sourceSet.linkDynamically(compilation.sourceSet, dependency, configurationName)
}

/**
 * Include [dependency] to be compiled alongside the current source, allowing Java platform annotations and Kotlin Multiplatform to function
 */
context(Project)
internal fun SourceSet.linkStatically(dependency: SourceSet) {
    extension<VirtualExtension>().dependsOn.add(dependency)

    project.dependencies.add(annotationProcessorConfigurationName, JAVA_EXPECT_ACTUAL_ANNOTATION_PROCESSOR)
}
