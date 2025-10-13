package earth.terrarium.cloche.metadata

import earth.terrarium.cloche.tasks.data.MetadataFileProvider
import earth.terrarium.cloche.tasks.data.decodeFromStream
import earth.terrarium.cloche.tasks.data.encodeToStream
import earth.terrarium.cloche.tasks.data.toml
import groovy.util.Node
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import net.peanuuutz.tomlkt.TomlArray
import net.peanuuutz.tomlkt.TomlElement
import net.peanuuutz.tomlkt.TomlLiteral
import net.peanuuutz.tomlkt.TomlNull
import net.peanuuutz.tomlkt.TomlTable
import net.peanuuutz.tomlkt.toBoolean
import net.peanuuutz.tomlkt.toFloat
import net.peanuuutz.tomlkt.toInt
import net.peanuuutz.tomlkt.toLocalDate
import net.peanuuutz.tomlkt.toLocalDateTime
import net.peanuuutz.tomlkt.toLocalTime
import net.peanuuutz.tomlkt.toOffsetDateTime
import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.io.path.readText
import kotlin.io.path.writeText

private class MetadataFileProviderImpl<ElementT : Any>(
    private val readString: () -> String,
    private val readElement: () -> ElementT,
    private val decodeNode: (ElementT) -> Node,
) : MetadataFileProvider<ElementT> {
    var builder: StringBuilder? = null
    var node: Node? = null
    var element: ElementT? = null

    override fun asString() = StringBuilder(readString()).also {
        builder = it
        node = null
        element = null
    }

    override fun asNode() = decodeNode(readElement()).also {
        builder = null
        node = it
        element = null
    }

    override fun asElement(): ElementT {
        builder = null
        node = null
        element = null

        return readElement()
    }

    override fun applyElement(element: ElementT) {
        builder = null
        node = null
        this.element = element
    }
}

internal fun fabricJsonMetadataAction(output: Provider<RegularFile>, action: Action<MetadataFileProvider<JsonObject>>) = metadataAction(
    output,
    action,
    Json::decodeFromStream,
    Json::encodeToStream,
    ::decodeNodeFromJsonObject,
    ::encodeNodeToJsonObject,
)

internal fun forgeTomlMetadataAction(output: Provider<RegularFile>, action: Action<MetadataFileProvider<TomlTable>>) = metadataAction(
    output,
    action,
    toml::decodeFromStream,
    toml::encodeToStream,
    ::decodeNodeFromTomlTable,
    ::encodeNodeToTomlTable,
)

private fun <ElementT : Any> metadataAction(
    output: Provider<RegularFile>,
    action: Action<MetadataFileProvider<ElementT>>,
    readElement: (stream: InputStream) -> ElementT,
    writeElement: (ElementT, stream: OutputStream) -> Unit,
    decodeNode: (ElementT) -> Node,
    encodeNode: (Node) -> ElementT,
): (Task) -> Unit {
    return action@{ _ ->
        val path = output.get().asFile.toPath()

        val provider = MetadataFileProviderImpl(
            { path.readText() },
            { path.inputStream().use(readElement) },
            decodeNode,
        )

        action.execute(provider)

        if (provider.builder != null) {
            path.writeText(provider.builder.toString())

            return@action
        }

        val element = provider.node?.let(encodeNode) ?: provider.element

        if (element != null) {
            path.outputStream().use {
                writeElement(element, it)
            }
        }
    }
}

private fun decodeNodeFromJsonObject(obj: JsonObject): Node {
    val node = Node(null, "root")

    for ((name, child) in obj.entries) {
        decodeNodeFromJsonElement(child, name, node)
    }

    return node
}

private fun decodeNodeFromJsonElement(element: JsonElement, name: String, parent: Node): Node {
    when (element) {
        is JsonNull -> {
            return Node(parent, name, null as Any?)
        }

        is JsonPrimitive -> {
            val values = listOf(element::booleanOrNull, element::intOrNull, element::longOrNull, element::floatOrNull, element::doubleOrNull, element::content)

            return Node(parent, name, values.firstNotNullOf { it() })
        }

        is JsonArray -> {
            val node = Node(parent, name, mapOf("array" to true))

            for (child in element) {
                decodeNodeFromJsonElement(child, "entry", node)
            }

            return parent
        }

        is JsonObject -> {
            val node = Node(parent, name, mapOf("array" to false))

            for ((name, child) in element.entries) {
                decodeNodeFromJsonElement(child, name, node)
            }

            return node
        }
    }
}

