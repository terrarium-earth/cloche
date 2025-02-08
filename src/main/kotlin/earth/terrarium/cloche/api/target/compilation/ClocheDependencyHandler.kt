package earth.terrarium.cloche.api.target.compilation

import org.gradle.api.artifacts.dsl.DependencyCollector
import org.gradle.api.plugins.jvm.JvmComponentDependencies

@Suppress("UnstableApiUsage")
@JvmDefaultWithoutCompatibility
interface ClocheDependencyHandler : JvmComponentDependencies {
    val api: DependencyCollector
    val compileOnlyApi: DependencyCollector

    val modApi: DependencyCollector
    val modCompileOnlyApi: DependencyCollector
    val modImplementation: DependencyCollector
    val modRuntimeOnly: DependencyCollector
    val modCompileOnly: DependencyCollector

    fun fabricApi(apiVersion: String) {
        modImplementation.add("net.fabricmc.fabric-api:fabric-api:$apiVersion")
    }
}
