package earth.terrarium.cloche.api.metadata

import earth.terrarium.cloche.api.metadata.CommonMetadata.Environment
import earth.terrarium.cloche.tasks.data.MetadataFileProvider
import kotlinx.serialization.json.JsonObject
import org.gradle.api.Action
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.newInstance
import javax.inject.Inject

abstract class FabricMetadata : CommonMetadata {
    abstract val environment: Property<Environment>
        @Optional
        @Input
        get

    abstract val languageAdapters: MapProperty<String, String>
        @Input
        @Optional
        get

    abstract fun withJson(action: Action<MetadataFileProvider<JsonObject>>)

    fun entrypoint(name: String, value: String) = entrypoint(name) {
        this.value.set(value)
    }

    fun entrypoint(name: String, action: Action<Entrypoint>) =
        entrypoint(name, listOf(action))

    abstract fun entrypoint(name: String, actions: List<Action<Entrypoint>>)

    fun set(other: FabricMetadata) {
        super.set(other)

        environment.set(other.environment)
        languageAdapters.set(other.languageAdapters)
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
