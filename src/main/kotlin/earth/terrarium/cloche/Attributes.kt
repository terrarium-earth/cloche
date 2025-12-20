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
val REMAPPED_ATTRIBUTE: Attribute<Boolean> =
    Attribute.of("earth.terrarium.cloche.remapped", Boolean::class.javaObjectType)

/**
 * Indicates that the variant has includes fully resolved/handled
 */
@JvmField
val INCLUDE_TRANSFORMED_OUTPUT_ATTRIBUTE: Attribute<Boolean> =
    Attribute.of("earth.terrarium.cloche.includeTransformedOutput", Boolean::class.javaObjectType)

/**
 * Indicates that the forge mapping service has been stripped
 */
@JvmField
val NO_NAME_MAPPING_ATTRIBUTE: Attribute<Boolean> =
    Attribute.of("earth.terrarium.cloche.noNameMappingService", Boolean::class.javaObjectType)

class ClocheVersion(val versionName: String) : Comparable<ClocheVersion> {
    private val versionValue = run {
        val parts = versionName.split('.')

        require(parts.size == 3) {
            "Expected version to contain three parts, but version $versionName contained ${parts.size}"
        }

        val (major, minor, patch) = parts.map(String::toInt)

        major * 1_000_000 + minor * 1_000 + patch
    }

    override fun compareTo(other: ClocheVersion) = versionValue.compareTo(other.versionValue)
}

class ClocheVersionCompatibilityRule : AttributeCompatibilityRule<String> {
    override fun execute(details: CompatibilityCheckDetails<String>) {
        details.compatible()
    }
}

class ClocheVersionDisambiguationRule : AttributeDisambiguationRule<String> {
    override fun execute(details: MultipleCandidatesDetails<String>) {
        val targetVersionName = details.consumerValue ?: ClochePlugin::class.java.`package`.implementationVersion

        if (targetVersionName != null && targetVersionName in details.candidateValues) {
            details.closestMatch(targetVersionName)
            return
        }

        val versions = details.candidateValues.map(::ClocheVersion).sorted()

        if (targetVersionName == null) {
            // No known target version, pick latest
            details.closestMatch(versions.last().versionName)
            return
        }

        val targetVersion = ClocheVersion(targetVersionName)

        val index = versions.binarySearch(targetVersion)
        val insertionPoint = -index - 1

        val closestVersion = if (insertionPoint == 0) {
            // Version is smaller than any of the candidates, so pick the lowest version
            versions.first()
        } else {
            // Pick the version directly before the target in the candidate list
            versions[insertionPoint - 1]
        }

        details.closestMatch(closestVersion.versionName)
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
