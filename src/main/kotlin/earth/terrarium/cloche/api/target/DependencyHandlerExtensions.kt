package earth.terrarium.cloche.api.target

import net.msrandom.minecraftcodev.core.MinecraftOperatingSystemAttribute
import net.msrandom.minecraftcodev.core.operatingSystemName
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.forge.task.ResolvePatchedMinecraft
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.attributes.Usage
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

fun Project.patchedMinecraft(
    group: String,
    artifact: String,
    minecraftVersion: String,
    loaderVersion: String,
    neoforge: Boolean = false,
): FileCollection = patchedMinecraft(group, artifact, provider { minecraftVersion }, provider { loaderVersion }, neoforge)

fun Project.patchedMinecraft(
    group: String,
    artifact: String,
    minecraftVersion: Provider<String>,
    loaderVersion: Provider<String>,
    neoforge: Boolean = false,
): FileCollection {
    val taskName = nextPatchedMinecraftTaskName(artifact)
    val minecraftLibraries = createPatchedMinecraftConfiguration(
        lowerCamelCaseGradleName(taskName, "minecraftLibraries"),
        operatingSystemAware = true,
    )
    val universal = createPatchedMinecraftConfiguration(lowerCamelCaseGradleName(taskName, "universal"))
    val patches = createPatchedMinecraftConfiguration(lowerCamelCaseGradleName(taskName, "patches"))

    dependencies.addProvider(minecraftLibraries.name, loaderDependency(group, artifact, loaderVersion) {
        capabilities {
            requireFeature("dependencies")
        }
    })
    dependencies.addProvider(universal.name, loaderDependency(group, artifact, loaderVersion) {})
    dependencies.addProvider(patches.name, loaderDependency(group, artifact, loaderVersion) {
        capabilities {
            requireFeature("moddev-bundle")
        }
    })

    val taskProvider = tasks.register<ResolvePatchedMinecraft>(taskName) {
        this.group = "minecraft-resolution"

        this.minecraftVersion.set(minecraftVersion)
        this.universal.from(universal)
        this.patches.from(patches)
        this.libraries.from(minecraftLibraries)
        this.output.set(patchedMinecraftOutput(taskName, loaderVersion))
        this.neoforge.set(neoforge)
    }

    return files(taskProvider.flatMap(ResolvePatchedMinecraft::output)).builtBy(taskProvider)
}

fun Project.forgeMinecraft(minecraftVersion: String, loaderVersion: String): FileCollection =
    forgeMinecraft(provider { minecraftVersion }, provider { loaderVersion })

fun Project.forgeMinecraft(minecraftVersion: Provider<String>, loaderVersion: Provider<String>): FileCollection =
    patchedMinecraft(
        "net.minecraftforge",
        "forge",
        minecraftVersion,
        minecraftVersion.zip(loaderVersion) { minecraftVersion, loaderVersion -> "$minecraftVersion-$loaderVersion" },
    )

fun Project.neoForgeMinecraft(minecraftVersion: String, loaderVersion: String): FileCollection =
    neoForgeMinecraft(provider { minecraftVersion }, provider { loaderVersion })

fun Project.neoForgeMinecraft(minecraftVersion: Provider<String>, loaderVersion: Provider<String>): FileCollection =
    patchedMinecraft("net.neoforged", "neoforge", minecraftVersion, loaderVersion, neoforge = true)

private fun Project.nextPatchedMinecraftTaskName(artifact: String): String {
    var index = 1

    while (true) {
        val taskName = lowerCamelCaseGradleName("resolve", artifact, "patchedMinecraft", index.toString())

        if (taskName !in tasks.names) {
            return taskName
        }

        index++
    }
}

private fun Project.createPatchedMinecraftConfiguration(
    name: String,
    operatingSystemAware: Boolean = false,
): Configuration = configurations.create(name) {
    isCanBeConsumed = false

    if (operatingSystemAware) {
        attributes.attribute(
            MinecraftOperatingSystemAttribute.attribute,
            objects.named(operatingSystemName()),
        )
        attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
    }
}

private fun Project.loaderDependency(
    group: String,
    artifact: String,
    version: Provider<String>,
    configure: ExternalModuleDependency.() -> Unit,
): Provider<ExternalModuleDependency> = version.map { artifactVersion ->
    (dependencies.create("$group:$artifact:$artifactVersion") as ExternalModuleDependency).apply(configure)
}

private fun Project.patchedMinecraftOutput(taskName: String, loaderVersion: Provider<String>) =
    layout.buildDirectory.dir("minecraft-resolution").flatMap { directory ->
        loaderVersion.map { directory.file("$taskName-$it.jar") }
    }
