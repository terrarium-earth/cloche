package earth.terrarium.cloche.target

abstract class QuiltTarget(name: String) : FabricTarget(name) {
    final override val loaderAttributeName get() = "quilt"
}
