package earth.terrarium.cloche

import earth.terrarium.cloche.target.*
import net.msrandom.minecraftcodev.accesswidener.accessWidenersConfigurationName
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.mixins.mixinsConfigurationName
import net.msrandom.virtualsourcesets.VirtualExtension
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet

const val JAVA_EXPECT_ACTUAL_ANNOTATION_PROCESSOR = "net.msrandom:java-expect-actual-processor:1.0.8"

/**
 * Depend on the compiled output of [sourceSet], by requesting the capability to allow for resolution to the proper variants
 */
context(Project)
private fun SourceSet.linkDynamically(sourceSet: SourceSet) {
    extend(implementationConfigurationName, sourceSet.implementationConfigurationName)
    extend(apiConfigurationName, sourceSet.apiConfigurationName)
    extend(runtimeOnlyConfigurationName, sourceSet.runtimeOnlyConfigurationName)
    extend(compileOnlyConfigurationName, sourceSet.compileOnlyConfigurationName)
    extend(compileOnlyApiConfigurationName, sourceSet.compileOnlyApiConfigurationName)
    extend(mixinsConfigurationName, sourceSet.mixinsConfigurationName)
    extend(accessWidenersConfigurationName, sourceSet.accessWidenersConfigurationName)
}

/**
 * Depend on the api configuration of [compilation]
 */
context(Project, CommonTargetInternal)
internal fun CommonCompilation.linkDynamically(compilation: CommonCompilation) {
    sourceSet.compileClasspath += compilation.sourceSet.output

    artifacts {
        it.add(sourceSet.apiElementsConfigurationName, tasks.named(compilation.sourceSet.jarTaskName))
    }

    sourceSet.linkDynamically(compilation.sourceSet)
}

/**
 * Depend on the variant of [compilation]
 */
context(Project)
internal fun TargetCompilation.linkDynamically(compilation: TargetCompilation) {
    sourceSet.compileClasspath += compilation.sourceSet.output
    sourceSet.runtimeClasspath += compilation.sourceSet.output

    artifacts {
        it.add(sourceSet.apiElementsConfigurationName, tasks.named(compilation.sourceSet.jarTaskName))
        it.add(sourceSet.runtimeElementsConfigurationName, tasks.named(compilation.sourceSet.jarTaskName))
    }

    sourceSet.linkDynamically(compilation.sourceSet)
}

/**
 * Include [dependency] to be compiled alongside the current source, allowing Java platform annotations and Kotlin Multiplatform to function
 */
context(Project)
internal fun SourceSet.linkStatically(dependency: SourceSet) {
    extension<VirtualExtension>().dependsOn.add(dependency)

    project.dependencies.add(annotationProcessorConfigurationName, JAVA_EXPECT_ACTUAL_ANNOTATION_PROCESSOR)
}
