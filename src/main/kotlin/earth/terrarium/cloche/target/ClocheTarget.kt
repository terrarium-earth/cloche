package earth.terrarium.cloche.target

import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Project
import javax.inject.Inject

internal const val TARGET_NAME_PATH_SEPARATOR = ':'

sealed interface ClocheTarget : Compilation {
    val dependsOn: DomainObjectCollection<CommonTarget>

    val project: Project
        @Inject
        get

    val featureName
        get() = lowerCamelCaseGradleName(name)

    val capabilityName
        get() = name.replace(TARGET_NAME_PATH_SEPARATOR, '-')

    val namePath
        get() = name.replace(TARGET_NAME_PATH_SEPARATOR, '/')

    fun dependsOn(vararg common: CommonTarget) {
        dependsOn.addAll(listOf(*common))
    }
}
