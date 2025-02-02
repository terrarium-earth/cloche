package earth.terrarium.cloche.target

import earth.terrarium.cloche.metadata.FabricMetadata
import earth.terrarium.cloche.metadata.ForgeMetadata
import org.gradle.api.Action

interface FabricTarget : MinecraftTarget<FabricMetadata> {
    fun client(action: Action<RunnableCompilation>? = null)
    fun includedClient(action: Action<Runnable>? = null)
}

interface ForgeLikeTarget : MinecraftTarget<ForgeMetadata> {
    fun client(action: Action<Runnable>?)
    fun client() = client(null)
}

interface ForgeTarget : ForgeLikeTarget
interface NeoforgeTarget : ForgeLikeTarget
