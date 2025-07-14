package earth.terrarium.cloche.util

import earth.terrarium.cloche.api.metadata.FabricMetadata
import earth.terrarium.cloche.api.metadata.ForgeMetadata
import earth.terrarium.cloche.api.metadata.Metadata
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.model.ObjectFactory

@Suppress("DuplicatedCode")
inline fun <reified MetadataType : Metadata> mergeMetadata(objectFactory: ObjectFactory, metadata: List<Metadata>): MetadataType {
    val mergedMetadata = objectFactory.newInstance(MetadataType::class.java)
    mergedMetadata.license.set("ARR")
    mergedMetadata.environment.set(Metadata.Environment.BOTH)
    when (mergedMetadata) {
        is ForgeMetadata -> {
            mergedMetadata.modLoader.set("javafml")
        }
    }

    for (metadata in metadata) {
        if (metadata.modId.isPresent) {
            mergedMetadata.modId.set(metadata.modId.get())
        }
        if (metadata.name.isPresent) {
            mergedMetadata.name.set(metadata.name)
        }
        if (metadata.description.isPresent) {
            mergedMetadata.description.set(metadata.description.get())
        }
        if (metadata.license.isPresent) {
            mergedMetadata.license.set(metadata.license.get())
        }
        if (metadata.icon.isPresent) {
            mergedMetadata.icon.set(metadata.icon.get())
        }
        if (metadata.url.isPresent) {
            mergedMetadata.url.set(metadata.url.get())
        }
        if (metadata.issues.isPresent) {
            mergedMetadata.issues.set(metadata.issues.get())
        }
        if (metadata.sources.isPresent) {
            mergedMetadata.sources.set(metadata.sources.get())
        }
        if (metadata.environment.isPresent) {
            val mergedEnvironment = mergedMetadata.environment.get()
            val environment = metadata.environment.get()
            if (mergedEnvironment != Metadata.Environment.BOTH && mergedEnvironment != environment) {
                mergedMetadata.environment.set(Metadata.Environment.BOTH)
            } else {
                mergedMetadata.environment.set(environment)
            }
        }
        mergedMetadata.authors.set(mergedMetadata.authors.get() + metadata.authors.get())
        mergedMetadata.contributors.set(mergedMetadata.contributors.get() + metadata.contributors.get())
        mergedMetadata.dependencies.set(mergedMetadata.dependencies.get() + metadata.dependencies.get())
        val mergedCustom = mergedMetadata.custom.get().toMutableMap()
        metadata.custom.get().forEach { (key, value) ->
            mergedCustom.merge(key, value) { oldValue, newValue -> oldValue + newValue }
        }
        mergedMetadata.custom.set(mergedCustom)

        when (metadata) {
            is FabricMetadata -> {
                val mergedMetadata = (mergedMetadata as FabricMetadata)
                val mergedEntrypoints = mergedMetadata.entrypoints.get().toMutableMap()
                metadata.entrypoints.get().forEach { (key, value) ->
                    mergedEntrypoints.merge(key, value) { oldValue, newValue -> oldValue + newValue }
                }
                mergedMetadata.entrypoints.set(mergedEntrypoints)
                mergedMetadata.languageAdapters.putAll(metadata.languageAdapters.get())
            }

            is ForgeMetadata -> {
                val mergedMetadata = (mergedMetadata as ForgeMetadata)
                if (metadata.modLoader.isPresent) {
                    mergedMetadata.modLoader.set(metadata.modLoader.get())
                }
                if (metadata.loaderVersion.isPresent) {
                    mergedMetadata.loaderVersion.set(metadata.loaderVersion.get())
                }
                if (metadata.showAsResourcePack.isPresent) {
                    mergedMetadata.showAsResourcePack.set(metadata.showAsResourcePack.get())
                }
                if (metadata.showAsDataPack.isPresent) {
                    mergedMetadata.showAsDataPack.set(metadata.showAsDataPack.get())
                }
                mergedMetadata.services.addAll(metadata.services.get())
                if (metadata.blurLogo.isPresent) {
                    mergedMetadata.blurLogo.set(metadata.blurLogo.get())
                }
                val mergedModProperties = mergedMetadata.modProperties.get().toMutableMap()
                metadata.modProperties.get().forEach { (key, value) ->
                    mergedModProperties.merge(key, value) { oldValue, newValue -> oldValue + newValue }
                }
                mergedMetadata.modProperties.set(mergedModProperties)
            }
        }
    }

    return mergedMetadata
}

fun validateMetadata(metadata: Metadata) {
    if (!metadata.modId.isPresent) {
        throw InvalidUserCodeException("Empty mod identifier specified for a target.")
    }
}
