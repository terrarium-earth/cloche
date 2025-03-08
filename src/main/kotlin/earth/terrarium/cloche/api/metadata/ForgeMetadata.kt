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
interface ForgeMetadata {
    val modLoader: Property<String>
        @Optional
        @Input
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

    val dependencies: ListProperty<Dependency>
        @Nested
        get

    val modProperties: MapProperty<String, JsonSerializable>
        @Nested
        get

    val objects: ObjectFactory
        @Inject get

    fun dependency(action: Action<Dependency>) =
        dependencies.add(objects.newInstance(Dependency::class.java).also(action::execute))

    fun custom(vararg data: Pair<String, Any?>) =
        modProperties(*data)

    fun custom(data: Map<String, Any?>) =
        modProperties(data)

    fun custom(name: String, value: Any?) =
        modProperty(name, value)

    fun modProperties(vararg data: Pair<String, Any?>) =
        custom(mapOf(*data))

    fun modProperties(data: Map<String, Any?>) =
        modProperties.putAll(data.mapValues { (_, value) -> convertToSerializable(objects, value) })

    fun modProperty(name: String, value: Any?) =
        modProperties.put(name, convertToSerializable(objects, value))
}
