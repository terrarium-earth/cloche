package earth.terrarium.cloche.tasks

import earth.terrarium.cloche.api.metadata.CommonMetadata
import earth.terrarium.cloche.api.metadata.FabricMetadata
import earth.terrarium.cloche.api.metadata.custom.convertToJsonFromSerializable
import earth.terrarium.cloche.metadata.fabricJsonMetadataAction
import earth.terrarium.cloche.modId
import earth.terrarium.cloche.tasks.data.FabricMod
import earth.terrarium.cloche.tasks.data.MetadataFileProvider
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToStream
import net.msrandom.minecraftcodev.core.utils.getAsPath
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
import java.io.File
import kotlin.io.path.outputStream

@OptIn(ExperimentalSerializationApi::class)
abstract class GenerateFabricModJson : DefaultTask() {
    abstract val loaderDependencyVersion: Property<String>
        @Input get

    abstract val output: RegularFileProperty
        @OutputFile get

    abstract val modId: Property<String>
        @Internal get

    abstract val targetMetadata: Property<FabricMetadata>
        @Nested get

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
        modId.convention(project.modId)
        modVersion.convention(project.provider { project.version.toString() })
    }

    fun withJson(action: Action<MetadataFileProvider<JsonObject>>) {
        doLast(fabricJsonMetadataAction(output, action))
    }

    private fun buildVersionRange(range: CommonMetadata.VersionRange): String? {
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

    private fun convertPerson(person: CommonMetadata.Person) = FabricMod.Person(
        person.name.get(),
        person.contact.orNull?.let {
            FabricMod.Contact(email = it)
        },
    )

    @TaskAction
    fun makeJson() {
        val modId = modId.get()
        val metadata = targetMetadata.get()

        val authors = metadata.authors.get().map(::convertPerson)
        val contributors = metadata.contributors.get().map(::convertPerson)

        val contact = if (metadata.url.isPresent || metadata.issues.isPresent || metadata.sources.isPresent) {
            FabricMod.Contact(
                homepage = metadata.url.orNull,
                issues = metadata.issues.orNull,
                sources = metadata.sources.orNull,
            )
        } else {
            null
        }

        val mixinNames = mixinConfigs.map(File::getName)
        val clientMixinNames = clientMixinConfigs.map(File::getName) - mixinNames.toSet()

        val commonMixins = mixinNames.map(FabricMod::Mixin)

        val clientMixins = clientMixinNames.map {
            FabricMod.Mixin(it, "client")
        }

        val entrypoints = metadata.entrypoints?.mapValues { (_, values) ->
            values.get().map { entrypoint ->
                FabricMod.Entrypoint(
                    value = entrypoint.value.get(),
                    adapter = entrypoint.adapter.orNull,
                )
            }
        } ?: emptyMap()

        val dependencies = metadata.dependencies.get()

        val depends = mutableMapOf<String, String>()
        val recommends = mutableMapOf<String, String>()
        val suggests = mutableMapOf<String, String>()
        val conflicts = mutableMapOf<String, String>()
        val breaks = mutableMapOf<String, String>()

        depends["fabricloader"] = ">=${loaderDependencyVersion.get()}"

        for (dependency in dependencies) {
            val key = dependency.modId.get()
            val version = dependency.version.map { buildVersionRange(it) ?: "*" }.getOrElse("*")
            when (dependency.type.getOrElse(CommonMetadata.Dependency.Type.Required)) {
                CommonMetadata.Dependency.Type.Required -> {
                    depends[key] = version
                }

                CommonMetadata.Dependency.Type.Recommended -> {
                    recommends[key] = version
                }

                CommonMetadata.Dependency.Type.Suggested -> {
                    suggests[key] = version
                }

                CommonMetadata.Dependency.Type.Conflicts -> {
                    conflicts[key] = version
                }

                CommonMetadata.Dependency.Type.Breaks -> {
                    breaks[key] = version
                }
            }
        }

        val modJson = FabricMod(
            schemaVersion = 1,
            id = modId,
            version = modVersion.get(),
            name = metadata.name.orNull,
            environment = metadata.environment.getOrElse(CommonMetadata.Environment.Both),
            description = metadata.description.orNull,
            authors = authors,
            contributors = contributors,
            contact = contact,
            license = metadata.license.orNull,
            icon = metadata.icon.orNull,
            mixins = commonMixins + clientMixins,
            entrypoints = entrypoints,
            languageAdapters = metadata.languageAdapters.get(),
            depends = depends,
            suggests = suggests,
            custom = metadata.custom.get().mapValues { (_, value) -> convertToJsonFromSerializable(value) },
        )

        output.getAsPath().outputStream().use {
            jsonBuilder.encodeToStream(modJson, it)
        }
    }
}
