@file:Suppress("UNCHECKED_CAST")

package earth.terrarium.cloche.metadata

import earth.terrarium.cloche.tasks.data.MetadataFileProvider
import earth.terrarium.cloche.tasks.data.decodeFromStream
import earth.terrarium.cloche.tasks.data.encodeToStream
import earth.terrarium.cloche.tasks.data.toml
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.lang.Closure
import groovy.toml.TomlBuilder
import groovy.toml.TomlSlurper
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import net.peanuuutz.tomlkt.TomlTable
import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.io.path.readText
import kotlin.io.path.writeText

private class MetadataFileProviderImpl<ElementT : Any>(
    private val readString: () -> String,
    private val readObject: () -> MutableMap<String, Any?>,
    private val readElement: () -> ElementT,
) : MetadataFileProvider<ElementT> {
    var builder: StringBuilder? = null
    var obj: Map<String, Any?>? = null
    var element: ElementT? = null

    override fun asString() = StringBuilder(readString()).also {
        builder = it
        obj = null
        element = null
    }

    override fun withContents(action: Action<MutableMap<String, Any?>>) {
        val newObj = readObject()

        builder = null
        obj = newObj
        element = null

        action.execute(newObj)
    }

    override fun withContents(closure: Closure<*>) = withContents {
        closure.rehydrate(it, this, this).call()
    }

    override fun withElement(action: ElementT.() -> ElementT) {
        builder = null
        obj = null
        element = action(readElement())
    }

    override fun withElement(closure: Closure<ElementT>) = withElement {
        val delegate = this@withElement
        val owner = this@MetadataFileProviderImpl

        closure.rehydrate(delegate, owner, owner).call()
    }
}

internal fun fabricJsonMetadataAction(output: Provider<RegularFile>, action: Action<MetadataFileProvider<JsonObject>>) = metadataAction(
    output,
    action,
    Json::decodeFromStream,
    Json::encodeToStream,
    ::decodeMapFromJson,
    ::encodeMapToJson,
)

internal fun forgeTomlMetadataAction(output: Provider<RegularFile>, action: Action<MetadataFileProvider<TomlTable>>) = metadataAction(
    output,
    action,
    toml::decodeFromStream,
    toml::encodeToStream,
    ::decodeMapFromToml,
    ::encodeMapToToml,
)

private fun <ElementT : Any> metadataAction(
    output: Provider<RegularFile>,
    action: Action<MetadataFileProvider<ElementT>>,
    readElement: (stream: InputStream) -> ElementT,
    writeElement: (ElementT, stream: OutputStream) -> Unit,
    readObject: (stream: InputStream) -> MutableMap<String, Any?>,
    writeObject: (Map<String, Any?>, output: Path) -> Unit,
): (Task) -> Unit {
    return action@{ _ ->
        val path = output.get().asFile.toPath()

        val provider = MetadataFileProviderImpl(
            { path.readText() },
            { path.inputStream().use(readObject) },
            { path.inputStream().use(readElement) },
        )

        action.execute(provider)

        if (provider.builder != null) {
            path.writeText(provider.builder.toString())
        } else if (provider.obj != null) {
            writeObject(provider.obj!!, path)
        } else if (provider.element != null) {
            path.outputStream().use {
                writeElement(provider.element!!, it)
            }
        }
    }
}

private fun decodeMapFromJson(stream: InputStream) =
    JsonSlurper().parse(stream) as MutableMap<String, Any?>

private fun encodeMapToJson(map: Map<String, Any?>, path: Path) {
    val builder = JsonBuilder()

    builder.call(map)

    path.writeText(builder.toString())
}

private fun decodeMapFromToml(stream: InputStream) =
    TomlSlurper().parse(stream) as MutableMap<String, Any?>

private fun encodeMapToToml(map: Map<String, Any?>, path: Path) {
    val builder = TomlBuilder()

    builder.call(map)

    path.writeText(builder.toString())
}
