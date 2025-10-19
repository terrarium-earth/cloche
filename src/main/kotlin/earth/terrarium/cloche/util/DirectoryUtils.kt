package earth.terrarium.cloche.util

import org.gradle.api.file.Directory

fun Directory.optionalDir(name: String?): Directory = if (name == null) {
    this
} else {
    dir(name)
}
