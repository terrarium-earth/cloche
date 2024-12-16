package earth.terrarium.cloche.target

import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import javax.inject.Inject

internal const val TARGET_NAME_PATH_SEPARATOR = ':'

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
        get() = lowerCamelCaseGradleName(name)

    val classifierName
        get() = name.replace(TARGET_NAME_PATH_SEPARATOR, '-')

    val namePath
        get() = name.replace(TARGET_NAME_PATH_SEPARATOR, '/')

    fun dependsOn(vararg common: CommonTarget) {
        dependsOn.addAll(*common)
    }
}
