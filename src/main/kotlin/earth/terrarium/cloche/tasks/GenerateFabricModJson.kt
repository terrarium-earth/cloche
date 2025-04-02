package earth.terrarium.cloche.tasks

import earth.terrarium.cloche.api.metadata.FabricMetadata
import earth.terrarium.cloche.api.metadata.ModMetadata
import earth.terrarium.cloche.api.metadata.custom.convertToJsonFromSerializable
import kotlinx.serialization.json.*
import net.msrandom.minecraftcodev.core.utils.getAsPath
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.io.File
import kotlin.io.path.outputStream

abstract class GenerateFabricModJson : DefaultTask() {
    abstract val loaderDependencyVersion: Property<String>
        @Input get

    abstract val output: RegularFileProperty
        @OutputFile get

    abstract val commonMetadata: Property<ModMetadata>
        @Nested get

    abstract val targetMetadata: Property<FabricMetadata>
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

    init {
        modVersion.convention(project.provider { project.version.toString() })
    }

    private fun buildVersionRange(range: ModMetadata.VersionRange): String? {
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

    private fun convertPerson(person: ModMetadata.Person) = buildMap {
        put("name", JsonPrimitive(person.name.get()))

        if (person.contact.isPresent) {
            put("contact", JsonPrimitive(person.contact.get()))
        }
    }.let(::JsonObject)

    @TaskAction
    fun makeJson() {
        val file = output.getAsPath()
        val commonMetadata = commonMetadata.get()
        val targetMetadata = targetMetadata.get()

        val json = buildMap {
            put("schemaVersion", JsonPrimitive(1))
            put("id", JsonPrimitive(commonMetadata.modId.get()))
            put("version", JsonPrimitive(modVersion.get()))

            if (commonMetadata.name.isPresent) put("name", JsonPrimitive(commonMetadata.name.get()))
            if (commonMetadata.description.isPresent) put(
                "description",
                JsonPrimitive(commonMetadata.description.get())
            )

            if (commonMetadata.authors.get().isNotEmpty()) {
                put(
                    "authors",
                    JsonArray(commonMetadata.authors.get().map(::convertPerson))
                )
            }

            if (commonMetadata.contributors.get().isNotEmpty()) {
                put(
                    "contributors",
                    JsonArray(commonMetadata.contributors.get().map(::convertPerson))
                )
            }

            if (commonMetadata.url.isPresent) {
                put(
                    "contact",
                    JsonObject(mapOf("homepage" to JsonPrimitive(commonMetadata.url.get()))),
                )
            }

            put("license", JsonPrimitive(commonMetadata.license.get()))

            if (commonMetadata.icon.isPresent) {
                put("icon", JsonPrimitive(commonMetadata.icon.get()))
            }

            if (accessWidener.isPresent) {
                put("accessWidener", JsonPrimitive(accessWidener.get()))
            }

            val mixinNames = mixinConfigs.map(File::getName)
            val clientMixinNames = clientMixinConfigs.map(File::getName) - mixinNames

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

            val depends = mutableMapOf<String, String>()

            val entrypoints = targetMetadata.entrypoints.get()

            if (entrypoints.isNotEmpty()) {
                put("entrypoints", JsonObject(entrypoints.mapValues { (key, value) ->
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

            depends.put("fabricloader", ">=${loaderDependencyVersion.get()}")
            depends.put("fabric", "*")

            val dependencies = commonMetadata.dependencies.get() + targetMetadata.dependencies.get()

            if (dependencies.isNotEmpty()) {
                val suggests = mutableMapOf<String, String>()

                for (dependency in dependencies) {
                    val key = dependency.modId.get()
                    val version = dependency.version.map { buildVersionRange(it) }.getOrElse("*")

                    if (dependency.required.getOrElse(false)) {
                        depends[key] = version
                    } else {
                        suggests[key] = version
                    }
                }

                if (suggests.isNotEmpty()) {
                    put("suggests", JsonObject(suggests.mapValues { (_, value) -> JsonPrimitive(value) }))
                }
            }

            put("depends", JsonObject(depends.mapValues { (_, value) -> JsonPrimitive(value) }))

            val custom = commonMetadata.custom.get() + targetMetadata.custom.get()

            if (custom.isNotEmpty()) {
                put("custom", JsonObject(custom.mapValues { (_, value) -> convertToJsonFromSerializable(value) }))
            }
        }

        output.getAsPath().outputStream().use {
            Json {
                prettyPrint = true
            }.encodeToStream(json, it)
        }
    }
}
