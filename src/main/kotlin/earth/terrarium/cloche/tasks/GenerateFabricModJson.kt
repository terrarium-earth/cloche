package earth.terrarium.cloche.tasks

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import earth.terrarium.cloche.metadata.FabricMetadata
import earth.terrarium.cloche.metadata.ModMetadata
import net.msrandom.minecraftcodev.core.utils.getAsPath
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import kotlin.io.path.writeText

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
            if (range.start.isPresent) {
                if (range.startInclusive.getOrElse(true)) {
                    append(">=")
                } else {
                    append('>')
                }

                append(range.start.get())
            }

            append(' ')

            if (range.end.isPresent) {
                if (range.endExclusive.getOrElse(true)) {
                    append('<')
                } else {
                    append("<=")
                }

                append(range.end.get())
            }
        }
    }

    private fun convertPerson(person: ModMetadata.Person) = JsonObject().apply {
        addProperty("name", person.name.get())

        if (person.contact.isPresent) {
            addProperty("contact", person.contact.get())
        }
    }

    @TaskAction
    fun makeJson() {
        val file = output.getAsPath()
        val commonMetadata = commonMetadata.get()
        val targetMetadata = targetMetadata.get()

        val json = JsonObject().apply {
            addProperty("schemaVersion", 1)
            addProperty("id", commonMetadata.modId.get())
            addProperty("version", modVersion.get())

            if (commonMetadata.name.isPresent) addProperty("name", commonMetadata.name.get())
            if (commonMetadata.description.isPresent) addProperty("description", commonMetadata.description.get())

            if (commonMetadata.authors.get().isNotEmpty()) {
                add(
                    "authors",
                    JsonArray().apply { commonMetadata.authors.get().forEach { add(convertPerson(it)) } },
                )
            }

            if (commonMetadata.contributors.get().isNotEmpty()) {
                add(
                    "contributors",
                    JsonArray().apply { commonMetadata.contributors.get().forEach { add(convertPerson(it)) } }
                )
            }

            if (commonMetadata.url.isPresent) {
                add(
                    "contact",
                    JsonObject().apply {
                        addProperty("homepage", commonMetadata.url.get())
                    }
                )
            }

            addProperty("license", commonMetadata.license.get())

            if (commonMetadata.icon.isPresent) {
                addProperty("icon", commonMetadata.icon.get())
            }

            if (accessWidener.isPresent) {
                addProperty("accessWidener", accessWidener.get())
            }

            val mixins = JsonArray()

            val clientMixins = JsonArray().apply {
                for (config in clientMixinConfigs) {
                    add(config.name)
                }
            }

            for (config in mixinConfigs) {
                mixins.add(config.name)
            }

            for (config in clientMixinConfigs) {
                mixins.add(JsonObject().apply {
                    addProperty("config", config.name)
                    addProperty("environment", "client")
                })
            }

            if (mixins.size() > 0) {
                add("mixins", mixins)
            }

            val depends = JsonObject()

            val entrypoints = targetMetadata.entrypoints.get()

            if (entrypoints.isNotEmpty()) {
                add("entrypoints", JsonObject().apply {
                    for ((key, values) in entrypoints.entries) {
                        val array = JsonArray()

                        for (entrypoint in values) {
                            array.add(JsonObject().apply {
                                addProperty("value", entrypoint.value.get())

                                if (entrypoint.adapter.isPresent) {
                                    addProperty("adapter", entrypoint.adapter.get())
                                }
                            })
                        }

                        add(key, array)
                    }
                })
            }

            depends.addProperty("fabricloader", ">=${loaderDependencyVersion.get()}")
            depends.addProperty("fabric", "*")

            if (commonMetadata.dependencies.get().isNotEmpty()) {
                val suggests = JsonObject()
                for (dependency in commonMetadata.dependencies.get()) {
                    val key = dependency.modId.get()
                    val version = buildVersionRange(dependency.version.get()) ?: "*"

                    if (dependency.required.get()) {
                        depends.addProperty(key, version)
                    } else {
                        suggests.addProperty(key, version)
                    }
                }

                if (suggests.size() != 0) {
                    add("suggests", suggests)
                }
            }

            add("depends", depends)
        }

        file.writeText(Gson().toJson(json))
    }
}
