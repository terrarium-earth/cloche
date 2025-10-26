package earth.terrarium.cloche.metadata

import earth.terrarium.cloche.api.metadata.ForgeMetadata
import earth.terrarium.cloche.target.forge.ForgeLikeTargetImpl
import earth.terrarium.cloche.tasks.data.MetadataFileProvider
import net.peanuuutz.tomlkt.TomlTable
import org.gradle.api.Action
import javax.inject.Inject

internal abstract class ForgeConfigurationMetadata @Inject constructor(val target: ForgeLikeTargetImpl) : ForgeMetadata {
    override fun withToml(action: Action<MetadataFileProvider<TomlTable>>) {
        target.withMetadataToml(action)

        target.data.onConfigured {
            it.withMetadataToml(action)
        }

        target.test.onConfigured {
            it.withMetadataToml(action)
        }
    }
}
