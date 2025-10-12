package earth.terrarium.cloche.tasks.data

import earth.terrarium.cloche.api.metadata.CommonMetadata
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NeoForgeMods(
    val modLoader: String,
    val loaderVersion: String,
    val license: String,
    val dependencies: Map<String, List<Dependency>> = emptyMap(),
    val mixins: List<Mixin> = emptyList(),
    val issueTrackerURL: String? = null,
    val mods: List<ForgeMod>,
    val logoBlur: Boolean? = null,
) {
    @Serializable
    data class Dependency(
        val modId: String,
        val type: Type?,
        val versionRange: String,
        val reason: String?,
        val ordering: CommonMetadata.Dependency.Ordering,
        @Serializable(ForgeMods.SideSerializer::class) val side: CommonMetadata.Environment,
    ) {
        enum class Type {
            @SerialName("required") Required,
            @SerialName("optional") Optional,
            @SerialName("discouraged") Discouraged,
            @SerialName("incompatible") Incompatible,
        }
    }

    @Serializable
    data class Mixin(
        val config: String,
    )
}
