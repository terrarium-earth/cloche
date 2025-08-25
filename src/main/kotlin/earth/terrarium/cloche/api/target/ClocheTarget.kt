package earth.terrarium.cloche.api.target

import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import org.gradle.api.DomainObjectCollection
import org.gradle.api.provider.Provider

internal const val TARGET_NAME_PATH_SEPARATOR = ':'

@JvmDefaultWithoutCompatibility
interface ClocheTarget : TargetTreeElement {
    val dependsOn: DomainObjectCollection<CommonTarget>

    val featureName
        get() = lowerCamelCaseGradleName(name)

    val capabilitySuffix
        get() = name.replace(TARGET_NAME_PATH_SEPARATOR, '-')

    val namePath
        get() = name.replace(TARGET_NAME_PATH_SEPARATOR, '/')

    val loaderName: String

    val minecraftVersion: Provider<String>

    fun dependsOn(vararg common: CommonTarget) {
        dependsOn.addAll(listOf(*common))
    }
}