private fun decodeNodeFromTomlTable(table: TomlTable): Node {
    val node = Node(null, "root")

    for ((name, child) in table.entries) {
        encodeNodeToJsonElement(child, name, node)
    }

    return node
}

private fun encodeNodeToJsonElement(element: TomlElement, name: String, parent: Node): Node {
    when (element) {
        is TomlNull -> {
            return Node(parent, name, null as Any?)
        }

        is TomlLiteral -> {
            val value = when (element.type) {
                TomlLiteral.Type.Boolean -> element.toBoolean()
                TomlLiteral.Type.Integer -> element.toInt()
                TomlLiteral.Type.Float -> element.toFloat()
                TomlLiteral.Type.String -> element.content
                TomlLiteral.Type.LocalDateTime -> element.toLocalDateTime()
                TomlLiteral.Type.OffsetDateTime -> element.toOffsetDateTime()
                TomlLiteral.Type.LocalDate -> element.toLocalDate()
                TomlLiteral.Type.LocalTime -> element.toLocalTime()
            }

            return Node(parent, name, value)
        }

        is TomlArray -> {
            val node = Node(parent, name, mapOf("array" to true))

            for (child in element) {
                encodeNodeToJsonElement(child, "entry", node)
            }

            return parent
        }

        is TomlTable -> {
            val node = Node(parent, name, mapOf("array" to false))

            for ((name, child) in element.entries) {
                encodeNodeToJsonElement(child, name, node)
            }

            return node
        }
    }
}

private fun encodeNodeToJsonElement(node: Node): JsonElement = when (val value = node.value()) {
    is Collection<*> -> {
        if (node.attributes()["array"] == true) {
            JsonArray(node.children().map {
                encodeNodeToJsonElement(it as Node)
            })
        } else {
            encodeNodeToJsonObject(node)
        }
    }
    is Boolean -> JsonPrimitive(value)
    is Int -> JsonPrimitive(value)
    is Long -> JsonPrimitive(value)
    is Float -> JsonPrimitive(value)
    is Double -> JsonPrimitive(value)
    is String -> JsonPrimitive(value)
    null -> JsonNull
    else -> throw UnsupportedOperationException("Unexpected value when converting Node to JSON, $value of type ${value::class.java}")
}

private fun encodeNodeToJsonObject(node: Node): JsonObject {
    val content = buildMap {
        for (child in node.children()) {
            child as Node

            val key = child.name() as String

            put(key, encodeNodeToJsonElement(child))
        }
    }

    return JsonObject(content)
}

private fun encodeNodeToTomlElement(node: Node): TomlElement = when (val value = node.value()) {
    is Collection<*> -> {
        if (node.attributes()["array"] == true) {
            TomlArray(node.children().map {
                encodeNodeToTomlElement(it as Node)
            })
        } else {
            encodeNodeToTomlTable(node)
        }
    }
    is Boolean -> TomlLiteral(value)
    is Int -> TomlLiteral(value)
    is Float -> TomlLiteral(value)
    is String -> TomlLiteral(value)
    is LocalDateTime -> TomlLiteral(value)
    is OffsetDateTime -> TomlLiteral(value)
    is LocalDate -> TomlLiteral(value)
    is LocalTime -> TomlLiteral(value)
    null -> TomlNull
    else -> throw UnsupportedOperationException("Unexpected value when converting Node to TOML, $value of type ${value::class.java}")
}

private fun encodeNodeToTomlTable(node: Node): TomlTable {
    val content = buildMap {
        for (child in node.children()) {
            child as Node

            val key = child.name() as String

            put(key, encodeNodeToJsonElement(child))
        }
    }

    return TomlTable(content)
}
