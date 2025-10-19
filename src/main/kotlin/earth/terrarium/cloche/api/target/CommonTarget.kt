package earth.terrarium.cloche.api.target

import earth.terrarium.cloche.COMMON
import earth.terrarium.cloche.api.LazyConfigurable
import earth.terrarium.cloche.api.metadata.CommonMetadata
import earth.terrarium.cloche.api.target.compilation.CommonSecondarySourceSets
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import org.gradle.api.Action
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Optional

@JvmDefaultWithoutCompatibility
interface CommonTarget : ClocheTarget, CommonSecondarySourceSets {
    override val loaderName: String
        get() = COMMON

    override val featureName
        get() = super.featureName!!

    override val capabilitySuffix
        get() = super.capabilitySuffix!!

    override val namePath
        get() = super.namePath!!

    val client: LazyConfigurable<CommonSecondarySourceSets>

    // Derived properties based on the dependant targets
    val dependents: Provider<List<MinecraftTarget>>

    val minecraftVersions: Provider<Set<String>>

    // Might be not set if there is not a common minecraft version
    override val minecraftVersion: Provider<String>
        @Optional get

    fun withPublication()

    fun metadata(action: Action<CommonMetadata>)
}
