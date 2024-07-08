package earth.terrarium.cloche

import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderConvertible

class ClocheDependencyHandler(
    private val project: Project,

    apiConfigurationName: String,
    implementationConfigurationName: String,
    runtimeOnlyConfigurationName: String,
    compileOnlyConfigurationName: String,

    modApiConfigurationName: String,
    modImplementationConfigurationName: String,
    modRuntimeOnlyConfigurationName: String,
    modCompileOnlyConfigurationName: String,
) {
    val api = ConfigurationHandler(apiConfigurationName)
    val modApi = ConfigurationHandler(modApiConfigurationName)
    val implementation = ConfigurationHandler(implementationConfigurationName)
    val modImplementation = ConfigurationHandler(modImplementationConfigurationName)
    val runtimeOnly = ConfigurationHandler(runtimeOnlyConfigurationName)
    val modRuntimeOnly = ConfigurationHandler(modRuntimeOnlyConfigurationName)
    val compileOnly = ConfigurationHandler(compileOnlyConfigurationName)
    val modCompileOnly = ConfigurationHandler(modCompileOnlyConfigurationName)

    inner class ConfigurationHandler(val configurationName: String) {
        operator fun invoke(dependencyNotation: Any) = project.dependencies.add(configurationName, dependencyNotation)
        operator fun invoke(dependencyNotation: Provider<*>) = project.dependencies.addProvider(configurationName, dependencyNotation)
        operator fun invoke(dependencyNotation: ProviderConvertible<*>) = project.dependencies.addProvider(configurationName, dependencyNotation.asProvider())
        operator fun invoke(dependencyNotation: String, configure: Closure<*>) = project.dependencies.add(configurationName, dependencyNotation.also { project.configure(it, configure) })

        operator fun invoke(dependencyNotation: String, configure: Action<ExternalModuleDependency>) =
            (project.dependencies.add(configurationName, (project.dependencies.create(dependencyNotation) as ExternalModuleDependency).also { configure.execute(it) }))

        operator fun invoke(
            group: String, name: String, version: String? = null, configuration: String? = null, classifier: String? = null, ext: String? = null, configure: Action<ExternalModuleDependency>? = null
        ) = project.dependencies.add(configurationName, project.dependencies.create(buildString {
            append(group).append(':').append(name).append(':')

            if (version != null) append(version)
            if (classifier != null) append(':').append(classifier)
            if (ext != null) append('@').append(ext)
        }).apply {
            this as ExternalModuleDependency

            if (configuration != null) {
                targetConfiguration = configuration
            }

            configure?.execute(this)
        }) as ModuleDependency

        operator fun <T : Dependency> invoke(dependency: T, configure: Action<T>) = (project.dependencies.add(configurationName, dependency.also(configure::execute)))
        operator fun <T : Dependency> invoke(dependency: T, configure: Closure<*>) = project.dependencies.add(configurationName, dependency.also { project.configure(it, configure) })

        operator fun <T : Dependency> invoke(dependency: Provider<T>, configure: Action<T>) = project.dependencies.addProvider(
            configurationName,
            dependency.map {
                configure.execute(it)
                it
            },
        )

        operator fun <T : Dependency> invoke(dependency: Provider<T>, configure: Closure<*>) = project.dependencies.addProvider(
            configurationName,
            dependency.map {
                project.configure(it, configure)
                it
            },
        )

        operator fun <T : Dependency> invoke(dependency: ProviderConvertible<T>, configure: Action<T>) =
            project.dependencies.addProvider(
                configurationName,
                dependency.asProvider().map {
                    configure.execute(it)
                    it
                },
            )

        operator fun <T : Dependency> invoke(dependency: ProviderConvertible<T>, configure: Closure<*>) = project.dependencies.addProvider(
            configurationName,
            dependency.asProvider().map {
                project.configure(it, configure)
                it
            },
        )
    }
}
