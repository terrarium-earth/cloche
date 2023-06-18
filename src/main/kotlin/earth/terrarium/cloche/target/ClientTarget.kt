package earth.terrarium.cloche.target

import org.gradle.api.Action
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

interface ClientTarget {
    val clientMode: Property<ClientMode>
        @Optional
        @Input
        get

    fun client(action: Action<Compilation>)
}
