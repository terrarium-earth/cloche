package earth.terrarium.cloche

import earth.terrarium.cloche.api.target.MinecraftTarget
import earth.terrarium.cloche.target.TargetCompilation
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseName
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.AttributeDisambiguationRule
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.attributes.MultipleCandidatesDetails

@JvmField
val SIDE_ATTRIBUTE: Attribute<PublicationSide> = Attribute.of("earth.terrarium.cloche.side", PublicationSide::class.java)

@JvmField
val DATA_ATTRIBUTE: Attribute<Boolean> = Attribute.of("earth.terrarium.cloche.data", Boolean::class.javaObjectType)

@JvmField
val NO_NAME_MAPPING_ATTRIBUTE: Attribute<Boolean> = Attribute.of("earth.terrarium.cloche.noNameMappingService", Boolean::class.javaObjectType)

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

class VariantCompatibilityRule : AttributeCompatibilityRule<PublicationSide> {
    override fun execute(details: CompatibilityCheckDetails<PublicationSide>) {
        if (details.consumerValue == details.producerValue || details.producerValue == PublicationSide.Common || details.producerValue == PublicationSide.Joined) {
            details.compatible()
        } else {
            details.incompatible()
        }
    }
}

class VariantDisambiguationRule : AttributeDisambiguationRule<PublicationSide> {
    override fun execute(details: MultipleCandidatesDetails<PublicationSide>) {
        if (PublicationSide.Common in details.candidateValues) {
            details.closestMatch(PublicationSide.Common)
        } else if (PublicationSide.Joined in details.candidateValues) {
            details.closestMatch(PublicationSide.Joined)
        }
    }
}

internal object ModTransformationStateAttribute {
    @JvmField
    val ATTRIBUTE: Attribute<String> = Attribute.of("earth.terrarium.cloche.modState", String::class.java)

    const val INITIAL = "none"
    const val REMAPPED = "remapped"
    const val MIXINS_STRIPPED = "mixinsStripped"

    fun of(target: MinecraftTarget, compilation: TargetCompilation, state: String) =
        lowerCamelCaseName(target.featureName, compilation.featureName, state)
}