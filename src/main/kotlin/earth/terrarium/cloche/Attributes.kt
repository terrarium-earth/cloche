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

class SideCompatibilityRule : AttributeCompatibilityRule<PublicationSide> {
    override fun execute(details: CompatibilityCheckDetails<PublicationSide>) {
        if (details.producerValue == PublicationSide.Common || details.producerValue == PublicationSide.Joined) {
            details.compatible()
        } else {
            details.incompatible()
        }
    }
}

class SideDisambiguationRule : AttributeDisambiguationRule<PublicationSide> {
    override fun execute(details: MultipleCandidatesDetails<PublicationSide>) {
        if (details.consumerValue in details.candidateValues) {
            // Pick the requested variant
            details.closestMatch(details.consumerValue!!)
        } else if (details.consumerValue == PublicationSide.Client) {
            // Prefer joined if the consumer is client
            if (PublicationSide.Joined in details.candidateValues) {
                details.closestMatch(PublicationSide.Joined)
            } else if (PublicationSide.Common in details.candidateValues) {
                details.closestMatch(PublicationSide.Common)
            }
        } else {
            // Prefer common otherwise
            if (PublicationSide.Common in details.candidateValues) {
                details.closestMatch(PublicationSide.Common)
            } else if (PublicationSide.Joined in details.candidateValues) {
                details.closestMatch(PublicationSide.Joined)
            }
        }
    }
}

internal object ModTransformationStateAttribute {
    @JvmField
    val ATTRIBUTE: Attribute<String> = Attribute.of("earth.terrarium.cloche.modState", String::class.java)

    const val INITIAL = "none"

    fun of(target: MinecraftTarget, compilation: TargetCompilation, state: String) =
        lowerCamelCaseName(target.featureName, compilation.featureName, state)
}

@JvmField
val NO_NAME_MAPPING_ATTRIBUTE: Attribute<Boolean> = Attribute.of("earth.terrarium.cloche.noNameMappingService", Boolean::class.javaObjectType)

enum class IncludeTransformationState {
    None,
    Stripped,
    Extracted;

    companion object {
        @JvmField
        val ATTRIBUTE: Attribute<IncludeTransformationState> =
            Attribute.of("earth.terrarium.cloche.includeState", IncludeTransformationState::class.java)
    }
}