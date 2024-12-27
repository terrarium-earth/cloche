package earth.terrarium.cloche.target

import earth.terrarium.cloche.NEOFORGE
import org.gradle.api.provider.Provider
import javax.inject.Inject

internal abstract class NeoForgeTargetImpl @Inject constructor(name: String): ForgeTargetImpl(name), NeoforgeTarget {
    final override val group
        get() = "net.neoforged"

    final override val artifact
        get() = "neoforge"

    final override val loaderAttributeName get() = NEOFORGE

    override val remapNamespace: Provider<String>
        get() = hasMappings.flatMap {
            if (it) {
                super<ForgeTargetImpl>.remapNamespace
            } else {
                providerFactory.provider { "" }
            }
        }

    final override fun version(minecraftVersion: String, loaderVersion: String) =
        loaderVersion
}
