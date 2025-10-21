package earth.terrarium.cloche.api.target

import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import org.gradle.api.DomainObjectCollection
import org.gradle.api.provider.Provider

internal const val TARGET_NAME_PATH_SEPARATOR = ':'

val ClocheTarget.targetName
    get() = name.takeUnless(String::isEmpty)

val ClocheTarget.isSingleTarget
    get() = targetName == null

@JvmDefaultWithoutCompatibility
interface ClocheTarget : TargetTreeElement {
    val dependsOn: DomainObjectCollection<CommonTarget>

    val featureName
        get() = targetName?.let { lowerCamelCaseGradleName(it) }

    val capabilitySuffix
        get() = targetName?.replace(TARGET_NAME_PATH_SEPARATOR, '-')

    val namePath
        get() = targetName?.replace(TARGET_NAME_PATH_SEPARATOR, '/')

    val loaderName: String

    val minecraftVersion: Provider<String>

    fun dependsOn(vararg common: CommonTarget) {
        dependsOn.addAll(listOf(*common))
    }
}
