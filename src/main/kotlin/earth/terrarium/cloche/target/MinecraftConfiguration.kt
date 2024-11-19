package earth.terrarium.cloche.target

import earth.terrarium.cloche.MinecraftAttributes
import earth.terrarium.cloche.TargetAttributes
import earth.terrarium.cloche.asDependency
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.java.TargetJvmEnvironment
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider

internal const val MINECRAFT_ARTIFACT_TYPE = "minecraft"

internal class MinecraftConfiguration(
    target: MinecraftTargetInternal,
    val targetMinecraftAttribute: String,
    val artifact: Provider<RegularFile>,
    targetName: String,
    name: String? = null,
) {
    val capabilityName = if (name == null) {
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
                .attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, target.project.objects.named(TargetJvmEnvironment::class.java, TargetJvmEnvironment.STANDARD_JVM))
                .attribute(TargetAttributes.MOD_LOADER, target.loaderAttributeName)
                .attributeProvider(TargetAttributes.MINECRAFT_VERSION, target.minecraftVersion)
                .attribute(MinecraftAttributes.TARGET_MINECRAFT, targetMinecraftAttribute)
        }
    }

    private val dependencyHolder = target.project.configurations.create(lowerCamelCaseGradleName(targetName, name, "minecraftDependencies")) { configuration ->
        configuration.isCanBeResolved = false
        configuration.isCanBeConsumed = false
    }

    val dependency = target.project.asDependency {
        because("Dependency on Minecraft($targetMinecraftAttribute)")

        capabilities { capabilities ->
            capabilities.requireCapability(capabilityName)
        }

        attributes { attributes ->
            attributes
                .attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, target.project.objects.named(LibraryElements::class.java, LibraryElements.JAR))
                .attribute(TargetAttributes.MOD_LOADER, target.loaderAttributeName)
                .attributeProvider(TargetAttributes.MINECRAFT_VERSION, target.minecraftVersion)
                .attribute(MinecraftAttributes.TARGET_MINECRAFT, targetMinecraftAttribute)
        }
    }

    val configurationName: String
        get() = consumableConfiguration.name

    init {
        target.project.dependencies.add(dependencyHolder.name, dependency)

        target.project.artifacts.add(consumableConfiguration.name, artifact) {
            it.extension = MINECRAFT_ARTIFACT_TYPE
            it.type = MINECRAFT_ARTIFACT_TYPE
        }
    }

    fun useIn(configuration: Configuration) {
        configuration.extendsFrom(dependencyHolder)
    }
}
