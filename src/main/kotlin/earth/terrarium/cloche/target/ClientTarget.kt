package earth.terrarium.cloche.target

import org.gradle.api.Action

interface MinecraftClientTarget : MinecraftTarget {
    override val client: RunnableCompilation

    fun noClient()

    fun includeClient()

    fun client(action: Action<RunnableCompilation>)
}

interface MinecraftNoClientTarget : MinecraftTarget {
    fun client(action: Action<Runnable>) = action.execute(client)
}
