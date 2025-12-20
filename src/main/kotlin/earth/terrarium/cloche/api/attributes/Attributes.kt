package earth.terrarium.cloche.api.attributes

import org.gradle.api.Named
import org.gradle.api.attributes.Attribute

object ClocheAttributes {
    @JvmField
    val CLOCHE_VERSION = Attribute.of("earth.terrarium.cloche.version", String::class.java)
}

object CompilationAttributes {
    @JvmField
    val DISTRIBUTION: Attribute<ModDistribution> =
        Attribute.of("io.github.mcgradleconventions.distribution", ModDistribution::class.java)

    @JvmField
    @Deprecated("Stop-gap for the migration to DISTRIBUTION", replaceWith = ReplaceWith("DISTRIBUTION"))
    val CLOCHE_SIDE: Attribute<String> =
        Attribute.of("earth.terrarium.cloche.side", String::class.java)

    @JvmField
    val DATA: Attribute<Boolean> = Attribute.of("earth.terrarium.cloche.data", Boolean::class.javaObjectType)
}

object CommonTargetAttributes {
    @JvmField
    val TYPE: Attribute<String> = Attribute.of("earth.terrarium.cloche.commonType", String::class.java)

    @JvmField
    val NAME: Attribute<String> = Attribute.of("earth.terrarium.cloche.commonName", String::class.java)
}

// https://github.com/mcgradleconventions
@Suppress("EnumEntryName")
enum class MinecraftModLoader : Named {
    fabric,
    forge,
    neoforge,
    common;

    override fun getName() = name
}

@Suppress("EnumEntryName")
enum class ModDistribution : Named {
    common,
    client;

    val legacyName get() =
        name[0].uppercase() + name.drop(1)

    override fun getName() = name
}

// Edge target attributes
object TargetAttributes {
    @JvmField
    val MINECRAFT_VERSION: Attribute<String> = Attribute.of("io.github.mcgradleconventions.version", String::class.java)

    @JvmField
    @Deprecated("Stop-gap for the migration to MINECRAFT_VERSION", replaceWith = ReplaceWith("MINECRAFT_VERSION"))
    val CLOCHE_MINECRAFT_VERSION: Attribute<String> = Attribute.of("earth.terrarium.cloche.minecraftVersion", String::class.java)

    @JvmField
    val MOD_LOADER: Attribute<MinecraftModLoader> = Attribute.of("io.github.mcgradleconventions.loader", MinecraftModLoader::class.java)

    @JvmField
    @Deprecated("Stop-gap for the migration to MOD_LOADER", replaceWith = ReplaceWith("MOD_LOADER"))
    val CLOCHE_MOD_LOADER: Attribute<MinecraftModLoader> = Attribute.of("earth.terrarium.cloche.modLoader", MinecraftModLoader::class.java)
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
