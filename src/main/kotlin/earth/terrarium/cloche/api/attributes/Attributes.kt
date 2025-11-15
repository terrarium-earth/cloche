package earth.terrarium.cloche.api.attributes

import org.gradle.api.Named
import org.gradle.api.attributes.Attribute

object CompilationAttributes {
    @JvmField
    val DISTRIBUTION: Attribute<ModDistribution> =
        Attribute.of("io.github.mcgradleconventions.distribution", ModDistribution::class.java)

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

    override fun getName() = name
}

// Edge target attributes
object TargetAttributes {
    @JvmField
    val MINECRAFT_VERSION: Attribute<String> = Attribute.of("io.github.mcgradleconventions.version", String::class.java)

    @JvmField
    val MOD_LOADER: Attribute<MinecraftModLoader> = Attribute.of("io.github.mcgradleconventions.loader", MinecraftModLoader::class.java)
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
