package earth.terrarium.cloche.util

import earth.terrarium.cloche.api.metadata.Metadata
import org.gradle.api.InvalidUserCodeException

fun validateMetadata(metadata: Metadata) {
    if (!metadata.modId.isPresent) {
        throw InvalidUserCodeException("Empty mod identifier specified.")
    }
}
