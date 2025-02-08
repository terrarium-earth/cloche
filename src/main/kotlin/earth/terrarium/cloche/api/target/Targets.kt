package earth.terrarium.cloche.api.target

import earth.terrarium.cloche.FABRIC
import earth.terrarium.cloche.FORGE
import earth.terrarium.cloche.NEOFORGE
import earth.terrarium.cloche.api.LazyConfigurable
import earth.terrarium.cloche.api.target.compilation.TargetSecondarySourceSets
import earth.terrarium.cloche.api.metadata.FabricMetadata
import earth.terrarium.cloche.api.metadata.ForgeMetadata

@JvmDefaultWithoutCompatibility
interface FabricTarget : MinecraftTarget<FabricMetadata> {
    override val loaderName: String
        get() = FABRIC

    val client: LazyConfigurable<TargetSecondarySourceSets>

    fun includedClient()
}

@JvmDefaultWithoutCompatibility
interface ForgeLikeTarget : MinecraftTarget<ForgeMetadata>

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
