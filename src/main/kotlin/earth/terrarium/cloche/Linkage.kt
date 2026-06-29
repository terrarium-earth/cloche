package earth.terrarium.cloche

import earth.terrarium.cloche.target.compilation.CommonCompilation
import earth.terrarium.cloche.target.common.commonBucketConfigurationName
import earth.terrarium.cloche.target.compilation.CompilationInternal
import earth.terrarium.cloche.target.compilation.TargetCompilation
import earth.terrarium.cloche.target.compilation.externalApiConfigurationName
import earth.terrarium.cloche.target.compilation.externalCompileConfigurationName
import earth.terrarium.cloche.target.compilation.externalRuntimeConfigurationName
import earth.terrarium.cloche.target.compilation.localImplementationConfigurationName
import earth.terrarium.cloche.target.compilation.localRuntimeConfigurationName
import earth.terrarium.cloche.target.compilation.modConfigurationName
import earth.terrarium.cloche.util.CLASSES_AND_RESOURCES_VARIANT_NAME
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.virtualsourcesets.SourceSetStaticLinkageInfo
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationVariant
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSet

const val JAVA_EXPECT_ACTUAL_ANNOTATION_PROCESSOR = "net.msrandom:java-expect-actual-processor:1.0.9"
const val JAVA_CLASS_EXTENSIONS_ANNOTATIONS = "net.msrandom:class-extension-annotations:1.0.0"
const val JAVA_CLASS_EXTENSIONS_PROCESSOR = "net.msrandom:java-class-extensions-processor:1.0.1"
const val KOTLIN_MULTIPLATFORM_STUB_PLUGIN = "net.msrandom:kmp-actual-stubs-compiler-plugin:0.1.2"

context(Project)
private fun SourceSet.extendConfigurations(dependency: SourceSet, common: Boolean) {
    val apiBucket = dependency.commonBucketConfigurationName(JavaPlugin.API_CONFIGURATION_NAME)
    val compileOnlyApiBucket = dependency.commonBucketConfigurationName(JavaPlugin.COMPILE_ONLY_API_CONFIGURATION_NAME)
    val implementationBucket = dependency.commonBucketConfigurationName(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME)
    val compileOnlyBucket = dependency.commonBucketConfigurationName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME)

    if (common) {
        project.extend(
            dependency.commonBucketConfigurationName(JavaPlugin.API_CONFIGURATION_NAME),
            apiBucket,
        )

        project.extend(
            dependency.commonBucketConfigurationName(JavaPlugin.COMPILE_ONLY_API_CONFIGURATION_NAME),
            compileOnlyApiBucket,
        )

        project.extend(
            dependency.commonBucketConfigurationName(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME),
            implementationBucket,
        )

        project.extend(
            dependency.commonBucketConfigurationName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME),
            compileOnlyBucket,
        )
    } else {
        project.extend(apiConfigurationName, apiBucket)
        project.extend(compileOnlyApiConfigurationName, compileOnlyApiBucket)
        project.extend(implementationConfigurationName, implementationBucket)
        project.extend(compileOnlyConfigurationName, compileOnlyBucket)
    }

    project.extend(runtimeOnlyConfigurationName, dependency.runtimeOnlyConfigurationName)

    project.extend(localRuntimeConfigurationName, dependency.localRuntimeConfigurationName)
    project.extend(localImplementationConfigurationName, dependency.localImplementationConfigurationName)

    project.extend(externalRuntimeConfigurationName, dependency.externalRuntimeConfigurationName)
    project.extend(externalCompileConfigurationName, dependency.externalCompileConfigurationName)
    project.extend(externalApiConfigurationName, dependency.externalApiConfigurationName)

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

    project.extend(
        modConfigurationName(localImplementationConfigurationName),
        modConfigurationName(dependency.localImplementationConfigurationName),
    )
}

/**
 * Depend on the api configuration of [dependency]
 */
context(Project)
internal fun CommonCompilation.addClasspathDependency(dependency: CommonCompilation) {
    println("(classpath dependency) $this -> $dependency")

    dependencies.add(sourceSet.compileOnlyConfigurationName, project().apply {
        capabilities {
            if (dependency.capabilitySuffix.isPresent) {
                requireFeature(dependency.capabilitySuffix)
            }
        }

        attributes(dependency::baseAttributes)
    })

    if (!isTest && !dependency.isTest) {
        artifacts {
            add(sourceSet.apiElementsConfigurationName, tasks.named(dependency.sourceSet.jarTaskName))
        }

        val dependencyProvider = configurations.named(dependency.sourceSet.apiElementsConfigurationName)

        configurations.named(sourceSet.apiElementsConfigurationName) {
            dependencies.addAllLater(dependencyProvider.map(Configuration::getAllDependencies))
            dependencyConstraints.addAllLater(dependencyProvider.map(Configuration::getAllDependencyConstraints))
        }
    }

    accessWideners.from(dependency.accessWideners)
    mixins.from(dependency.mixins)
}

