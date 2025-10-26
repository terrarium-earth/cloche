package earth.terrarium.cloche.metadata

import earth.terrarium.cloche.api.metadata.FabricMetadata
import earth.terrarium.cloche.target.fabric.FabricTargetImpl
import earth.terrarium.cloche.tasks.data.MetadataFileProvider
import kotlinx.serialization.json.JsonObject
import org.gradle.api.Action
import javax.inject.Inject

internal abstract class FabricConfigurationMetadata @Inject constructor(val target: FabricTargetImpl) : FabricMetadata() {
    override fun withJson(action: Action<MetadataFileProvider<JsonObject>>) {
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

    override fun entrypoint(name: String, actions: List<Action<Entrypoint>>) {
        target.main.generateModJson.configure {
            metadata.entrypoint(name, actions)
        }

        target.data.onConfigured {
            it.generateModJson.configure {
                metadata.entrypoint(name, actions)
            }
        }

        target.test.onConfigured {
            it.generateModJson.configure {
                metadata.entrypoint(name, actions)
            }
        }

        target.client.onConfigured {
            it.data.onConfigured {
                it.generateModJson.configure {
                    metadata.entrypoint(name, actions)
                }
            }

            it.test.onConfigured {
                it.generateModJson.configure {
                    metadata.entrypoint(name, actions)
                }
            }
        }
    }
}
