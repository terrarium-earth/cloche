package earth.terrarium.cloche.target.common

import earth.terrarium.cloche.target.addCollectedDependencies
import earth.terrarium.cloche.target.compilation.*
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin

context(Project)
internal fun setupConfigurations(compilation: CommonCompilation) {
    val sourceSet = compilation.sourceSet

    configurations.dependencyScope(modConfigurationName(sourceSet.implementationConfigurationName)) {
        addCollectedDependencies(compilation.dependencyHandler.modImplementation)
    }

    configurations.dependencyScope(modConfigurationName(sourceSet.apiConfigurationName)) {
        addCollectedDependencies(compilation.dependencyHandler.modApi)
    }

    configurations.dependencyScope(modConfigurationName(sourceSet.compileOnlyConfigurationName)) {
        addCollectedDependencies(compilation.dependencyHandler.modCompileOnly)
    }

    configurations.dependencyScope(modConfigurationName(sourceSet.compileOnlyApiConfigurationName)) {
        addCollectedDependencies(compilation.dependencyHandler.modCompileOnlyApi)
    }

    configurations.dependencyScope(modConfigurationName(sourceSet.runtimeOnlyConfigurationName)) {
        addCollectedDependencies(compilation.dependencyHandler.modRuntimeOnly)
    }

    configurations.dependencyScope(modConfigurationName(sourceSet.localRuntimeConfigurationName)) {
        addCollectedDependencies(compilation.dependencyHandler.modLocalRuntime)
    }

    configurations.dependencyScope(modConfigurationName(sourceSet.localImplementationConfigurationName)) {
        addCollectedDependencies(compilation.dependencyHandler.modLocalImplementation)
    }

    configurations.dependencyScope(sourceSet.commonBucketConfigurationName(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME)) {
        addCollectedDependencies(compilation.dependencyHandler.implementation)
    }

    configurations.dependencyScope(sourceSet.commonBucketConfigurationName(JavaPlugin.API_CONFIGURATION_NAME)) {
        addCollectedDependencies(compilation.dependencyHandler.api)
    }

    configurations.dependencyScope(sourceSet.commonBucketConfigurationName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME)) {
        addCollectedDependencies(compilation.dependencyHandler.compileOnly)
    }

    configurations.dependencyScope(sourceSet.commonBucketConfigurationName(JavaPlugin.COMPILE_ONLY_API_CONFIGURATION_NAME)) {
        addCollectedDependencies(compilation.dependencyHandler.compileOnlyApi)
    }

    configurations.named(sourceSet.runtimeOnlyConfigurationName) {
        addCollectedDependencies(compilation.dependencyHandler.runtimeOnly)
    }

    configurations.named(sourceSet.localRuntimeConfigurationName) {
        addCollectedDependencies(compilation.dependencyHandler.localRuntime)
    }

    configurations.named(sourceSet.localImplementationConfigurationName) {
        addCollectedDependencies(compilation.dependencyHandler.localImplementation)
    }

    configurations.named(sourceSet.externalRuntimeConfigurationName) {
        addCollectedDependencies(compilation.dependencyHandler.externalRuntime)
    }

    configurations.named(sourceSet.externalCompileConfigurationName) {
        addCollectedDependencies(compilation.dependencyHandler.externalCompile)
    }

    configurations.named(sourceSet.externalApiConfigurationName) {
        addCollectedDependencies(compilation.dependencyHandler.externalApi)
    }

    configurations.named(sourceSet.annotationProcessorConfigurationName) {
        addCollectedDependencies(compilation.dependencyHandler.annotationProcessor)
    }
}
