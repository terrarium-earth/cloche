package earth.terrarium.cloche

import earth.terrarium.cloche.target.MinecraftTarget
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseName
import org.gradle.api.attributes.*

@JvmField
val VARIANT_ATTRIBUTE: Attribute<PublicationVariant> = Attribute.of("earth.terrarium.cloche.variant", PublicationVariant::class.java)

// Edge target attributes
object TargetAttributes {
    @JvmField
    val MINECRAFT_VERSION: Attribute<String> = Attribute.of("earth.terrarium.cloche.minecraftVersion", String::class.java)

    @JvmField
    val MOD_LOADER: Attribute<String> = Attribute.of("earth.terrarium.cloche.modLoader", String::class.java)
}

object CommonTargetAttributes {
    @JvmField
    val TYPE: Attribute<String> = Attribute.of("earth.terrarium.cloche.commonType", String::class.java)

    @JvmField
    val NAME: Attribute<String> = Attribute.of("earth.terrarium.cloche.commonName", String::class.java)
}

class VariantCompatibilityRule : AttributeCompatibilityRule<PublicationVariant> {
    override fun execute(details: CompatibilityCheckDetails<PublicationVariant>) {
        if (details.consumerValue == details.producerValue || details.producerValue == PublicationVariant.Common || details.producerValue == PublicationVariant.Joined) {
            details.compatible()
        } else {
            details.incompatible()
        }
    }
}

class VariantDisambiguationRule : AttributeDisambiguationRule<PublicationVariant> {
    override fun execute(details: MultipleCandidatesDetails<PublicationVariant>) {
        if (PublicationVariant.Common in details.candidateValues) {
            details.closestMatch(PublicationVariant.Common)
        } else if (PublicationVariant.Joined in details.candidateValues) {
            details.closestMatch(PublicationVariant.Joined)
        }
    }
}

internal object ModTransformationStateAttribute {
    @JvmField
    val ATTRIBUTE: Attribute<String> = Attribute.of("earth.terrarium.cloche.modState", String::class.java)

    const val INITIAL = "none"

    fun of(target: MinecraftTarget<*>, state: String) =
        lowerCamelCaseName(target.featureName, state)
}
