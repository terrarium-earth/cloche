package earth.terrarium.cloche.tasks

import earth.terrarium.cloche.api.metadata.CommonMetadata
import earth.terrarium.cloche.api.metadata.ForgeMetadata
import earth.terrarium.cloche.api.metadata.custom.convertToTomlFromSerializable
import earth.terrarium.cloche.metadata.forgeTomlMetadataAction
import earth.terrarium.cloche.modId
import earth.terrarium.cloche.tasks.data.ForgeMod
import earth.terrarium.cloche.tasks.data.ForgeMods
import earth.terrarium.cloche.tasks.data.MetadataFileProvider
import earth.terrarium.cloche.tasks.data.NeoForgeMods
import earth.terrarium.cloche.tasks.data.encodeToStream
import earth.terrarium.cloche.tasks.data.toml
import net.msrandom.minecraftcodev.core.utils.getAsPath
import net.peanuuutz.tomlkt.TomlTable
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import kotlin.io.path.outputStream

abstract class GenerateForgeModsToml : DefaultTask() {
    abstract val loaderDependencyVersion: Property<CommonMetadata.VersionRange>
        @Nested get

    abstract val output: RegularFileProperty
        @OutputFile get

    abstract val modId: Property<String>
        @Internal get

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

    abstract val neoforge: Property<Boolean>
        @Input get

    init {
        modId.convention(project.modId)
        modVersion.convention(project.provider { project.version.toString() })
    }

    fun withToml(action: Action<MetadataFileProvider<TomlTable>>) {
        doLast(forgeTomlMetadataAction(output, action))
    }

    private fun CommonMetadata.Dependency.Type.toNeoforgeType() = when (this) {
        CommonMetadata.Dependency.Type.Required -> NeoForgeMods.Dependency.Type.Required
        CommonMetadata.Dependency.Type.Recommended, CommonMetadata.Dependency.Type.Suggested -> NeoForgeMods.Dependency.Type.Optional
        CommonMetadata.Dependency.Type.Conflicts -> NeoForgeMods.Dependency.Type.Discouraged
        CommonMetadata.Dependency.Type.Breaks -> NeoForgeMods.Dependency.Type.Incompatible
    }

    private fun buildVersionRange(range: CommonMetadata.VersionRange?): String {
        if (range == null || !range.start.isPresent && !range.end.isPresent) {
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

    private fun convertPerson(person: CommonMetadata.Person) = if (person.contact.isPresent) {
        "${person.name.get()} <${person.contact.get()}>"
    } else {
        person.name.get()
    }

    @TaskAction
    fun makeToml() {
        val output = output.getAsPath()
        val modId = modId.get()
        val metadata = metadata.get()
        val loaderVersionRange = buildVersionRange(loaderDependencyVersion.get())
        val dependencies: MutableList<Map<String, Any>> = mutableListOf()

        dependencies.addAll(
            metadata.dependencies.get().map { dependency ->
                val dependencyType = dependency.type.getOrElse(CommonMetadata.Dependency.Type.Required)

                val map: MutableMap<String, Any> = mutableMapOf(
                    "modId" to dependency.modId.get(),
                    // TODO: Don't add both the `mandatory` and `type` fields
                    "mandatory" to (dependencyType == CommonMetadata.Dependency.Type.Required),
                    "type" to dependencyType.toNeoforgeType(),
                    "ordering" to dependency.ordering.getOrElse(CommonMetadata.Dependency.Ordering.None),
                    "side" to dependency.environment.getOrElse(CommonMetadata.Environment.Both).name.uppercase()
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

        val authors = metadata.authors.get().takeUnless { it.isEmpty() }?.joinToString(transform = ::convertPerson)

        val credits = metadata.contributors.get().takeUnless { it.isEmpty() }?.let {
            "Contributors: ${it.joinToString(transform = ::convertPerson)}"
        }

        val modProperties = (metadata.custom.get() + metadata.modProperties.get()).mapValues { (_, value) ->
            convertToTomlFromSerializable(value)
        }

        val mod = ForgeMod(
            modId = modId,
            version = modVersion.get(),
            displayName = metadata.name.orNull,
            description = metadata.description.orNull,
            logoFile = metadata.icon.orNull,
            displayURL = metadata.url.orNull,
            credits = credits,
            authors = authors,
            modproperties = modProperties,
        )

        if (neoforge.get()) {
            val dependencies = metadata.dependencies.get().map {
                NeoForgeMods.Dependency(
                    modId = it.modId.get(),
                    type = it.type.getOrElse(CommonMetadata.Dependency.Type.Required).toNeoforgeType(),
                    versionRange = buildVersionRange(it.version.orNull),
                    reason = it.reason.orNull,
                    ordering = it.ordering.getOrElse(CommonMetadata.Dependency.Ordering.None),
                    side = it.environment.getOrElse(CommonMetadata.Environment.Both),
                )
            }

            val mods = NeoForgeMods(
                modLoader = metadata.modLoader.get(),
                loaderVersion = loaderVersionRange,
                license = metadata.license.getOrElse("ARR"),
                issueTrackerURL = metadata.issues.orNull,
                mixins = mixinConfigs.map {
                    NeoForgeMods.Mixin(it.name)
                },
                mods = listOf(mod),
                dependencies = mapOf(modId to dependencies),
                logoBlur = metadata.blurLogo.orNull,
            )

            output.outputStream().use {
                toml.encodeToStream(mods, it)
            }
        } else {
            val dependencies = metadata.dependencies.get().map {
                ForgeMods.Dependency(
                    modId = it.modId.get(),
                    mandatory = it.type.getOrElse(CommonMetadata.Dependency.Type.Required) == CommonMetadata.Dependency.Type.Required,
                    versionRange = buildVersionRange(it.version.orNull),
                    ordering = it.ordering.getOrElse(CommonMetadata.Dependency.Ordering.None),
                    side = it.environment.getOrElse(CommonMetadata.Environment.Both),
                )
            }

            val mods = ForgeMods(
                modLoader = metadata.modLoader.get(),
                loaderVersion = loaderVersionRange,
                license = metadata.license.getOrElse("ARR"),
                issueTrackerURL = metadata.issues.orNull,
                mods = listOf(mod),
                dependencies = mapOf(modId to dependencies),
                logoBlur = metadata.blurLogo.orNull,
            )

            output.outputStream().use {
                toml.encodeToStream(mods, it)
            }
        }
    }
}
