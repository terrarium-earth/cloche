package earth.terrarium.cloche.api.target

import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import org.gradle.api.DomainObjectCollection

internal const val TARGET_NAME_PATH_SEPARATOR = ':'

@JvmDefaultWithoutCompatibility
sealed interface ClocheTarget : TargetTreeElement {
    val dependsOn: DomainObjectCollection<CommonTarget>

    val featureName
        get() = lowerCamelCaseGradleName(name)

    val classifierName
        get() = name.replace(TARGET_NAME_PATH_SEPARATOR, '-')

    val namePath
        get() = name.replace(TARGET_NAME_PATH_SEPARATOR, '/')

    val loaderName: String

    fun dependsOn(vararg common: CommonTarget) {
        dependsOn.addAll(listOf(*common))
    }
}
