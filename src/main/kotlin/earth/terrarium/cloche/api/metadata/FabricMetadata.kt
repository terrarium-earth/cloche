package earth.terrarium.cloche.api.metadata

import org.gradle.api.Action
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

@JvmDefaultWithoutCompatibility
interface FabricMetadata : Metadata {
    var entrypoints: MutableMap<String, ListProperty<Entrypoint>>?
        @Input
        @Optional
        get

    val languageAdapters: MapProperty<String, String>
        @Input
        @Optional
        get

    fun entrypoint(name: String, value: String) = entrypoint(name) {
        it.value.set(value)
    }

    fun entrypoint(name: String, action: Action<Entrypoint>) =
        entrypoint(name, listOf(action))

    fun entrypoint(name: String, actions: List<Action<Entrypoint>>) {
        if (entrypoints == null) {
            entrypoints = mutableMapOf()
        }

        entrypoints!!.computeIfAbsent(name) { _ ->
            objects.listProperty(Entrypoint::class.java)
        }.addAll(actions.map {
            objects.newInstance(Entrypoint::class.java).also(it::execute)
        })
    }

    interface Entrypoint {
        val value: Property<String>
            @Input get

        val adapter: Property<String>
            @Optional
            @Input
            get
    }
}
