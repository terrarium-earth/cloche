package earth.terrarium.cloche

import earth.terrarium.cloche.target.CommonCompilation
import earth.terrarium.cloche.target.CompilationInternal
import earth.terrarium.cloche.target.TargetCompilation
import earth.terrarium.cloche.target.localRuntimeConfigurationName
import earth.terrarium.cloche.target.modConfigurationName
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.mixins.mixinsConfigurationName
import net.msrandom.virtualsourcesets.SourceSetStaticLinkageInfo
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSet

const val JAVA_EXPECT_ACTUAL_ANNOTATION_PROCESSOR = "net.msrandom:java-expect-actual-processor:1.0.9"
const val JAVA_CLASS_EXTENSIONS_ANNOTATIONS = "net.msrandom:class-extension-annotations:1.0.0"
const val JAVA_CLASS_EXTENSIONS_PROCESSOR = "net.msrandom:java-class-extensions-processor:1.0.0"
const val KOTLIN_MULTIPLATFORM_STUB_SYMBOL_PROCESSOR = "net.msrandom:kmp-actual-stubs-processor:1.0.3"

context(Project)
private fun SourceSet.extendConfigurations(dependency: SourceSet, common: Boolean) {
    val apiBucket: String
    val compileOnlyApiBucket: String
    val implementationBucket: String
    val compileOnlyBucket: String

    if (common) {
        apiBucket = dependency.commonBucketConfigurationName(JavaPlugin.API_CONFIGURATION_NAME)
        compileOnlyApiBucket = dependency.commonBucketConfigurationName(JavaPlugin.COMPILE_ONLY_API_CONFIGURATION_NAME)
        implementationBucket = dependency.commonBucketConfigurationName(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME)
        compileOnlyBucket = dependency.commonBucketConfigurationName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME)
    } else {
        apiBucket = dependency.apiConfigurationName
        compileOnlyApiBucket = dependency.compileOnlyApiConfigurationName
        implementationBucket = dependency.implementationConfigurationName
        compileOnlyBucket = dependency.compileOnlyConfigurationName
    }

    project.extend(apiConfigurationName, apiBucket)
    project.extend(compileOnlyApiConfigurationName, compileOnlyApiBucket)
    project.extend(implementationConfigurationName, implementationBucket)
    project.extend(compileOnlyConfigurationName, compileOnlyBucket)

    project.extend(runtimeOnlyConfigurationName, dependency.runtimeOnlyConfigurationName)
    project.extend(localRuntimeConfigurationName, dependency.localRuntimeConfigurationName)

    project.extend(
        modConfigurationName(implementationConfigurationName),
        modConfigurationName(dependency.implementationConfigurationName),
    )

    project.extend(
        modConfigurationName(apiConfigurationName),
        modConfigurationName(dependency.apiConfigurationName),
    )

    project.extend(
        modConfigurationName(compileOnlyConfigurationName),
        modConfigurationName(dependency.compileOnlyConfigurationName),
    )

    project.extend(
        modConfigurationName(compileOnlyApiConfigurationName),
        modConfigurationName(dependency.compileOnlyApiConfigurationName),
    )

    project.extend(
        modConfigurationName(runtimeOnlyConfigurationName),
        modConfigurationName(dependency.runtimeOnlyConfigurationName),
    )

    project.extend(
        modConfigurationName(localRuntimeConfigurationName),
        modConfigurationName(dependency.localRuntimeConfigurationName),
    )

    project.extend(mixinsConfigurationName, dependency.mixinsConfigurationName)
}

/**
 * Depend on the api configuration of [dependency]
 */
context(Project)
internal fun CommonCompilation.addClasspathDependency(dependency: CommonCompilation) {
    println("(classpath dependency) $this -> $dependency")

    sourceSet.compileClasspath += dependency.sourceSet.output

    if (!isTest && !dependency.isTest) {
        artifacts {
            it.add(sourceSet.apiElementsConfigurationName, tasks.named(dependency.sourceSet.jarTaskName))
        }
    }

    sourceSet.extendConfigurations(dependency.sourceSet, true)
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

    if (!isTest && !dependency.isTest) {
        artifacts {
            it.add(sourceSet.apiElementsConfigurationName, tasks.named(dependency.sourceSet.jarTaskName))

            it.add(sourceSet.runtimeElementsConfigurationName, tasks.named(dependency.sourceSet.jarTaskName))
        }
    }

    sourceSet.extendConfigurations(dependency.sourceSet, false)
    accessWideners.from(dependency.accessWideners)
}

/**
 * Include [dependency] to be compiled alongside the current source, allowing Java platform annotations and Kotlin Multiplatform to function
 */
context(Project)
internal fun CompilationInternal.addSourceDependency(dependency: CommonCompilation) {
    println("(source dependency) $this -> $dependency")

    sourceSet.extension<SourceSetStaticLinkageInfo>().link(dependency.sourceSet)

    sourceSet.extendConfigurations(dependency.sourceSet, true)

    project.dependencies.add(sourceSet.compileOnlyConfigurationName, JAVA_CLASS_EXTENSIONS_ANNOTATIONS)
    project.dependencies.add(sourceSet.annotationProcessorConfigurationName, JAVA_CLASS_EXTENSIONS_PROCESSOR)
    project.dependencies.add(sourceSet.annotationProcessorConfigurationName, JAVA_EXPECT_ACTUAL_ANNOTATION_PROCESSOR)
    accessWideners.from(dependency.accessWideners)
}
