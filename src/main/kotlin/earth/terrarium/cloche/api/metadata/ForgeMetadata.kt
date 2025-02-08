package earth.terrarium.cloche.api.metadata

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
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

    val services: ListProperty<String>
        @Optional
        @Input
        get

    val blurLogo: Property<Boolean>
        @Optional
        @Input
        get
}