context(Project)
private fun TargetCompilation<*>.extendFromDependency(dependency: TargetCompilation<*>) {
    if (!isTest && !dependency.isTest) {
        for (name in listOf(
            SourceSet::getApiElementsConfigurationName,
            SourceSet::getRuntimeElementsConfigurationName
        )) {
            val dependencyProvider = configurations.named(name(dependency.sourceSet))

            configurations.named(name(sourceSet)) {
                dependencies.addAllLater(dependencyProvider.map(Configuration::getAllDependencies))
                dependencyConstraints.addAllLater(dependencyProvider.map(Configuration::getAllDependencyConstraints))
            }
        }
    }

    includeBucketConfiguration.configure {
        extendsFrom(dependency.includeBucketConfiguration)
    }

    accessWideners.from(dependency.accessWideners)
    mixins.from(dependency.mixins)
}

/**
 * Depend on the variant of [dependency]
 */
context(Project)
internal fun TargetCompilation<*>.addClasspathDependency(dependency: TargetCompilation<*>) {
    println("(classpath dependency) $this -> $dependency")

    dependencies.add(sourceSet.localImplementationConfigurationName, project().apply {
        capabilities {
            if (dependency.capabilitySuffix.isPresent) {
                requireFeature(dependency.capabilitySuffix)
            }
        }

        attributes(dependency::baseAttributes)
    })

    val apiElementsClasses = configurations.named(dependency.sourceSet.apiElementsConfigurationName).flatMap {
        it.outgoing.variants.named(LibraryElements.CLASSES)
    }

    val runtimeElementsClasses = configurations.named(dependency.sourceSet.runtimeElementsConfigurationName).flatMap {
        it.outgoing.variants.named(LibraryElements.CLASSES)
    }

    val runtimeElementsResources = configurations.named(dependency.sourceSet.runtimeElementsConfigurationName).flatMap {
        it.outgoing.variants.named(LibraryElements.RESOURCES)
    }

    configurations.named(sourceSet.apiElementsConfigurationName) {
        outgoing.variants.named(LibraryElements.CLASSES) {
            artifacts.addAllLater(apiElementsClasses.map(ConfigurationVariant::getArtifacts))
        }
    }

    configurations.named(sourceSet.runtimeElementsConfigurationName) {
        outgoing.variants.named(LibraryElements.CLASSES) {
            artifacts.addAllLater(runtimeElementsClasses.map(ConfigurationVariant::getArtifacts))
        }

        outgoing.variants.named(LibraryElements.RESOURCES) {
            artifacts.addAllLater(runtimeElementsResources.map(ConfigurationVariant::getArtifacts))
        }
    }

    extendFromDependency(dependency)
}

context(Project)
internal fun TargetCompilation<*>.addDataClasspathDependency(dependency: TargetCompilation<*>) {
    println("(data classpath dependency) $this -> $dependency")

    dependencies.add(sourceSet.localImplementationConfigurationName, project().apply {
        capabilities {
            if (dependency.capabilitySuffix.isPresent) {
                requireFeature(dependency.capabilitySuffix)
            }
        }

        attributes {
            attribute(WITHOUT_DATA_ATTRIBUTE, true)
            dependency.baseAttributes(this)
        }
    })

    val apiElementsClasses = configurations.named(dependency.sourceSet.apiElementsConfigurationName).flatMap {
        it.outgoing.variants.named(LibraryElements.CLASSES)
    }

    val runtimeElementsClasses = configurations.named(dependency.sourceSet.runtimeElementsConfigurationName).flatMap {
        it.outgoing.variants.named(LibraryElements.CLASSES)
    }

    val runtimeElementsResources = configurations.named(dependency.sourceSet.runtimeElementsConfigurationName).flatMap {
        it.outgoing.variants.named(LibraryElements.RESOURCES)
    }

    configurations.named(sourceSet.apiElementsConfigurationName) {
        outgoing.variants.named(LibraryElements.CLASSES) {
            artifacts.addAllLater(apiElementsClasses.map(ConfigurationVariant::getArtifacts))
        }
    }

    configurations.named(sourceSet.runtimeElementsConfigurationName) {
        outgoing.variants.named(LibraryElements.CLASSES) {
            artifacts.addAllLater(runtimeElementsClasses.map(ConfigurationVariant::getArtifacts))
        }

        outgoing.variants.named(LibraryElements.RESOURCES) {
            artifacts.addAllLater(runtimeElementsResources.map(ConfigurationVariant::getArtifacts))
        }
    }

    extendFromDependency(dependency)
}

/**
 * Include [dependency] to be compiled alongside the current source, allowing Java platform annotations and Kotlin Multiplatform to function
 */
context(Project)
internal fun CompilationInternal.addSourceDependency(dependency: CommonCompilation) {
    println("(source dependency) $this -> $dependency")

    sourceSet.extension<SourceSetStaticLinkageInfo>().link(dependency.sourceSet)

    sourceSet.extendConfigurations(dependency.sourceSet, this is CommonCompilation)

    project.dependencies.add(sourceSet.compileOnlyConfigurationName, JAVA_CLASS_EXTENSIONS_ANNOTATIONS)
    project.dependencies.add(sourceSet.annotationProcessorConfigurationName, JAVA_CLASS_EXTENSIONS_PROCESSOR)
    project.dependencies.add(sourceSet.annotationProcessorConfigurationName, JAVA_EXPECT_ACTUAL_ANNOTATION_PROCESSOR)

    accessWideners.from(dependency.accessWideners)
    mixins.from(dependency.mixins)
}
