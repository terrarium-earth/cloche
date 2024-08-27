package earth.terrarium.cloche

import net.msrandom.minecraftcodev.accesswidener.accessWidenersConfigurationName
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.forge.patchesConfigurationName
import net.msrandom.minecraftcodev.mixins.mixinsConfigurationName
import net.msrandom.minecraftcodev.remapper.mappingsConfigurationName
import net.msrandom.virtualsourcesets.VirtualExtension
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet

const val JAVA_EXPECT_ACTUAL_ANNOTATION_PROCESSOR = "net.msrandom:java-expect-actual-processor:1.0.8"

/**
 * Depend on the compiled output of [dependency], including the configuration dependencies for resolution to the proper variants
 */
context(Project)
internal fun SourceSet.linkDynamically(dependency: SourceSet) {
    compileClasspath += dependency.output
    runtimeClasspath += dependency.output

    project.extend(mixinsConfigurationName, dependency.mixinsConfigurationName)
    project.extend(patchesConfigurationName, dependency.patchesConfigurationName)
    project.extend(mappingsConfigurationName, dependency.mappingsConfigurationName)
    project.extend(accessWidenersConfigurationName, dependency.accessWidenersConfigurationName)
    project.extend(apiConfigurationName, dependency.apiConfigurationName)
    project.extend(compileOnlyApiConfigurationName, dependency.compileOnlyApiConfigurationName)
    project.extend(implementationConfigurationName, dependency.implementationConfigurationName)
    project.extend(runtimeOnlyConfigurationName, dependency.runtimeOnlyConfigurationName)
    project.extend(compileOnlyConfigurationName, dependency.compileOnlyConfigurationName)
    project.extend(modConfigurationName(apiConfigurationName), modConfigurationName(dependency.apiConfigurationName))
    project.extend(modConfigurationName(compileOnlyApiConfigurationName), modConfigurationName(dependency.compileOnlyApiConfigurationName))
    project.extend(modConfigurationName(implementationConfigurationName), modConfigurationName(dependency.implementationConfigurationName))
    project.extend(modConfigurationName(runtimeOnlyConfigurationName), modConfigurationName(dependency.runtimeOnlyConfigurationName))
    project.extend(modConfigurationName(compileOnlyConfigurationName), modConfigurationName(dependency.compileOnlyConfigurationName))
}

/**
 * Include [dependency] to be compiled alongside the current source, allowing Java platform annotations and Kotlin Multiplatform to function
 */
context(Project)
internal fun SourceSet.linkStatically(dependency: SourceSet) {
    extension<VirtualExtension>().dependsOn.add(dependency)

    project.dependencies.add(annotationProcessorConfigurationName, JAVA_EXPECT_ACTUAL_ANNOTATION_PROCESSOR)
}
