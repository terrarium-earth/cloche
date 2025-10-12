package earth.terrarium.cloche.api.target

import earth.terrarium.cloche.FABRIC
import earth.terrarium.cloche.FORGE
import earth.terrarium.cloche.NEOFORGE
import earth.terrarium.cloche.api.LazyConfigurable
import earth.terrarium.cloche.api.target.compilation.TargetSecondarySourceSets
import earth.terrarium.cloche.api.metadata.FabricMetadata
import earth.terrarium.cloche.api.metadata.ForgeMetadata
import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.artifacts.dsl.DependencyCollector

@JvmDefaultWithoutCompatibility
interface FabricTarget : MinecraftTarget {
    override val metadata: FabricMetadata

    override val loaderName: String
        get() = FABRIC

    val client: LazyConfigurable<TargetSecondarySourceSets>

    fun includedClient()

    fun metadata(action: Action<FabricMetadata>) = action.execute(metadata)
}

@JvmDefaultWithoutCompatibility
interface ForgeLikeTarget : MinecraftTarget {
    val legacyClasspath: DependencyCollector
        @Incubating get

    val dataLegacyClasspath: DependencyCollector
        @Incubating get

    val testLegacyClasspath: DependencyCollector
        @Incubating get

    override val metadata: ForgeMetadata

    fun metadata(action: Action<ForgeMetadata>) = action.execute(metadata)
}

@JvmDefaultWithoutCompatibility
interface ForgeTarget : ForgeLikeTarget {
    override val loaderName: String
        get() = FORGE
}

@JvmDefaultWithoutCompatibility
interface NeoforgeTarget : ForgeLikeTarget {
    override val loaderName: String
        get() = NEOFORGE
}
