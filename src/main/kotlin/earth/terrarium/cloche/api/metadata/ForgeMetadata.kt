package earth.terrarium.cloche.api.metadata

import earth.terrarium.cloche.api.metadata.CommonMetadata.VersionRange
import earth.terrarium.cloche.api.metadata.custom.JsonSerializable
import earth.terrarium.cloche.api.metadata.custom.convertToSerializable
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

@JvmDefaultWithoutCompatibility
interface ForgeMetadata : CommonMetadata {
    val modLoader: Property<String>
        @Optional
        @Input
        get

    val loaderVersion: Property<VersionRange>
        @Nested
        @Optional
        get

    val showAsResourcePack: Property<Boolean>
        @Optional
        @Input
        get

    val showAsDataPack: Property<Boolean>
        @Optional
        @Input
        get

    val services: ListProperty<String>
        @Optional
        @Input
        get

    val blurLogo: Property<Boolean>
        @Optional
        @Input
        get

    val modProperties: MapProperty<String, JsonSerializable>
        @Nested
        get

    fun withToml(action: Action<MetadataFileProvider<TomlTable>>) {
        // Implemented only at configuration time (in ForgeConfigurationMetadata), no-op at execution time
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

    fun set(other: ForgeMetadata) {
        super.set(other)

        modLoader.set(other.modLoader)
        loaderVersion.set(other.loaderVersion)
        showAsResourcePack.set(other.showAsResourcePack)
        showAsDataPack.set(other.showAsDataPack)
        services.set(other.services)
        blurLogo.set(other.blurLogo)
        modProperties.set(other.modProperties)
    }
}
