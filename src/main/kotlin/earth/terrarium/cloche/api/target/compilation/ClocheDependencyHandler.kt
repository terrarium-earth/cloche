package earth.terrarium.cloche.api.target.compilation

import earth.terrarium.cloche.api.attributes.IncludeTransformationStateAttribute
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.dsl.DependencyCollector
import org.gradle.api.artifacts.dsl.DependencyModifier
import org.gradle.api.plugins.jvm.JvmComponentDependencies
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.newInstance
import javax.inject.Inject

@Suppress("UnstableApiUsage")
abstract class ClocheDependencyHandler @Inject constructor(private val minecraftVersion: Provider<String>) : JvmComponentDependencies {
    abstract val include: DependencyCollector

    abstract val api: DependencyCollector
    abstract val compileOnlyApi: DependencyCollector
    abstract val localRuntime: DependencyCollector
    abstract val localImplementation: DependencyCollector

    abstract val modApi: DependencyCollector
    abstract val modCompileOnlyApi: DependencyCollector
    abstract val modImplementation: DependencyCollector
    abstract val modRuntimeOnly: DependencyCollector
    abstract val modCompileOnly: DependencyCollector
    abstract val modLocalRuntime: DependencyCollector
    abstract val modLocalImplementation: DependencyCollector

    val skipIncludeTransformation: SkipIncludeTransformationDependencyModifier = objectFactory.newInstance<SkipIncludeTransformationDependencyModifier>()
    val extractIncludes: ExtractIncludesDependencyModifier = objectFactory.newInstance<ExtractIncludesDependencyModifier>()
    val stripIncludes: StripIncludesDependencyModifier = objectFactory.newInstance<StripIncludesDependencyModifier>()

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
        dependencyFactory.create("net.fabricmc.fabric-api", "fabric-api", "$apiVersion+$minecraftVersion")

    abstract class SkipIncludeTransformationDependencyModifier : DependencyModifier() {
        override fun modifyImplementation(dependency: ModuleDependency) {
            dependency.attributes {
                attribute(IncludeTransformationStateAttribute.ATTRIBUTE, IncludeTransformationStateAttribute.None)
            }
        }
    }

    abstract class ExtractIncludesDependencyModifier : DependencyModifier() {
        override fun modifyImplementation(dependency: ModuleDependency) {
            dependency.attributes {
                attribute(IncludeTransformationStateAttribute.ATTRIBUTE, IncludeTransformationStateAttribute.Extracted)
            }
        }
    }

    abstract class StripIncludesDependencyModifier : DependencyModifier() {
        override fun modifyImplementation(dependency: ModuleDependency) {
            dependency.attributes {
                attribute(IncludeTransformationStateAttribute.ATTRIBUTE, IncludeTransformationStateAttribute.Stripped)
            }
        }
    }
}
