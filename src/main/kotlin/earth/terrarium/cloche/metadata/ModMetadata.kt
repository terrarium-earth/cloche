package earth.terrarium.cloche.metadata

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input

abstract class ModMetadata {
    abstract val modId: Property<String>
        @Input
        get
}
