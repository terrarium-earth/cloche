package earth.terrarium.cloche.metadata

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

abstract class ModMetadata {
    abstract val modId: Property<String>
        @Input get

    abstract val name: Property<String>
        @Optional
        @Input
        get

    abstract val description: Property<String>
        @Optional
        @Input
        get

    abstract val license: Property<String>
        @Optional
        @Input
        get

    abstract val icon: Property<String>
        @Optional
        @Input
        get

    abstract val url: Property<String>
        @Optional
        @Input
        get

    abstract val sources: Property<String>
        @Optional
        @Input
        get

    abstract val authors: ListProperty<String>
        @Optional
        @Input
        get
}
