package earth.terrarium.cloche.metadata

import earth.terrarium.cloche.api.metadata.FabricMetadata
import earth.terrarium.cloche.tasks.data.MetadataFileProvider
import kotlinx.serialization.json.JsonObject
import org.gradle.api.Action
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.newInstance

abstract class FabricTaskMetadata : FabricMetadata() {
    val entrypoints = mutableMapOf<String, ListProperty<Entrypoint>>()
        @Input
        @Optional
        get

    override fun withJson(action: Action<MetadataFileProvider<JsonObject>>) {
        // Implemented only at configuration time (in FabricConfigurationMetadata), no-op at execution time
    }

    override fun entrypoint(
        name: String,
        actions: List<Action<Entrypoint>>
    ) {
        entrypoints.computeIfAbsent(name) { _ ->
            objects.listProperty<Entrypoint>()
        }.addAll(actions.map {
            objects.newInstance<Entrypoint>().also(it::execute)
        })
    }
}
