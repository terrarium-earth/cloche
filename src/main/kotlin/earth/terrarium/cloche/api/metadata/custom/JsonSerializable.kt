package earth.terrarium.cloche.api.metadata.custom

import kotlinx.serialization.json.*
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import javax.inject.Inject

private fun ObjectFactory.serializable(type: JsonType, builder: (JsonSerializable.() -> Unit)? = null) =
    newInstance(JsonSerializable::class.java).also {
        it.type.set(type)

        builder?.invoke(it)
    }

internal fun convertToSerializable(objectFactory: ObjectFactory, value: Any?): JsonSerializable = when (value) {
    null -> objectFactory.serializable(JsonType.Null)

    is Collection<*> -> objectFactory.serializable(JsonType.List) {
        listValues.addAll(value.map {
            convertToSerializable(objectFactory, it)
        })
    }

    is Map<*, *> -> objectFactory.serializable(JsonType.Object) {
        objectValues.putAll(value.map { (key, value) ->
            if (key !is String) {
                throw InvalidUserCodeException("Map key was $key of type ${key?.javaClass}, but only String is allowed")
            }

            key to convertToSerializable(
                objectFactory,
                value,
            )
        }.toMap())
    }

    is String -> objectFactory.serializable(JsonType.String) {
        stringValue.set(value)
    }

    is Boolean -> objectFactory.serializable(JsonType.Boolean) {
        booleanValue.set(value)
    }

    is Byte, is Short, is Int, is Long -> objectFactory.serializable(JsonType.Int) {
        intValue.set(value.toLong())
    }

    is Float, is Double -> objectFactory.serializable(JsonType.Float) {
        floatValue.set(value.toDouble())
    }

    else -> throw InvalidUserCodeException("Value $value of type ${value.javaClass} is not serializable.\n\nAccepted types are:\n\tnull,\n\tString,\n\tboolean,\n\tbyte/short/int/long,\n\tfloat/double,\n\tCollection<T>, and\n\tMap<String, T>")
}

internal fun convertToJsonFromSerializable(value: JsonSerializable): JsonElement = when (value.type.get()) {
    JsonType.Null -> JsonNull
    JsonType.Object -> JsonObject(value.objectValues.get().mapValues { (_, value) -> convertToJsonFromSerializable(value) })
    JsonType.List -> JsonArray(value.listValues.get().map(::convertToJsonFromSerializable))
    JsonType.String -> JsonPrimitive(value.stringValue.get())
    JsonType.Boolean -> JsonPrimitive(value.booleanValue.get())
    JsonType.Int -> JsonPrimitive(value.intValue.get())
    JsonType.Float -> JsonPrimitive(value.floatValue.get())
}

internal fun convertToObjectFromSerializable(value: JsonSerializable): Any? = when (value.type.get()) {
    JsonType.Null -> null
    JsonType.Object -> value.objectValues.get().mapValues { (_, value) -> convertToObjectFromSerializable(value) }
    JsonType.List -> value.listValues.get().map(::convertToObjectFromSerializable)
    JsonType.String -> value.stringValue.get()
    JsonType.Boolean -> value.booleanValue.get()
    JsonType.Int -> value.intValue.get()
    JsonType.Float -> value.floatValue.get()
}

enum class JsonType {
    Null,
    Object,
    List,
    String,
    Boolean,
    Int,
    Float,
}

/**
 * A cursed, but probably required way to represent arbitrary metadata in Gradle
 */
interface JsonSerializable {
    val type: Property<JsonType>
        @Input get

    val objectValues: MapProperty<String, JsonSerializable>
        @Nested get

    val listValues: ListProperty<JsonSerializable>
        @Nested get

    val stringValue: Property<String>
        @Optional
        @Input get

    val booleanValue: Property<Boolean>
        @Optional
        @Input get

    val intValue: Property<Long>
        @Optional
        @Input get

    val floatValue: Property<Double>
        @Optional
        @Input get

    val objects: ObjectFactory
        @Inject get

    operator fun plus(rightHandSide: JsonSerializable): JsonSerializable {
        val type = type.get()
        if (type != rightHandSide.type.get()) {
            throw UnsupportedOperationException("The left-hand side's type should be equal to the right-hand side's type.")
        }

        when (type) {
            JsonType.Object -> {
                val mergedJsonSerializable = objects.serializable(type)
                val mergedObjectValues = objectValues.get().toMutableMap()
                rightHandSide.objectValues.get().forEach { (key, value) ->
                    mergedObjectValues.merge(key, value) { oldValue, newValue -> oldValue + newValue }
                }
                mergedJsonSerializable.objectValues.set(mergedObjectValues)
                return mergedJsonSerializable
            }

            JsonType.List -> {
                val mergedJsonSerializable = objects.serializable(type)
                mergedJsonSerializable.listValues.set(listValues.get() + rightHandSide.listValues.get())
                return mergedJsonSerializable
            }

            else -> {
                return rightHandSide
            }
        }
    }
}
