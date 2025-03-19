package earth.terrarium.cloche.tasks

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.put
import net.msrandom.minecraftcodev.core.utils.getAsPath
import net.msrandom.minecraftcodev.core.utils.zipFileSystem
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

private val JSON = Json { prettyPrint = true }

abstract class GenerateModJsonJarsEntry : DefaultTask() {

    abstract val jar: RegularFileProperty
        @InputFile
        get

    abstract val includedJars: ConfigurableFileCollection
        @PathSensitive(PathSensitivity.RELATIVE)
        @InputFiles
        get

    @TaskAction
    fun processFabricModJson() {
        if(includedJars.isEmpty) return
        zipFileSystem(jar.getAsPath()).use {
            val input = it.getPath("fabric.mod.json")
            if (!input.exists()) throw IllegalStateException("Cannot process nonexistent mod json")
            val inputJson = input.inputStream().use {
                JSON.decodeFromStream<JsonObject>(it)
            }

            val json = buildJsonObject {
                inputJson.forEach { (key, value) -> put(key, value) }
                put("jars", JsonArray(includedJars.map {
                    buildJsonObject {
                        put("file", "META-INF/jars/${it.name}")
                    }
                }))
            }

            input.outputStream().use {
                JSON.encodeToStream(json, it)
            }
        }
    }
}