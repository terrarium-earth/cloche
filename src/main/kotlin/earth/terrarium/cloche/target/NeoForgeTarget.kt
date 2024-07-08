package earth.terrarium.cloche.target

abstract class NeoForgeTarget(name: String) : ForgeTarget(name) {
    override val group
        get() = "net.neoforged"

    override val artifact
        get() = "neoforge"

    override val loaderAttributeName get() = "neoforge"

    override fun version(minecraftVersion: String, loaderVersion: String) =
        loaderVersion
}
