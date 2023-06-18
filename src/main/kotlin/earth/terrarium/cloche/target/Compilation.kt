package earth.terrarium.cloche.target

import earth.terrarium.cloche.ClocheDependencyHandler
import org.gradle.api.Action
import org.gradle.api.Named

interface Compilation : Named {
    fun dependencies(action: Action<ClocheDependencyHandler>)
}
