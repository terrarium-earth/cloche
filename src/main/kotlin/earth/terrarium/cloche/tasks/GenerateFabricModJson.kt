package earth.terrarium.cloche.tasks

import earth.terrarium.cloche.api.metadata.FabricMetadata
import earth.terrarium.cloche.api.metadata.Metadata
import earth.terrarium.cloche.api.metadata.custom.convertToJsonFromSerializable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import net.msrandom.minecraftcodev.core.utils.getAsPath
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.io.File
import kotlin.io.path.outputStream

@OptIn(ExperimentalSerializationApi::class)
abstract class GenerateFabricModJson : DefaultTask() {
    abstract val loaderDependencyVersion: Property<String>
        @Input get

    abstract val output: RegularFileProperty
        @OutputFile get

    abstract val metadata: Property<FabricMetadata>
        @Nested get

    abstract val accessWidener: Property<String>
        @Optional
        @Input
        get

    abstract val modVersion: Property<String>
        @Input get

    abstract val mixinConfigs: ConfigurableFileCollection
        @PathSensitive(PathSensitivity.RELATIVE)
        @InputFiles
        get

    abstract val clientMixinConfigs: ConfigurableFileCollection
        @PathSensitive(PathSensitivity.RELATIVE)
        @InputFiles
        get

    private val jsonBuilder: Json = Json {
        prettyPrint = true
    }

    init {
        modVersion.convention(project.provider { project.version.toString() })
    }

    private fun Metadata.Environment.toFabricString(): String {
        return when (this) {
            Metadata.Environment.CLIENT -> {
                "client"
            }

            Metadata.Environment.SERVER -> {
                "server"
            }

            Metadata.Environment.BOTH -> {
                "*"
            }
        }
    }

    private fun buildVersionRange(range: Metadata.VersionRange): String? {
        if (!range.start.isPresent && !range.end.isPresent) {
            return null
        }

        return buildString {
            if (range.start == range.end) {
                require(range.startInclusive.getOrElse(true)) {
                    "No version in the range"
                }
                append(range.start.get())
                return@buildString
            }

            if (range.start.isPresent) {
                if (range.startInclusive.getOrElse(true)) {
                    append(">=")
                } else {
                    append('>')
                }

                append(range.start.get())
            }

            if (range.end.isPresent) {
                if (range.start.isPresent) append(' ')

                if (range.endExclusive.getOrElse(true)) {
                    append('<')
                } else {
                    append("<=")
                }

                append(range.end.get())
            }
        }
    }

    private fun convertPerson(person: Metadata.Person) = buildMap {
        put("name", JsonPrimitive(person.name.get()))

        if (person.contact.isPresent) {
            put("contact", JsonPrimitive(person.contact.get()))
        }
    }.let(::JsonObject)

