package earth.terrarium.cloche

import earth.terrarium.cloche.target.CommonCompilation
import earth.terrarium.cloche.target.CompilationInternal
import earth.terrarium.cloche.target.TargetCompilation
import earth.terrarium.cloche.target.modConfigurationName
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.mixins.mixinsConfigurationName
import net.msrandom.virtualsourcesets.SourceSetStaticLinkageInfo
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet

const val JAVA_EXPECT_ACTUAL_ANNOTATION_PROCESSOR = "net.msrandom:java-expect-actual-processor:1.0.8"
const val KOTLIN_MULTIPLATFORM_STUB_SYMBOL_PROCESSOR = "net.msrandom:kmp-actual-stubs-processor:1.0.2"

/**
 * Depend on the compiled output of [dependency], by requesting the capability to allow for resolution to the proper variants
 */
context(Project)
private fun SourceSet.addClasspathDependency(dependency: SourceSet) {
    extend(implementationConfigurationName, dependency.implementationConfigurationName)
    extend(apiConfigurationName, dependency.apiConfigurationName)
    extend(runtimeOnlyConfigurationName, dependency.runtimeOnlyConfigurationName)
    extend(compileOnlyConfigurationName, dependency.compileOnlyConfigurationName)
    extend(compileOnlyApiConfigurationName, dependency.compileOnlyApiConfigurationName)

    extend(modConfigurationName(implementationConfigurationName), modConfigurationName(dependency.implementationConfigurationName))
    extend(modConfigurationName(apiConfigurationName), modConfigurationName(dependency.apiConfigurationName))
    extend(modConfigurationName(runtimeOnlyConfigurationName), modConfigurationName(dependency.runtimeOnlyConfigurationName))
    extend(modConfigurationName(compileOnlyConfigurationName), modConfigurationName(dependency.compileOnlyConfigurationName))
    extend(modConfigurationName(compileOnlyApiConfigurationName), modConfigurationName(dependency.compileOnlyApiConfigurationName))

    extend(mixinsConfigurationName, dependency.mixinsConfigurationName)
}

/**
 * Depend on the api configuration of [dependency]
 */
context(Project)
internal fun CommonCompilation.addClasspathDependency(dependency: CommonCompilation) {
    println("(classpath dependency) $this -> $dependency")

    sourceSet.compileClasspath += dependency.sourceSet.output

    artifacts {
        it.add(sourceSet.apiElementsConfigurationName, tasks.named(dependency.sourceSet.jarTaskName))
    }

    sourceSet.addClasspathDependency(dependency.sourceSet)
    accessWideners.from(dependency.accessWideners)
}

/**
 * Depend on the variant of [dependency]
 */
context(Project)
internal fun TargetCompilation.addClasspathDependency(dependency: TargetCompilation) {
    println("(classpath dependency) $this -> $dependency")

    sourceSet.compileClasspath += dependency.sourceSet.output
    sourceSet.runtimeClasspath += dependency.sourceSet.output

    artifacts {
        it.add(sourceSet.apiElementsConfigurationName, tasks.named(dependency.sourceSet.jarTaskName))
        it.add(sourceSet.runtimeElementsConfigurationName, tasks.named(dependency.sourceSet.jarTaskName))
    }

    sourceSet.addClasspathDependency(dependency.sourceSet)
    accessWideners.from(dependency.accessWideners)
}

/**
 * Include [dependency] to be compiled alongside the current source, allowing Java platform annotations and Kotlin Multiplatform to function
 */
context(Project)
internal fun CompilationInternal.addSourceDependency(dependency: CompilationInternal) {
    println("(source dependency) $this -> $dependency")

    sourceSet.extension<SourceSetStaticLinkageInfo>().link(dependency.sourceSet)

    project.dependencies.add(sourceSet.annotationProcessorConfigurationName, JAVA_EXPECT_ACTUAL_ANNOTATION_PROCESSOR)
    accessWideners.from(dependency.accessWideners)
}
