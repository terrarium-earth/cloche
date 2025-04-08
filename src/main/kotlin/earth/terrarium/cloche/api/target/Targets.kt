package earth.terrarium.cloche.api.target

import earth.terrarium.cloche.FABRIC
import earth.terrarium.cloche.FORGE
import earth.terrarium.cloche.NEOFORGE
import earth.terrarium.cloche.api.LazyConfigurable
import earth.terrarium.cloche.api.target.compilation.TargetSecondarySourceSets
import earth.terrarium.cloche.api.metadata.FabricMetadata
import earth.terrarium.cloche.api.metadata.ForgeMetadata
import org.gradle.api.Action

@JvmDefaultWithoutCompatibility
interface FabricTarget : MinecraftTarget {
    val metadata: FabricMetadata

    override val loaderName: String
        get() = FABRIC

    val client: LazyConfigurable<TargetSecondarySourceSets>

    fun includedClient()

    fun metadata(configure: Action<FabricMetadata>) = configure.execute(metadata)
}

@JvmDefaultWithoutCompatibility
interface ForgeLikeTarget : MinecraftTarget {
    val metadata: ForgeMetadata

    fun metadata(configure: Action<ForgeMetadata>) = configure.execute(metadata)
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