    @TaskAction
    fun makeJson() {
        val json = buildMap {
            val metadata = metadata.get()
            put("schemaVersion", JsonPrimitive(1))
            put("id", JsonPrimitive(metadata.modId.get()))
            put("version", JsonPrimitive(modVersion.get()))

            if (metadata.name.isPresent) put("name", JsonPrimitive(metadata.name.get()))
            if (metadata.description.isPresent) put(
                "description",
                JsonPrimitive(metadata.description.get())
            )

            if (metadata.authors.get().isNotEmpty()) {
                put(
                    "authors",
                    JsonArray(metadata.authors.get().map(::convertPerson))
                )
            }

            if (metadata.contributors.get().isNotEmpty()) {
                put(
                    "contributors",
                    JsonArray(metadata.contributors.get().map(::convertPerson))
                )
            }

            if (metadata.url.isPresent) {
                put(
                    "contact",
                    JsonObject(mapOf("homepage" to JsonPrimitive(metadata.url.get()))),
                )
            }

            put("license", JsonPrimitive(metadata.license.get()))

            if (metadata.icon.isPresent) {
                put("icon", JsonPrimitive(metadata.icon.get()))
            }

            put("environment", JsonPrimitive(metadata.environment.get().toFabricString()))

            if (accessWidener.isPresent) {
                put("accessWidener", JsonPrimitive(accessWidener.get()))
            }

            val mixinNames = mixinConfigs.map(File::getName)
            val clientMixinNames = clientMixinConfigs.map(File::getName) - mixinNames.toSet()

            val commonMixins = mixinNames.map(::JsonPrimitive)
            val clientMixins = clientMixinNames.map {
                JsonObject(
                    mapOf(
                        "config" to JsonPrimitive(it),
                        "environment" to JsonPrimitive("client"),
                    )
                )
            }
            if (commonMixins.isNotEmpty() || clientMixins.isNotEmpty()) {
                put("mixins", JsonArray(commonMixins + clientMixins))
            }

            val entrypoints = metadata.entrypoints.get()
            if (entrypoints.isNotEmpty()) {
                put("entrypoints", JsonObject(entrypoints.mapValues { (_, value) ->
                    JsonArray(value.map { entrypoint ->
                        JsonObject(buildMap {
                            put("value", JsonPrimitive(entrypoint.value.get()))

                            if (entrypoint.adapter.isPresent) {
                                put("adapter", JsonPrimitive(entrypoint.adapter.get()))
                            }
                        })
                    })
                }))
            }

            val languageAdapters = metadata.languageAdapters.get()
            if (languageAdapters.isNotEmpty()) {
                put("languageAdapters", JsonObject(languageAdapters.mapValues { (_, value) -> JsonPrimitive(value) }))
            }

            val depends = mutableMapOf<String, String>()
            depends["fabricloader"] = ">=${loaderDependencyVersion.get()}"

            val dependencies = metadata.dependencies.get()
            if (dependencies.isNotEmpty()) {
                val recommends = mutableMapOf<String, String>()
                val suggests = mutableMapOf<String, String>()
                val conflicts = mutableMapOf<String, String>()
                val breaks = mutableMapOf<String, String>()
                for (dependency in dependencies) {
                    val key = dependency.modId.get()
                    val version = dependency.version.map { buildVersionRange(it) ?: "*" }.getOrElse("*")
                    when (dependency.type.getOrElse(Metadata.Dependency.Type.REQUIRED)) {
                        Metadata.Dependency.Type.REQUIRED -> {
                            depends[key] = version
                        }

                        Metadata.Dependency.Type.RECOMMENDED -> {
                            recommends[key] = version
                        }

                        Metadata.Dependency.Type.SUGGESTED -> {
                            suggests[key] = version
                        }

                        Metadata.Dependency.Type.CONFLICTS -> {
                            conflicts[key] = version
                        }

                        Metadata.Dependency.Type.BREAKS -> {
                            breaks[key] = version
                        }
                    }
                }

                if (recommends.isNotEmpty()) {
                    put("recommends", JsonObject(recommends.mapValues { (_, value) -> JsonPrimitive(value) }))
                }
                if (suggests.isNotEmpty()) {
                    put("suggests", JsonObject(suggests.mapValues { (_, value) -> JsonPrimitive(value) }))
                }
                if (conflicts.isNotEmpty()) {
                    put("conflicts", JsonObject(conflicts.mapValues { (_, value) -> JsonPrimitive(value) }))
                }
                if (breaks.isNotEmpty()) {
                    put("breaks", JsonObject(breaks.mapValues { (_, value) -> JsonPrimitive(value) }))
                }
            }

            put("depends", JsonObject(depends.mapValues { (_, value) -> JsonPrimitive(value) }))

            val custom = metadata.custom.get()
            if (custom.isNotEmpty()) {
                put("custom", JsonObject(custom.mapValues { (_, value) -> convertToJsonFromSerializable(value) }))
            }
        }

        output.getAsPath().outputStream().use {
            jsonBuilder.encodeToStream(json, it)
        }
    }
}
