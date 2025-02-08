package earth.terrarium.cloche

import earth.terrarium.cloche.target.CommonCompilation
import earth.terrarium.cloche.target.CommonTargetInternal
import earth.terrarium.cloche.target.CompilationInternal
import earth.terrarium.cloche.target.TargetCompilation
import net.msrandom.minecraftcodev.accesswidener.accessWidenersConfigurationName
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.mixins.mixinsConfigurationName
import net.msrandom.virtualsourcesets.SourceSetStaticLinkageInfo
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
 * Depend on the api configuration of [dependency]
 */
context(Project)
internal fun CommonCompilation.linkDynamically(dependency: CommonCompilation) {
    println("(dynamic) $this -> $dependency")

    sourceSet.compileClasspath += dependency.sourceSet.output

    artifacts {
        it.add(sourceSet.apiElementsConfigurationName, tasks.named(dependency.sourceSet.jarTaskName))
    }

    sourceSet.linkDynamically(dependency.sourceSet)
}

/**
 * Depend on the variant of [dependency]
 */
context(Project)
internal fun TargetCompilation.linkDynamically(dependency: TargetCompilation) {
    println("(dynamic) $this -> $dependency")

    sourceSet.compileClasspath += dependency.sourceSet.output
    sourceSet.runtimeClasspath += dependency.sourceSet.output

    artifacts {
        it.add(sourceSet.apiElementsConfigurationName, tasks.named(dependency.sourceSet.jarTaskName))
        it.add(sourceSet.runtimeElementsConfigurationName, tasks.named(dependency.sourceSet.jarTaskName))
    }

    sourceSet.linkDynamically(dependency.sourceSet)
}

/**
 * Include [dependency] to be compiled alongside the current source, allowing Java platform annotations and Kotlin Multiplatform to function
 */
context(Project)
internal fun CompilationInternal.linkStatically(dependency: CompilationInternal) {
    println("(global static) $this -> $dependency")

    sourceSet.extension<SourceSetStaticLinkageInfo>().link(dependency.sourceSet)

    project.dependencies.add(sourceSet.annotationProcessorConfigurationName, JAVA_EXPECT_ACTUAL_ANNOTATION_PROCESSOR)
}
