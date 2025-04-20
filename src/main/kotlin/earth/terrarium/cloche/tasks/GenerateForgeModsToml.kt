package earth.terrarium.cloche.tasks

import com.moandjiezana.toml.TomlWriter
import earth.terrarium.cloche.api.metadata.ForgeMetadata
import earth.terrarium.cloche.api.metadata.ModMetadata
import earth.terrarium.cloche.api.metadata.custom.convertToObjectFromSerializable
import net.msrandom.minecraftcodev.core.utils.getAsPath
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import kotlin.io.path.outputStream

// TODO Migrate to use ktoml
abstract class GenerateForgeModsToml : DefaultTask() {
    abstract val loaderDependencyVersion: Property<ModMetadata.VersionRange>
        @Nested get

    abstract val output: RegularFileProperty
        @OutputFile get

    abstract val commonMetadata: Property<ModMetadata>
        @Nested get

    abstract val targetMetadata: Property<ForgeMetadata>
        @Nested get

    abstract val modVersion: Property<String>
        @Input get

    abstract val mixinConfigs: ConfigurableFileCollection
        @PathSensitive(PathSensitivity.RELATIVE)
        @InputFiles
        get

    abstract val loaderName: Property<String>
        @Input get

    init {
        modVersion.convention(project.provider { project.version.toString() })
    }

    private fun buildVersionRange(range: ModMetadata.VersionRange): String? {
        if (!range.start.isPresent && !range.end.isPresent) {
            return "[0,)"
        }

        return buildString {
            if (range.startInclusive.getOrElse(true)) {
                append('[')
            } else {
                append('(')
            }

            if (range.start.isPresent) {
                append(range.start.get())
            }

            if (range.start == range.end) {
                require(range.startInclusive.getOrElse(true)) {
                    "No version in the range"
                }
                append(']')
                return@buildString
            }

            append(',')

            if (range.end.isPresent) {
                append(range.end.get())
            }

            if (range.endExclusive.getOrElse(true)) {
                append(')')
            } else {
                append(']')
            }
        }
    }

    private fun convertPerson(person: ModMetadata.Person) = if (person.contact.isPresent) {
        "${person.name.get()} <${person.contact.get()}>"
    } else {
        person.name.get()
    }

    @TaskAction
    fun makeToml() {
        val output = output.getAsPath()
        val commonMetadata = commonMetadata.get()
        val targetMetadata = targetMetadata.get()

        val loaderVersionRange = buildVersionRange(loaderDependencyVersion.get())

        if (loaderVersionRange == null) {
            throw InvalidUserCodeException("Empty loader version range specified for a forge-like target.")
        }

        val dependencies: MutableList<Map<String, Any>> = mutableListOf(
            mapOf(
                "modId" to loaderName.get(),
                "type" to "required",
                "mandatory" to true,
                "versionRange" to loaderVersionRange,
            )
        )

        dependencies.addAll(
            (commonMetadata.dependencies.get() + targetMetadata.dependencies.get()).map { dependency ->
                val map: MutableMap<String, Any> = mutableMapOf(
                    "modId" to dependency.modId.get(),
                    // TODO Don't add both fields
                    "mandatory" to dependency.required.getOrElse(false),
                    "type" to dependency.required.getOrElse(false).let {
                        if (it) {
                            "required"
                        } else {
                            "optional"
                        }
                    },
                )

                dependency.version.map { buildVersionRange(it) }.orNull.let {
                    map["versionRange"] = it ?: "[0,)"
                }

                map
            }
        )

        val toml = mutableMapOf(
            "modLoader" to targetMetadata.modLoader.getOrElse("javafml"),
            "loaderVersion" to loaderVersionRange,
            "license" to commonMetadata.license.get(),
            "dependencies" to mapOf(commonMetadata.modId.get() to dependencies),
            "mixins" to mixinConfigs.map {
                mapOf("config" to it.name)
            },
        )

        if (commonMetadata.issues.isPresent) {
            toml["issueTrackerURL"] = commonMetadata.issues.get()
        }

        val mod = mutableMapOf("modId" to commonMetadata.modId.get(), "version" to modVersion.get())

        if (commonMetadata.name.isPresent) {
            mod["displayName"] = commonMetadata.name.get()
        }

        if (commonMetadata.description.isPresent) {
            mod["description"] = commonMetadata.description.get()
        }

        if (commonMetadata.icon.isPresent) {
            mod["logoFile"] = commonMetadata.icon.get()

            if (targetMetadata.blurLogo.isPresent) {
                toml["logoBlur"] = targetMetadata.blurLogo.get()
            }
        }

        if (commonMetadata.url.isPresent) {
            mod["displayURL"] = commonMetadata.url.get()
        }

        if (commonMetadata.contributors.get().isNotEmpty()) {
            mod["credits"] =
                "Contributors: ${commonMetadata.contributors.get().joinToString(transform = ::convertPerson)}"
        }

        if (commonMetadata.authors.get().isNotEmpty()) {
            mod["authors"] = commonMetadata.authors.get().joinToString(transform = ::convertPerson)
        }

        toml["mods"] = arrayOf(mod)

        val custom = commonMetadata.custom.get() + targetMetadata.modProperties.get()

        if (custom.isNotEmpty()) {
            toml["modproperties"] = custom.mapValues { (_, value) ->
                convertToObjectFromSerializable(value)
            }
        }

        output.outputStream().use {
            TomlWriter().write(toml, it)
        }
    }
}
