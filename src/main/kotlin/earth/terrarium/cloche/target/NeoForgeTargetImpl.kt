package earth.terrarium.cloche.target

import earth.terrarium.cloche.ClocheExtension

internal abstract class NeoForgeTargetImpl(name: String) : ForgeTargetImpl(name) {
    final override val group
        get() = "net.neoforged"

    final override val artifact
        get() = "neoforge"

    final override val loaderAttributeName get() = ClocheExtension::neoforge.name

    override val remapNamespace: String?
        get() = if (hasMappings) {
            super.remapNamespace
        } else {
            null
        }

    final override fun version(minecraftVersion: String, loaderVersion: String) =
        loaderVersion
}
