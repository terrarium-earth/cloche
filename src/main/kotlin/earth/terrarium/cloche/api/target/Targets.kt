package earth.terrarium.cloche.api.target

import earth.terrarium.cloche.api.LazyConfigurable
import earth.terrarium.cloche.api.metadata.FabricMetadata
import earth.terrarium.cloche.api.metadata.ForgeMetadata
import earth.terrarium.cloche.api.target.compilation.FabricCompilation
import earth.terrarium.cloche.api.target.compilation.FabricIncludedClient
import earth.terrarium.cloche.api.target.compilation.ForgeCompilation
import earth.terrarium.cloche.api.target.compilation.FabricSecondarySourceSets
import org.gradle.api.Action

@JvmDefaultWithoutCompatibility
interface FabricTarget : MinecraftTarget, FabricSecondarySourceSets, FabricCompilation {
    override val metadata: FabricMetadata

    val client: LazyConfigurable<FabricSecondarySourceSets>
    val includedClient: LazyConfigurable<FabricIncludedClient>

    fun metadata(action: Action<FabricMetadata>) = action.execute(metadata)
}

@JvmDefaultWithoutCompatibility
interface ForgeLikeTarget : MinecraftTarget, ForgeCompilation {
    override val data: LazyConfigurable<ForgeCompilation>
    override val test: LazyConfigurable<ForgeCompilation>

    override val metadata: ForgeMetadata

    fun metadata(action: Action<ForgeMetadata>) = action.execute(metadata)
}

interface ForgeTarget : ForgeLikeTarget
interface NeoforgeTarget : ForgeLikeTarget
