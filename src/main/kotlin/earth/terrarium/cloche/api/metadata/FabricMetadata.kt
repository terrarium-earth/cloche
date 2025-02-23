package earth.terrarium.cloche.api.metadata

import earth.terrarium.cloche.api.metadata.ModMetadata.Dependency
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import javax.inject.Inject

interface FabricMetadata {
    val entrypoints: MapProperty<String, List<Entrypoint>>
        @Input get

    val objectFactory: ObjectFactory
        @Inject get

    val dependencies: ListProperty<Dependency>
        @Nested
        get

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

    fun dependency(action: Action<Dependency>) =
        dependencies.add(objectFactory.newInstance(Dependency::class.java).also(action::execute))

    interface Entrypoint {
        val value: Property<String>
            @Input get

        val adapter: Property<String>
            @Optional
            @Input
            get
    }
}
