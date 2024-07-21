package earth.terrarium.cloche.target

abstract class NeoForgeTarget(name: String) : ForgeTarget(name) {
    final override val group
        get() = "net.neoforged"

    final override val artifact
        get() = "neoforge"

    final override val loaderAttributeName get() = "neoforge"

    final override fun version(minecraftVersion: String, loaderVersion: String) =
        loaderVersion
}
