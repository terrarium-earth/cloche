package earth.terrarium.cloche.target

import org.gradle.api.Action
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

interface FabricTarget : MinecraftTarget {
    override val client: RunnableCompilation

    fun noClient()

    fun includeClient()

    fun client(action: Action<RunnableCompilation>)
}

interface ForgeLikeTarget : MinecraftTarget {
    fun client(action: Action<Runnable>) = action.execute(client)
}

interface ForgeTarget : ForgeLikeTarget {
    val userdevClassifier: Property<String>
        @Input
        @Optional
        get
}

interface NeoforgeTarget : ForgeLikeTarget