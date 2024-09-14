package earth.terrarium.cloche.metadata

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

interface ModMetadata {
    val modId: Property<String>
        @Input get

    val name: Property<String>
        @Optional
        @Input
        get

    val description: Property<String>
        @Optional
        @Input
        get

    val license: Property<String>
        @Optional
        @Input
        get

    val icon: Property<String>
        @Optional
        @Input
        get

    val url: Property<String>
        @Optional
        @Input
        get

    val issues: Property<String>
        @Optional
        @Input
        get

    val sources: Property<String>
        @Optional
        @Input
        get

    val authors: ListProperty<String>
        @Optional
        @Input
        get
}
