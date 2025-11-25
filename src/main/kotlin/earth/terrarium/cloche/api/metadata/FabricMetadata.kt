package earth.terrarium.cloche.api.metadata

import earth.terrarium.cloche.api.metadata.CommonMetadata.Environment
import earth.terrarium.cloche.target.fabric.FabricTargetImpl
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

abstract class FabricMetadata @Inject internal constructor(
    @Transient
    private val target: FabricTargetImpl,
) : CommonMetadata {
    abstract val environment: Property<Environment>
        @Optional
        @Input
        get

    val entrypoints = mutableMapOf<String, ListProperty<Entrypoint>>()
        @Input
        @Optional
        get

    abstract val languageAdapters: MapProperty<String, String>
        @Input
        @Optional
        get

    fun withJson(action: Action<MetadataFileProvider<JsonObject>>) {
        target.withMetadataJson(action)

        target.data.onConfigured {
            it.withMetadataJson(action)
        }

        target.test.onConfigured {
            it.withMetadataJson(action)
        }

        target.client.onConfigured {
            it.data.onConfigured {
                it.withMetadataJson(action)
            }

            it.test.onConfigured {
                it.withMetadataJson(action)
            }
        }
    }

    fun entrypoint(name: String, value: String) = entrypoint(name) {
        this.value.set(value)
    }

    fun entrypoint(name: String, action: Action<Entrypoint>) =
        entrypoint(name, listOf(action))

    fun entrypoint(name: String, actions: List<Action<Entrypoint>>) = entrypoints.computeIfAbsent(name) { _ ->
        objects.listProperty<Entrypoint>()
    }.addAll(actions.map {
        objects.newInstance<Entrypoint>().also(it::execute)
    })

    interface Entrypoint {
        val value: Property<String>
            @Input get

        val adapter: Property<String>
            @Optional
            @Input
            get
    }
}
