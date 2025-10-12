package earth.terrarium.cloche.tasks.data

import earth.terrarium.cloche.api.metadata.CommonMetadata
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class FabricMod(
    val schemaVersion: Int,
    val id: String,
    val version: String,
    val name: String? = null,
    val description: String? = null,
    val environment: CommonMetadata.Environment = CommonMetadata.Environment.Both,
    val authors: List<Person> = emptyList(),
    val contributors: List<Person> = emptyList(),
    val contact: Contact? = null,
    val license: String? = null,
    val icon: String? = null,
    val accessWidener: String? = null,
    val mixins: List<Mixin> = emptyList(),
    val entrypoints: Map<String, List<Entrypoint>> = emptyMap(),
    val languageAdapters: Map<String, String> = emptyMap(),
    val depends: Map<String, String>,
    val suggests: Map<String, String> = emptyMap(),
    val custom: Map<String, JsonElement> = emptyMap(),
) {
    @Serializable
    data class Person(
        val name: String,
        val contact: Contact? = null
    )

    @Serializable
    data class Contact(
        val email: String? = null,
        val irc: String? = null,
        val homepage: String? = null,
        val issues: String? = null,
        val sources: String? = null,
    )

    @Serializable
    data class Mixin(
        val config: String,
        val environment: String? = null
    )

    @Serializable
    data class Entrypoint(
        val value: String,
        val adapter: String? = null
    )
}
