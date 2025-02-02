package earth.terrarium.cloche.metadata

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import javax.inject.Inject

abstract class FabricMetadata {
    abstract val entrypoints: MapProperty<String, List<Entrypoint>>
        @Input get

    abstract val objectFactory: ObjectFactory
        @Inject get

    fun entrypoint(name: String, value: String) = entrypoint(name) {
        it.value.set(value)
    }

    fun entrypoint(name: String, action: Action<Entrypoint>) =
        entrypoint(name, listOf(action))

    fun entrypoint(name: String, actions: List<Action<Entrypoint>>) {
        val entrypoints = actions.map {
            objectFactory.newInstance(Entrypoint::class.java).also(it::execute)
        }

        this.entrypoints.put(name, entrypoints)
    }
}

interface Entrypoint {
    val value: Property<String>
        @Input get

    val adapter: Property<String>
        @Optional
        @Input
        get
}
