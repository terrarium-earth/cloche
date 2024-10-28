package earth.terrarium.cloche.target

import earth.terrarium.cloche.MINECRAFT_VERSION_ATTRIBUTE
import earth.terrarium.cloche.MOD_LOADER_ATTRIBUTE
import earth.terrarium.cloche.TARGET_MINECRAFT_ATTRIBUTE
import earth.terrarium.cloche.asDependency
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import org.gradle.api.Action
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider

internal class MinecraftConfiguration(
    target: MinecraftTargetInternal,
    val targetMinecraftAttribute: String,
    val artifact: Provider<RegularFile>,
    targetName: String,
    name: String? = null,
) {
    private val capabilityName = if (name == null) {
        "net.minecraft:$targetName"
    } else {
        "net.minecraft:$targetName-$name"
    }

    private val consumableConfiguration = target.project.configurations.create(lowerCamelCaseGradleName(targetName, "minecraft", name)) { configuration ->
        configuration.isCanBeResolved = false

        configuration.outgoing {
            it.capability("$capabilityName:0.0.0")
        }

        configuration.attributes { attributes ->
            attributes
                .attribute(Category.CATEGORY_ATTRIBUTE, target.project.objects.named(Category::class.java, Category.LIBRARY))
                .attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, target.project.objects.named(LibraryElements::class.java, LibraryElements.JAR))
                .attribute(Bundling.BUNDLING_ATTRIBUTE, target.project.objects.named(Bundling::class.java, Bundling.EXTERNAL))
                .attribute(TARGET_MINECRAFT_ATTRIBUTE, targetMinecraftAttribute)
                .attribute(MOD_LOADER_ATTRIBUTE, target.loaderAttributeName)
                .attributeProvider(MINECRAFT_VERSION_ATTRIBUTE, target.minecraftVersion)
        }
    }

    private val dependencyHolder = target.project.configurations.create(lowerCamelCaseGradleName(targetName, name, "minecraftDependencies")) { configuration ->
        configuration.isCanBeResolved = false
        configuration.isCanBeConsumed = false
    }

    val dependency = target.project.asDependency().apply {
        capabilities {
            it.requireCapability(capabilityName)
        }

        attributes {
            it
                .attribute(TARGET_MINECRAFT_ATTRIBUTE, targetMinecraftAttribute)
                .attribute(MOD_LOADER_ATTRIBUTE, target.loaderAttributeName)
                .attributeProvider(MINECRAFT_VERSION_ATTRIBUTE, target.minecraftVersion)
        }
    }

    val configurationName: String
        get() = consumableConfiguration.name

    init {
        target.project.dependencies.add(dependencyHolder.name, dependency)
        target.project.artifacts.add(consumableConfiguration.name, artifact)
    }

    fun useIn(configuration: Configuration) {
        configuration.extendsFrom(dependencyHolder)
    }

    internal fun attributes(action: Action<AttributeContainer>) {
        consumableConfiguration.attributes(action)
    }
}
