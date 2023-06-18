package earth.terrarium.cloche

import groovy.lang.Closure
import net.msrandom.minecraftcodev.remapper.dependency.RemappedDependency
import net.msrandom.minecraftcodev.remapper.dependency.remapped
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderConvertible

class ClocheDependencyHandler(
    private val project: Project,
    private val remapNamespace: String?,
    private val mappingsConfiguration: String,

    apiConfigurationName: String,
    implementationConfigurationName: String,
    runtimeOnlyConfigurationName: String,
    compileOnlyConfigurationName: String
) {
    val api = ConfigurationHandler(apiConfigurationName)
    val implementation = ConfigurationHandler(implementationConfigurationName)
    val runtimeOnly = ConfigurationHandler(runtimeOnlyConfigurationName)
    val compileOnly = ConfigurationHandler(compileOnlyConfigurationName)

    private fun remap(dependencyNotation: Any) = project.dependencies.create(dependencyNotation).let {
        if (it is ModuleDependency) {
            it.remapped(remapNamespace, mappingsConfiguration = mappingsConfiguration)
        } else {
            it
        }
    }

    private fun remapProvider(dependencyNotation: Provider<*>) = dependencyNotation.map {
        if (it is ModuleDependency) {
            it.remapped(remapNamespace, mappingsConfiguration = mappingsConfiguration)
        } else {
            it
        }
    }

    private fun remap(dependency: ModuleDependency) = dependency.remapped(remapNamespace, mappingsConfiguration = mappingsConfiguration)

    inner class ConfigurationHandler(val configurationName: String) {
        operator fun invoke(dependencyNotation: Any) = project.dependencies.add(configurationName, remap(dependencyNotation))

        operator fun invoke(dependencyNotation: Provider<*>) = project.dependencies.addProvider(configurationName, remapProvider(dependencyNotation))

        operator fun invoke(dependencyNotation: ProviderConvertible<*>) = project.dependencies.addProvider(configurationName, remapProvider(dependencyNotation.asProvider()))

        operator fun invoke(dependencyNotation: String, configure: Closure<*>) = project.dependencies.add(configurationName, remap(dependencyNotation.also { project.configure(it, configure) }))

        operator fun invoke(dependencyNotation: String, configure: Action<ExternalModuleDependency>) =
            (project.dependencies.add(configurationName, remap((project.dependencies.create(dependencyNotation) as ExternalModuleDependency).also { configure.execute(it) })))

        operator fun invoke(
            group: String, name: String, version: String? = null, configuration: String? = null, classifier: String? = null, ext: String? = null, configure: Action<ExternalModuleDependency>? = null
        ) = project.dependencies.add(configurationName, remap(DefaultExternalModuleDependency(group, name, version.orEmpty(), configuration.orEmpty()).apply {
            if (classifier != null || ext != null) {
                artifact {
                    it.classifier = classifier.orEmpty()
                    it.extension = ext.orEmpty()
                }
            }

            configure?.execute(this)
        })) as ModuleDependency

        operator fun <T : Dependency> invoke(dependency: T, configure: Action<T>) = (project.dependencies.add(configurationName, remap(dependency.also(configure::execute))))
        operator fun <T : Dependency> invoke(dependency: T, configure: Closure<*>) = project.dependencies.add(configurationName, remap(dependency.also { project.configure(it, configure) }))

        operator fun <T : Dependency> invoke(dependency: Provider<T>, configure: Action<T>) = (project.dependencies.addProvider(configurationName, remapProvider(dependency.map {
            configure.execute(it)
            it
        })))

        operator fun <T : Dependency> invoke(dependency: Provider<T>, configure: Closure<*>) = project.dependencies.addProvider(
            configurationName, remapProvider(dependency.map {
                project.configure(it, configure)
                it
            })
        )

        operator fun <T : Dependency> invoke(dependency: ProviderConvertible<T>, configure: Action<T>) = (project.dependencies.addProvider(configurationName, remapProvider(dependency.asProvider().map {
            configure.execute(it)
            it
        })))

        operator fun <T : Dependency> invoke(dependency: ProviderConvertible<T>, configure: Closure<*>) = project.dependencies.addProvider(
            configurationName, remapProvider(dependency.asProvider().map {
                project.configure(it, configure)
                it
            })
        )
    }
}
