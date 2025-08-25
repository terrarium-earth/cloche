package earth.terrarium.cloche.api.attributes

import earth.terrarium.cloche.PublicationSide
import org.gradle.api.attributes.Attribute

object CompilationAttributes {
    @JvmField
    val SIDE: Attribute<PublicationSide> =
        Attribute.of("earth.terrarium.cloche.side", PublicationSide::class.java)

    @JvmField
    val DATA: Attribute<Boolean> = Attribute.of("earth.terrarium.cloche.data", Boolean::class.javaObjectType)
}

object CommonTargetAttributes {
    @JvmField
    val TYPE: Attribute<String> = Attribute.of("earth.terrarium.cloche.commonType", String::class.java)

    @JvmField
    val NAME: Attribute<String> = Attribute.of("earth.terrarium.cloche.commonName", String::class.java)
}

// Edge target attributes
object TargetAttributes {
    @JvmField
    val MINECRAFT_VERSION: Attribute<String> = Attribute.of("earth.terrarium.cloche.minecraftVersion", String::class.java)

    @JvmField
    val MOD_LOADER: Attribute<String> = Attribute.of("earth.terrarium.cloche.modLoader", String::class.java)
}

enum class IncludeTransformationStateAttribute {
    None,
    Stripped,
    Extracted;

    companion object {
        @JvmField
        val ATTRIBUTE: Attribute<IncludeTransformationStateAttribute> =
            Attribute.of("earth.terrarium.cloche.includeState", IncludeTransformationStateAttribute::class.java)
    }
}
