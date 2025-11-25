package earth.terrarium.cloche

import earth.terrarium.cloche.api.attributes.ModDistribution
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

object ClocheTargetAttribute {
    val ATTRIBUTE = Attribute.of("earth.terrarium.cloche.target", String::class.java)
    const val INITIAL = "none"

    class CompatibilityRule : AttributeCompatibilityRule<String> {
        override fun execute(details: CompatibilityCheckDetails<String>) {
            if (details.producerValue == INITIAL || details.consumerValue == INITIAL) {
                details.compatible()
            }
        }
    }
}

class DistributionCompatibilityRule : AttributeCompatibilityRule<ModDistribution> {
    override fun execute(details: CompatibilityCheckDetails<ModDistribution>) {
        details.compatible()
    }
}

class DistributionDisambiguationRule : AttributeDisambiguationRule<ModDistribution> {
    override fun execute(details: MultipleCandidatesDetails<ModDistribution>) {
        if (details.consumerValue in details.candidateValues) {
            // Pick the requested variant
            details.closestMatch(details.consumerValue!!)
        } else if (details.consumerValue == ModDistribution.client && ModDistribution.common in details.candidateValues) {
            details.closestMatch(ModDistribution.common)
        }
    }
}

@Deprecated("Stop-gap for migration to DISTRIBUTION attribute")
class ClocheSideCompatibilityRule : AttributeCompatibilityRule<String> {
    override fun execute(details: CompatibilityCheckDetails<String>) {
        details.compatible()
    }
}

@Deprecated("Stop-gap for migration to DISTRIBUTION attribute")
class ClocheSideDisambiguationRule : AttributeDisambiguationRule<String> {
    override fun execute(details: MultipleCandidatesDetails<String>) {
        if (details.consumerValue in details.candidateValues) {
            // Pick the requested variant
            details.closestMatch(details.consumerValue!!)
        } else if (details.consumerValue == ModDistribution.client.legacyName && ModDistribution.common.legacyName in details.candidateValues) {
            details.closestMatch(ModDistribution.common.legacyName)
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
