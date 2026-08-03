package earth.terrarium.cloche.api.target.compilation

import earth.terrarium.cloche.REMAP_SOURCE_NAMESPACE_ATTRIBUTE
import earth.terrarium.cloche.api.attributes.IncludeTransformationStateAttribute
import org.gradle.api.artifacts.ExternalModuleDependency
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
    abstract val externalRuntime: DependencyCollector
    abstract val externalCompile: DependencyCollector
    abstract val externalApi: DependencyCollector

    abstract val modApi: DependencyCollector
    abstract val modCompileOnlyApi: DependencyCollector
    abstract val modImplementation: DependencyCollector
    abstract val modRuntimeOnly: DependencyCollector
    abstract val modCompileOnly: DependencyCollector
    abstract val modLocalRuntime: DependencyCollector
    abstract val modLocalImplementation: DependencyCollector
    abstract val remapClasspath: DependencyCollector

    val skipIncludeTransformation: SkipIncludeTransformationDependencyModifier = objectFactory.newInstance<SkipIncludeTransformationDependencyModifier>()
    val extractIncludes: ExtractIncludesDependencyModifier = objectFactory.newInstance<ExtractIncludesDependencyModifier>()
    val stripIncludes: StripIncludesDependencyModifier = objectFactory.newInstance<StripIncludesDependencyModifier>()
    internal val remapSourceNamespaces: Set<String>
        get() = remapSourceNamespaceDeclarations

    private val remapSourceNamespaceDeclarations = linkedSetOf<String>()
    private var remapSourceNamespaceDeclarationLock: RemapSourceNamespaceDeclarationLock? = null

    internal fun lockRemapSourceNamespaceDeclarations(compilationName: String, configurationName: String) {
        if (remapSourceNamespaceDeclarationLock == null) {
            remapSourceNamespaceDeclarationLock = RemapSourceNamespaceDeclarationLock(compilationName, configurationName)
        }
    }

    private fun recordRemapSourceNamespace(sourceNamespace: String) {
        if (sourceNamespace in remapSourceNamespaceDeclarations) {
            return
        }

        val lock = remapSourceNamespaceDeclarationLock
        if (lock != null) {
            error(
                "Cannot declare remapFrom(\"$sourceNamespace\") for Compilation ${lock.compilationName} " +
                    "after related configuration ${lock.configurationName} has begun resolving"
            )
        }

        remapSourceNamespaceDeclarations.add(sourceNamespace)
    }

    private data class RemapSourceNamespaceDeclarationLock(
        val compilationName: String,
        val configurationName: String,
    )

    fun remapFrom(sourceNamespace: String): DependencyModifier {
        require(sourceNamespace.isNotBlank()) {
            "remapFrom source namespace must not be blank"
        }
        require(sourceNamespace != "named") {
            "remapFrom(\"named\") is a no-op; declare the dependency without remapFrom instead"
        }

        return objectFactory.newInstance<RemapSourceNamespaceDependencyModifier>(sourceNamespace, this)
    }

    fun fabricApi(apiVersion: String) {
        modApi.add(minecraftVersion.map {
            fabricApiDependency(apiVersion, it)
        })
    }

    fun fabricApi(apiVersion: Provider<String>) {
        addLazyFabricApi(apiVersion, minecraftVersion)
    }

    fun fabricApi(apiVersion: String, minecraftVersion: String) {
        modApi.add(fabricApiDependency(apiVersion, minecraftVersion))
    }

    fun fabricApi(apiVersion: Provider<String>, minecraftVersion: String) {
        modApi.add(apiVersion.map {
            fabricApiDependency(it, minecraftVersion)
        })
    }

    fun fabricApi(apiVersion: String, minecraftVersion: Provider<String>) {
        modApi.add(minecraftVersion.orElse(this.minecraftVersion).map {
            fabricApiDependency(apiVersion, it)
        })
    }

    fun fabricApi(apiVersion: Provider<String>, minecraftVersion: Provider<String>) {
        addLazyFabricApi(apiVersion, minecraftVersion.orElse(this.minecraftVersion))
    }

    private fun addLazyFabricApi(apiVersion: Provider<String>, minecraftVersion: Provider<String>) {
        modApi.add(
            apiVersion.zip(minecraftVersion, ::Pair)
                .map { (apiVersion, minecraftVersion) ->
                    fabricApiDependency(apiVersion, minecraftVersion)
                }
        )
    }

    private fun fabricApiDependency(apiVersion: String, minecraftVersion: String) =
        dependencyFactory.create("net.fabricmc.fabric-api", "fabric-api", "$apiVersion+$minecraftVersion")

    abstract class RemapSourceNamespaceDependencyModifier @Inject constructor(
        private val sourceNamespace: String,
        private val dependencyHandler: ClocheDependencyHandler,
    ) : DependencyModifier() {
        override fun modifyImplementation(dependency: ModuleDependency) {
            require(dependency is ExternalModuleDependency) {
                "remapFrom(\"$sourceNamespace\") only supports external module dependencies; got ${dependency::class.java.name}"
            }

            dependencyHandler.recordRemapSourceNamespace(sourceNamespace)

            dependency.attributes {
                attribute(REMAP_SOURCE_NAMESPACE_ATTRIBUTE, sourceNamespace)
            }
        }
    }

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
