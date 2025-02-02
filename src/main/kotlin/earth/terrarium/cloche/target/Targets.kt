package earth.terrarium.cloche.target

import org.gradle.api.Action

interface FabricTarget : MinecraftTarget {
    fun client(action: Action<RunnableCompilation>? = null)
    fun includedClient(action: Action<Runnable>? = null)
}

interface ForgeLikeTarget : MinecraftTarget {
    fun client(action: Action<Runnable>?)
    fun client() = client(null)
}

interface ForgeTarget : ForgeLikeTarget
interface NeoforgeTarget : ForgeLikeTarget
