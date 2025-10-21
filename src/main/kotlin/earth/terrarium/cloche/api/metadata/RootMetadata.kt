package earth.terrarium.cloche.api.metadata

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

interface RootMetadata : CommonMetadata {
    // Mod ID should only ever be set once per project
    val modId: Property<String>
        @Optional
        @Input get
}
