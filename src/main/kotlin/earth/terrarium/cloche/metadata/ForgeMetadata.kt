package earth.terrarium.cloche.metadata

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional

interface ForgeMetadata {
    val showAsResourcePack: Property<Boolean>
        @Optional
        @Input
        get

    val showAsDataPack: Property<Boolean>
        @Optional
        @Input
        get

    val clientOnly: Property<Boolean>
        @Optional
        @Input
        get

    val services: ListProperty<String>
        @Optional
        @Input
        get


}
