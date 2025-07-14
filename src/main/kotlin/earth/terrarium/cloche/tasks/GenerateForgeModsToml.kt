package earth.terrarium.cloche.tasks

import com.moandjiezana.toml.TomlWriter
import earth.terrarium.cloche.api.metadata.ForgeMetadata
import earth.terrarium.cloche.api.metadata.Metadata
import earth.terrarium.cloche.api.metadata.custom.convertToObjectFromSerializable
import net.msrandom.minecraftcodev.core.utils.getAsPath
import org.gradle.api.DefaultTask
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
    abstract val loaderDependencyVersion: Property<Metadata.VersionRange>
        @Nested get

    abstract val output: RegularFileProperty
        @OutputFile get

    abstract val metadata: Property<ForgeMetadata>
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

    private fun Metadata.Environment.toForgeString(): String {
        return when (this) {
            Metadata.Environment.CLIENT -> {
                "CLIENT"
            }

            Metadata.Environment.SERVER -> {
                "SERVER"
            }

            Metadata.Environment.BOTH -> {
                "BOTH"
            }
        }
    }

    private fun Metadata.Dependency.Type.toForgeString(): String {
        return when (this) {
            Metadata.Dependency.Type.REQUIRED -> {
                "required"
            }

            Metadata.Dependency.Type.RECOMMENDED -> {
                "optional"
            }

            Metadata.Dependency.Type.SUGGESTED -> {
                "optional"
            }

            Metadata.Dependency.Type.CONFLICTS -> {
                "discouraged"
            }

            Metadata.Dependency.Type.BREAKS -> {
                "incompatible"
            }
        }
    }

    private fun buildVersionRange(range: Metadata.VersionRange): String {
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

    private fun convertPerson(person: Metadata.Person) = if (person.contact.isPresent) {
        "${person.name.get()} <${person.contact.get()}>"
    } else {
        person.name.get()
    }

    @TaskAction
    fun makeToml() {
        val output = output.getAsPath()
        val metadata = metadata.get()
        val loaderVersionRange = buildVersionRange(loaderDependencyVersion.get())
        val dependencies: MutableList<Map<String, Any>> = mutableListOf(
            mapOf(
                "modId" to loaderName.get(),
                "type" to "required",
                "mandatory" to true,
                "versionRange" to loaderVersionRange,
            )
        )

        dependencies.addAll(
            metadata.dependencies.get().map { dependency ->
                val dependencyType = dependency.type.getOrElse(Metadata.Dependency.Type.REQUIRED)
                val map: MutableMap<String, Any> = mutableMapOf(
                    "modId" to dependency.modId.get(),
                    // TODO: Don't add both the `mandatory` and `type` fields
                    "mandatory" to dependencyType,
                    "type" to dependencyType.toForgeString(),
                    "ordering" to dependency.ordering.getOrElse(Metadata.Dependency.Ordering.NONE),
                    "side" to dependency.environment.getOrElse(Metadata.Environment.BOTH).toForgeString()
                )
                if (dependency.reason.isPresent) {
                    map["reason"] = dependency.reason.get()
                }

                dependency.version.map { buildVersionRange(it) }.orNull.let {
                    map["versionRange"] = it ?: "[0,)"
                }

                map
            }
        )

        val toml = mutableMapOf(
            "modLoader" to metadata.modLoader.get(),
            "loaderVersion" to loaderVersionRange,
            "license" to metadata.license.get(),
            "dependencies" to mapOf(metadata.modId.get() to dependencies),
            "mixins" to mixinConfigs.map {
                mapOf("config" to it.name)
            },
        )

        if (metadata.issues.isPresent) {
            toml["issueTrackerURL"] = metadata.issues.get()
        }

        val mod = mutableMapOf("modId" to metadata.modId.get(), "version" to modVersion.get())

        if (metadata.name.isPresent) {
            mod["displayName"] = metadata.name.get()
        }

        if (metadata.description.isPresent) {
            mod["description"] = metadata.description.get()
        }

        if (metadata.icon.isPresent) {
            mod["logoFile"] = metadata.icon.get()

            if (metadata.blurLogo.isPresent) {
                toml["logoBlur"] = metadata.blurLogo.get()
            }
        }

        if (metadata.url.isPresent) {
            mod["displayURL"] = metadata.url.get()
        }

        if (metadata.contributors.get().isNotEmpty()) {
            mod["credits"] =
                "Contributors: ${metadata.contributors.get().joinToString(transform = ::convertPerson)}"
        }

        if (metadata.authors.get().isNotEmpty()) {
            mod["authors"] = metadata.authors.get().joinToString(transform = ::convertPerson)
        }

        toml["mods"] = arrayOf(mod)

        val custom = metadata.custom.get()
        if (custom.isNotEmpty()) {
            custom.mapValues { (key, value) ->
                toml[key] = convertToObjectFromSerializable(value)
            }
        }

        val modProperties = metadata.modProperties.get()
        if (modProperties.isNotEmpty()) {
            toml["modproperties"] = modProperties.mapValues { (_, value) ->
                convertToObjectFromSerializable(value)
            }
        }

        output.outputStream().use {
            TomlWriter().write(toml, it)
        }
    }
}
