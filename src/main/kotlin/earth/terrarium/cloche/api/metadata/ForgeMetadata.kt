package earth.terrarium.cloche.api.metadata

import earth.terrarium.cloche.api.metadata.CommonMetadata.VersionRange
import earth.terrarium.cloche.api.metadata.custom.JsonSerializable
import earth.terrarium.cloche.api.metadata.custom.convertToSerializable
import org.gradle.api.Action
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional

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

    fun modProperty(name: String, value: Any?) =
        modProperties.put(name, convertToSerializable(objects, value))

    fun modProperties(vararg data: Pair<String, Any?>) =
        custom(mapOf(*data))

    fun modProperties(data: Map<String, Any?>) =
        modProperties.putAll(data.mapValues { (_, value) -> convertToSerializable(objects, value) })

    fun loaderVersion(version: String) = loaderVersion {
        it.start.set(version)
    }

    fun loaderVersion(action: Action<VersionRange>) =
        loaderVersion.set(objects.newInstance(VersionRange::class.java).also(action::execute))
}
