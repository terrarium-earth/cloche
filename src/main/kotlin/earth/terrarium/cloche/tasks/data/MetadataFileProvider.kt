package earth.terrarium.cloche.tasks.data

import groovy.util.Node

/**
 * Provides various ways to access the content of a metadata file.
 */
interface MetadataFileProvider<ElementT> {
    /**
     * Returns the metadata file as a [StringBuilder]. Changes to the returned instance will be applied to the file.
     * The returned instance is only valid until one of the other methods on this interface is called.
     *
     * @return A `[StringBuilder]` representation of the metadata file.
     */
    fun asString(): StringBuilder

    /**
     * Returns the metadata file as a Groovy [Node]. Changes to the returned instance will be applied
     * to the metadata file. The returned instance is only valid until one of the other methods on this interface is called.
     *
     * @return A `[Node]` representation of the metadata file.
     */
    fun asNode(): Node

    /**
     * Returns the metadata file as an immutable [ElementT]. The returned instance is only valid until one of the other methods on this interface is called.
     *
     * @return An `[ElementT]` representation of the metadata file.
     */
    fun asElement(): ElementT

    /**
     * Applies a modified [ElementT] to the metadata file.
     */
    fun applyElement(element: ElementT)
}
