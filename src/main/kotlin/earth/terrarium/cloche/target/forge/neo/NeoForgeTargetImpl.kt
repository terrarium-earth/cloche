package earth.terrarium.cloche.target.forge.neo

import earth.terrarium.cloche.NEOFORGE
import earth.terrarium.cloche.api.target.NeoforgeTarget
import earth.terrarium.cloche.target.CompilationInternal
import earth.terrarium.cloche.target.forge.ForgeLikeTargetImpl
import net.msrandom.minecraftcodev.core.operatingSystemName
import net.msrandom.minecraftcodev.forge.MinecraftCodevForgePlugin
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.provider.Provider
import javax.inject.Inject

private val NEOFORGE_DISTRIBUTION_ATTRIBUTE = Attribute.of("net.neoforged.distribution", String::class.java)
private val NEOFORGE_OPERATING_SYSTEM_ATTRIBUTE = Attribute.of("net.neoforged.operatingsystem", String::class.java)

internal abstract class NeoForgeTargetImpl @Inject constructor(name: String) : ForgeLikeTargetImpl(name), NeoforgeTarget {
    final override val group
        get() = "net.neoforged"

    final override val artifact
        get() = "neoforge"

    final override val loaderName get() = NEOFORGE

    override val minecraftRemapNamespace: Provider<String>
        get() = mappings.isDefault.map {
            if (it) {
                ""
            } else {
                MinecraftCodevForgePlugin.SRG_MAPPINGS_NAMESPACE
            }
        }

    override val modRemapNamespace: Provider<String>
        get() = mappings.isOfficialCompatible.map {
            if (it) {
                ""
            } else {
                MinecraftCodevForgePlugin.SRG_MAPPINGS_NAMESPACE
            }
        }

    init {
        minecraftLibrariesConfiguration.attributes {
            it.attribute(NEOFORGE_DISTRIBUTION_ATTRIBUTE, "client")

            it.attribute(
                NEOFORGE_OPERATING_SYSTEM_ATTRIBUTE,
                operatingSystemName(),
            )
        }

        generateModsToml.configure {
            it.loaderDependencyVersion.set(metadata.loaderVersion.orElse(loaderVersionRange("1")))

            it.output.set(metadataDirectory.map {
                it.dir("META-INF").file("neoforge.mods.toml")
            })

            it.mixinConfigs.from(mixins)
        }

        resolvePatchedMinecraft.configure {
            it.neoforge.set(true)
        }
    }

    private fun addAttributes(attributeContainer: AttributeContainer) {
        attributeContainer.attribute(NEOFORGE_DISTRIBUTION_ATTRIBUTE, "client")

        attributeContainer.attribute(
            NEOFORGE_OPERATING_SYSTEM_ATTRIBUTE,
            operatingSystemName(),
        )
    }

    override fun initialize(isSingleTarget: Boolean) {
        super.initialize(isSingleTarget)

        attributes(::addAttributes)

        data.onConfigured {
            it.attributes(::addAttributes)
        }
    }

    final override fun version(minecraftVersion: String, loaderVersion: String) =
        loaderVersion
}
