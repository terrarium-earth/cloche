package earth.terrarium.cloche.target

import earth.terrarium.cloche.ClocheExtension

internal abstract class NeoForgeTarget(name: String) : ForgeTarget(name) {
    final override val group
        get() = "net.neoforged"

    final override val artifact
        get() = "neoforge"

    final override val loaderAttributeName get() = ClocheExtension::neoforge.name

    final override fun version(minecraftVersion: String, loaderVersion: String) =
        loaderVersion
}
