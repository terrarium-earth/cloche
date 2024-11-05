package earth.terrarium.cloche.target

import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import javax.inject.Inject

sealed interface ClocheTarget : Compilation {
    val dependsOn: ListProperty<CommonTarget>
        @Optional
        @Input
        get

    val project: Project
        @Inject
        get

    val remapNamespace: String?
        @Internal
        get() = null

    val featureName
        get() = name.replace('/', '-')

    fun dependsOn(vararg common: CommonTarget) {
        dependsOn.addAll(*common)
    }
}
