package earth.terrarium.cloche

import org.gradle.api.attributes.*

@JvmField
val MINECRAFT_VERSION_ATTRIBUTE: Attribute<String> = Attribute.of("earth.terrarium.cloche.minecraftVersion", String::class.java)

@JvmField
val MOD_LOADER_ATTRIBUTE: Attribute<String> = Attribute.of("earth.terrarium.cloche.modLoader", String::class.java)

@JvmField
val VARIANT_ATTRIBUTE: Attribute<PublicationVariant> = Attribute.of("earth.terrarium.cloche.variant", PublicationVariant::class.java)

@JvmField
val COMMON_TYPE_ATTRIBUTE: Attribute<String> = Attribute.of("earth.terrarium.cloche.commonType", String::class.java)

@JvmField
val COMMON_NAME_ATTRIBUTE: Attribute<String> = Attribute.of("earth.terrarium.cloche.commonName", String::class.java)

@JvmField
internal val TARGET_MINECRAFT_ATTRIBUTE: Attribute<String> = Attribute.of("earth.terrarium.cloche.targetMinecraft", String::class.java)

class VariantCompatibilityRule : AttributeCompatibilityRule<PublicationVariant> {
    override fun execute(details: CompatibilityCheckDetails<PublicationVariant>) {
        if (details.consumerValue == details.producerValue || details.producerValue == PublicationVariant.Common) {
            details.compatible()
        } else {
            details.incompatible()
        }
    }
}
