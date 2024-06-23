package earth.terrarium.cloche

import groovy.lang.Closure
import net.msrandom.minecraftcodev.includes.dependency.extractIncludes
import net.msrandom.minecraftcodev.mixins.dependency.skipMixins
import net.msrandom.minecraftcodev.remapper.dependency.remapped
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ModuleDependency
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
    val api = ConfigurationHandler(apiConfigurationName, modded = false, inCompileClasspath = true)
    val modApi = ConfigurationHandler(apiConfigurationName, modded = true, inCompileClasspath = true)
    val implementation = ConfigurationHandler(implementationConfigurationName, modded = false, inCompileClasspath = true)
    val modImplementation = ConfigurationHandler(implementationConfigurationName, modded = true, inCompileClasspath = true)
    val runtimeOnly = ConfigurationHandler(runtimeOnlyConfigurationName, modded = false, inCompileClasspath = false)
    val modRuntimeOnly = ConfigurationHandler(runtimeOnlyConfigurationName, modded = true, inCompileClasspath = false)
    val compileOnly = ConfigurationHandler(compileOnlyConfigurationName, modded = false, inCompileClasspath = true)
    val modCompileOnly = ConfigurationHandler(compileOnlyConfigurationName, modded = true, inCompileClasspath = true)

    inner class ConfigurationHandler(val configurationName: String, private val modded: Boolean, private val inCompileClasspath: Boolean) {
        private fun processMod(dependencyNotation: Any) = if (modded) {
            project.dependencies.create(dependencyNotation).let {
                if (it is ModuleDependency) {
                    it
                        .extractIncludes
                        .remapped(remapNamespace, mappingsConfiguration = mappingsConfiguration)
                        //.skipMixins
                } else {
                    it
                }
            }
        } else {
            dependencyNotation
        }

        private fun processModProvider(dependencyNotation: Provider<*>) = if (modded) {
            dependencyNotation.map {
                val dependency = project.dependencies.create(it)

                if (dependency is ModuleDependency) {
                    dependency
                        .extractIncludes
                        .remapped(remapNamespace, mappingsConfiguration = mappingsConfiguration)
                        //.skipMixins
                } else {
                    dependency
                }
            }
        } else {
            dependencyNotation
        }

        private fun processMod(dependency: ModuleDependency) = if (modded) {
            dependency
                .extractIncludes
                .remapped(remapNamespace, mappingsConfiguration = mappingsConfiguration)
                //.skipMixins
        } else {
            dependency
        }

        operator fun invoke(dependencyNotation: Any) = project.dependencies.add(configurationName, processMod(dependencyNotation))
        operator fun invoke(dependencyNotation: Provider<*>) = project.dependencies.addProvider(configurationName, processModProvider(dependencyNotation))
        operator fun invoke(dependencyNotation: ProviderConvertible<*>) = project.dependencies.addProvider(configurationName, processModProvider(dependencyNotation.asProvider()))
        operator fun invoke(dependencyNotation: String, configure: Closure<*>) = project.dependencies.add(configurationName, processMod(dependencyNotation.also { project.configure(it, configure) }))

        operator fun invoke(dependencyNotation: String, configure: Action<ExternalModuleDependency>) =
            (project.dependencies.add(configurationName, processMod((project.dependencies.create(dependencyNotation) as ExternalModuleDependency).also { configure.execute(it) })))

        operator fun invoke(
            group: String, name: String, version: String? = null, configuration: String? = null, classifier: String? = null, ext: String? = null, configure: Action<ExternalModuleDependency>? = null
        ) = project.dependencies.add(configurationName, processMod(project.dependencies.create(buildString {
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
        })) as ModuleDependency

        operator fun <T : Dependency> invoke(dependency: T, configure: Action<T>) = (project.dependencies.add(configurationName, processMod(dependency.also(configure::execute))))
        operator fun <T : Dependency> invoke(dependency: T, configure: Closure<*>) = project.dependencies.add(configurationName, processMod(dependency.also { project.configure(it, configure) }))

        operator fun <T : Dependency> invoke(dependency: Provider<T>, configure: Action<T>) = (project.dependencies.addProvider(configurationName, processModProvider(dependency.map {
            configure.execute(it)
            it
        })))

        operator fun <T : Dependency> invoke(dependency: Provider<T>, configure: Closure<*>) = project.dependencies.addProvider(
            configurationName, processModProvider(dependency.map {
                project.configure(it, configure)
                it
            })
        )

        operator fun <T : Dependency> invoke(dependency: ProviderConvertible<T>, configure: Action<T>) =
            (project.dependencies.addProvider(configurationName, processModProvider(dependency.asProvider().map {
                configure.execute(it)
                it
            })))

        operator fun <T : Dependency> invoke(dependency: ProviderConvertible<T>, configure: Closure<*>) = project.dependencies.addProvider(
            configurationName, processModProvider(dependency.asProvider().map {
                project.configure(it, configure)
                it
            })
        )
    }
}
