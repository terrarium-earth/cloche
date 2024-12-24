package earth.terrarium.cloche.target

import org.gradle.api.Action
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

interface FabricTarget : MinecraftTarget {
    /**
     * null if neither runnable nor a source set
     * Runnable if only runnable but not a source set
     * RunnableCompilation if runnable and a source set
     */
    override val client: Runnable?

    fun client(action: Action<RunnableCompilation>? = null)
    fun includedClient(action: Action<Runnable>? = null)
}

interface ForgeLikeTarget : MinecraftTarget {
    fun client(action: Action<Runnable>)
}

interface ForgeTarget : ForgeLikeTarget {
    val userdevClassifier: Property<String>
        @Input
        @Optional
        get
}

interface NeoforgeTarget : ForgeLikeTarget
