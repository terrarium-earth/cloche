package earth.terrarium.cloche.target

abstract class QuiltTarget(name: String) : FabricTarget(name) {
    override val loaderAttributeName get() = "quilt"
}
