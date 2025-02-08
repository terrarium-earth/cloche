package earth.terrarium.cloche.api.target

import earth.terrarium.cloche.COMMON
import earth.terrarium.cloche.api.LazyConfigurable
import earth.terrarium.cloche.api.target.compilation.CommonSecondarySourceSets

@JvmDefaultWithoutCompatibility
interface CommonTarget : ClocheTarget, CommonSecondarySourceSets {
    override val loaderName: String
        get() = COMMON

    val client: LazyConfigurable<CommonSecondarySourceSets>

    fun withPublication()
}
