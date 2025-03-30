package earth.terrarium.cloche.api.metadata

import earth.terrarium.cloche.api.metadata.ModMetadata.Dependency
import earth.terrarium.cloche.api.metadata.custom.JsonSerializable
import earth.terrarium.cloche.api.metadata.custom.convertToSerializable
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import javax.inject.Inject

@JvmDefaultWithoutCompatibility
interface FabricMetadata {
    val entrypoints: MapProperty<String, List<Entrypoint>>
        @Input get

    val languageAdapters: MapProperty<String, String>
        @Input get

    val dependencies: ListProperty<Dependency>
        @Nested
        get

    val custom: MapProperty<String, JsonSerializable>
        @Nested
        get

    val objects: ObjectFactory
        @Inject get

    fun entrypoint(name: String, value: String) = entrypoint(name) {
        it.value.set(value)
    }

    fun entrypoint(name: String, action: Action<Entrypoint>) =
        entrypoint(name, listOf(action))

    fun entrypoint(name: String, actions: List<Action<Entrypoint>>) {
        val entrypoints = actions.map {
            objects.newInstance(Entrypoint::class.java).also(it::execute)
        }

        this.entrypoints.put(name, entrypoints)
    }

    fun languageAdapter(name: String, value: String) {
        this.languageAdapters.put(name, value)
    }

    fun dependency(action: Action<Dependency>) =
        dependencies.add(objects.newInstance(Dependency::class.java).also(action::execute))

    fun custom(vararg data: Pair<String, Any?>) = custom(mapOf(*data))

    fun custom(data: Map<String, Any?>) =
        custom.putAll(data.mapValues { (_, value) -> convertToSerializable(objects, value) })

    fun custom(name: String, value: Any?) =
        custom.put(name, convertToSerializable(objects, value))

    interface Entrypoint {
        val value: Property<String>
            @Input get

        val adapter: Property<String>
            @Optional
            @Input
            get
    }
}
