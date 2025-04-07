package earth.terrarium.cloche.api.target.compilation

import org.gradle.api.artifacts.dsl.DependencyCollector
import org.gradle.api.plugins.jvm.JvmComponentDependencies
import org.gradle.api.provider.Provider
import javax.inject.Inject

@Suppress("UnstableApiUsage")
@JvmDefaultWithoutCompatibility
abstract class ClocheDependencyHandler @Inject constructor(private val minecraftVersion: Provider<String>) : JvmComponentDependencies {
    abstract val api: DependencyCollector
    abstract val compileOnlyApi: DependencyCollector

    abstract val modApi: DependencyCollector
    abstract val modCompileOnlyApi: DependencyCollector
    abstract val modImplementation: DependencyCollector
    abstract val modRuntimeOnly: DependencyCollector
    abstract val modCompileOnly: DependencyCollector

    fun fabricApi(apiVersion: String) {
        modImplementation.add(minecraftVersion.map {
            fabricApiDependency(apiVersion, it)
        })
    }

    fun fabricApi(apiVersion: Provider<String>) {
        addLazyFabricApi(apiVersion, minecraftVersion)
    }

    fun fabricApi(apiVersion: String, minecraftVersion: String) {
        modImplementation.add(fabricApiDependency(apiVersion, minecraftVersion))
    }

    fun fabricApi(apiVersion: Provider<String>, minecraftVersion: String) {
        modImplementation.add(apiVersion.map {
            fabricApiDependency(it, minecraftVersion)
        })
    }

    fun fabricApi(apiVersion: String, minecraftVersion: Provider<String>) {
        modImplementation.add(minecraftVersion.orElse(this.minecraftVersion).map {
            fabricApiDependency(apiVersion, it)
        })
    }

    fun fabricApi(apiVersion: Provider<String>, minecraftVersion: Provider<String>) {
        addLazyFabricApi(apiVersion, minecraftVersion.orElse(this.minecraftVersion))
    }

    private fun addLazyFabricApi(apiVersion: Provider<String>, minecraftVersion: Provider<String>) {
        modImplementation.add(
            apiVersion.zip(minecraftVersion, ::Pair)
                .map { (apiVersion, minecraftVersion) ->
                    fabricApiDependency(apiVersion, minecraftVersion)
                }
        )
    }

    private fun fabricApiDependency(apiVersion: String, minecraftVersion: String) =
        module("net.fabricmc.fabric-api", "fabric-api", "$apiVersion+$minecraftVersion")
}
