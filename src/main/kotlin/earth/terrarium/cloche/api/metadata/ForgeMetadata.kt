package earth.terrarium.cloche.api.metadata

import earth.terrarium.cloche.api.metadata.ModMetadata.Dependency
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import javax.inject.Inject

interface ForgeMetadata {
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

    val objects: ObjectFactory
        @Inject get

    fun dependency(action: Action<Dependency>) =
        dependencies.add(objects.newInstance(Dependency::class.java).also(action::execute))
}
