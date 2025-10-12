package earth.terrarium.cloche.tasks.data

import earth.terrarium.cloche.api.metadata.CommonMetadata
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.peanuuutz.tomlkt.Toml
import net.peanuuutz.tomlkt.TomlElement
import net.peanuuutz.tomlkt.decodeFromNativeReader
import net.peanuuutz.tomlkt.encodeToNativeWriter
import java.io.InputStream
import java.io.OutputStream

@Serializable
data class ForgeMods(
    val modLoader: String,
    val loaderVersion: String,
    val license: String,
    val dependencies: Map<String, List<Dependency>> = emptyMap(),
    val issueTrackerURL: String? = null,
    val mods: List<ForgeMod>,
    val logoBlur: Boolean? = null,
) {
    @Serializable
    data class Dependency(
        val modId: String,
        val mandatory: Boolean,
        val versionRange: String,
        val ordering: CommonMetadata.Dependency.Ordering,
        @Serializable(SideSerializer::class) val side: CommonMetadata.Environment,
    )

    // Serialize the enum to uppercase
    @Serializer(CommonMetadata.Environment::class)
    class SideSerializer : KSerializer<CommonMetadata.Environment> {
        override fun serialize(encoder: Encoder, value: CommonMetadata.Environment) = encoder.encodeString(value.name.uppercase())

        override fun deserialize(decoder: Decoder): CommonMetadata.Environment {
            val value = decoder.decodeString()

            return CommonMetadata.Environment
                .valueOf(value.take(1).uppercase() + value.drop(1).lowercase())
        }
    }
}

@Serializable
data class ForgeMod(
    val modId: String,
    val version: String,
    val displayName: String? = null,
    val description: String? = null,
    val logoFile: String? = null,
    val displayURL: String? = null,
    val credits: String? = null,
    val authors: String? = null,
    val modproperties: Map<String, TomlElement> = emptyMap(),
)

inline fun <reified T> Toml.decodeFromStream(stream: InputStream) =
    decodeFromNativeReader<T>(stream.bufferedReader())

inline fun <reified T> Toml.encodeToStream(value: T, stream: OutputStream) =
    encodeToNativeWriter(value, stream.bufferedWriter())
