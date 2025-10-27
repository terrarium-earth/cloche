package earth.terrarium.cloche.api.metadata

import earth.terrarium.cloche.api.metadata.CommonMetadata.VersionRange
import earth.terrarium.cloche.api.metadata.custom.JsonSerializable
import earth.terrarium.cloche.api.metadata.custom.convertToSerializable
import earth.terrarium.cloche.target.forge.ForgeLikeTargetImpl
import earth.terrarium.cloche.target.forge.lex.ForgeTargetImpl
import earth.terrarium.cloche.tasks.data.MetadataFileProvider
import net.peanuuutz.tomlkt.TomlTable
import org.gradle.api.Action
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.kotlin.dsl.newInstance
import javax.inject.Inject

abstract class ForgeMetadata @Inject internal constructor(
    @Transient
    private val target: ForgeLikeTargetImpl,
) : CommonMetadata {
    abstract val modLoader: Property<String>
        @Optional
        @Input
        get

    abstract val loaderVersion: Property<VersionRange>
        @Nested
        @Optional
        get

    abstract val showAsResourcePack: Property<Boolean>
        @Optional
        @Input
        get

    abstract val showAsDataPack: Property<Boolean>
        @Optional
        @Input
        get

    abstract val services: ListProperty<String>
        @Optional
        @Input
        get

    abstract val blurLogo: Property<Boolean>
        @Optional
        @Input
        get

    abstract val modProperties: MapProperty<String, JsonSerializable>
        @Nested
        get

    fun withToml(action: Action<MetadataFileProvider<TomlTable>>) {
        target.withMetadataToml(action)

        target.data.onConfigured {
            it.withMetadataToml(action)
        }

        target.test.onConfigured {
            it.withMetadataToml(action)
        }
    }

    fun modProperty(name: String, value: Any?) =
        modProperties.put(name, convertToSerializable(objects, value))

    fun modProperties(vararg data: Pair<String, Any?>) =
        custom(mapOf(*data))

    fun modProperties(data: Map<String, Any?>) =
        modProperties.putAll(data.mapValues { (_, value) -> convertToSerializable(objects, value) })

    fun loaderVersion(version: String) = loaderVersion {
        this.start.set(version)
    }

    fun loaderVersion(action: Action<VersionRange>) =
        loaderVersion.set(objects.newInstance<VersionRange>().also(action::execute))
}
