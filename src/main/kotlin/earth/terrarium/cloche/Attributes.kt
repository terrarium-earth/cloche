package earth.terrarium.cloche

import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.AttributeDisambiguationRule
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.attributes.MultipleCandidatesDetails

/**
 * Indicates that the variant is fully remapped
 */
@JvmField
val REMAPPED_ATTRIBUTE: Attribute<Boolean> = Attribute.of("earth.terrarium.cloche.remapped", Boolean::class.javaObjectType)

/**
 * Indicates that the variant has includes fully resolved/handled
 */
@JvmField
val INCLUDE_TRANSFORMED_OUTPUT_ATTRIBUTE: Attribute<Boolean> = Attribute.of("earth.terrarium.cloche.includeTransformedOutput", Boolean::class.javaObjectType)

/**
 * Indicates that the forge mapping service has been stripped
 */
@JvmField
val NO_NAME_MAPPING_ATTRIBUTE: Attribute<Boolean> = Attribute.of("earth.terrarium.cloche.noNameMappingService", Boolean::class.javaObjectType)

val CLOCHE_TARGET_ATTRIBUTE: Attribute<String> = Attribute.of("earth.terrarium.cloche.target", String::class.java)

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

class DataCompatibilityRule : AttributeCompatibilityRule<Boolean> {
    override fun execute(details: CompatibilityCheckDetails<Boolean>) {
        details.compatible()
    }
}

class DataDisambiguationRule : AttributeDisambiguationRule<Boolean> {
    override fun execute(details: MultipleCandidatesDetails<Boolean>) {
        if (details.consumerValue == true && true in details.candidateValues) {
            details.closestMatch(false)
        } else if (false in details.candidateValues) {
            details.closestMatch(false)
        }
    }
}
