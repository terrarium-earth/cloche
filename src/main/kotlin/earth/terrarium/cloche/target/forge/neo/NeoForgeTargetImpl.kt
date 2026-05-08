package earth.terrarium.cloche.target.forge.neo

import earth.terrarium.cloche.api.target.NeoforgeTarget
import earth.terrarium.cloche.target.compilation.CompilationInternal
import earth.terrarium.cloche.target.forge.ForgeLikeTargetImpl
import earth.terrarium.cloche.target.compilation.localImplementationConfigurationName
import earth.terrarium.cloche.tasks.data.decodeFromStream
import earth.terrarium.cloche.tasks.data.encodeToStream
import earth.terrarium.cloche.tasks.data.toml
import net.msrandom.minecraftcodev.core.operatingSystemName
import net.msrandom.minecraftcodev.core.utils.isUnobfuscatedVersion
import net.msrandom.minecraftcodev.core.utils.zipFileSystem
import net.msrandom.minecraftcodev.forge.MinecraftCodevForgePlugin
import net.peanuuutz.tomlkt.*
import org.gradle.api.artifacts.Dependency
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.named
import javax.inject.Inject
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

private val NEOFORGE_DISTRIBUTION_ATTRIBUTE = Attribute.of("net.neoforged.distribution", String::class.java)
private val NEOFORGE_OPERATING_SYSTEM_ATTRIBUTE = Attribute.of("net.neoforged.operatingsystem", String::class.java)

internal abstract class NeoForgeTargetImpl @Inject constructor(name: String) : ForgeLikeTargetImpl(name),
    NeoforgeTarget {
    final override val group
        get() = "net.neoforged"

    final override val artifact
        get() = "neoforge"

    override val minecraftRemapNamespace: Provider<String>
        get() = mappings.isDefault.map {
            if (it) {
                ""
            } else {
                MinecraftCodevForgePlugin.SRG_MAPPINGS_NAMESPACE
            }
        }

    override val modRemapNamespace: Provider<String>
        get() = mappings.isOfficialCompatible.map {
            if (it) {
                ""
            } else {
                MinecraftCodevForgePlugin.SRG_MAPPINGS_NAMESPACE
            }
        }

    init {
        minecraftLibrariesConfiguration.attributes {
            attribute(NEOFORGE_DISTRIBUTION_ATTRIBUTE, "client")

            attribute(
                NEOFORGE_OPERATING_SYSTEM_ATTRIBUTE,
                operatingSystemName(),
            )
        }

        resolvePatchedMinecraft.configure {
            neoforge.set(true)
        }

        resolvableAttributes(::addAttributes)

        data.onConfigured {
            it.resolvableAttributes(::addAttributes)
        }

        val emptyList = project.provider { emptyList<Dependency>() }

        project.configurations.named(sourceSet.localImplementationConfigurationName) {
            dependencies.addAllLater(minecraftVersion.flatMap {
                if (isUnobfuscatedVersion(it)) {
                    (forgeDependency {}).map(::listOf)
                } else {
                    emptyList
                }
            })
        }
    }

    private fun addAttributes(attributeContainer: AttributeContainer) {
        attributeContainer.attribute(NEOFORGE_DISTRIBUTION_ATTRIBUTE, "client")

        attributeContainer.attribute(
            NEOFORGE_OPERATING_SYSTEM_ATTRIBUTE,
            operatingSystemName(),
        )
    }

    final override fun version(minecraftVersion: String, loaderVersion: String) =
        loaderVersion

    override fun registerAccessWidenerMergeTask(compilation: CompilationInternal) {
        super.registerAccessWidenerMergeTask(compilation)

        if (compilation.isTest) {
            return
        }

        project.tasks.named<Jar>(compilation.sourceSet.jarTaskName) {
            doLast {
                this as Jar

                zipFileSystem(archiveFile.get().asFile.toPath()).use {
                    val accessTransformerPathName = "META-INF/accesstransformer.cfg"
                    val accessTransformerPath = it.getPath(accessTransformerPathName)
                    val tomlPath = it.getPath("META-INF", "neoforge.mods.toml")

                    if (!accessTransformerPath.exists() || !tomlPath.exists()) {
                        return@use
                    }

                    val metadata: TomlTable = tomlPath.inputStream().use(toml::decodeFromStream)

                    val accessTransformers = metadata["accessTransformers"]?.asTomlArray() ?: TomlArray()

                    if (accessTransformers.any { it.asTomlTable()["file"]?.content?.toString() == accessTransformerPathName }) {
                        return@use
                    }

                    val newMetadata = buildTomlTable {
                        for ((key, value) in metadata) {
                            if (key != "accessTransformers") {
                                element(key, value)
                            } else {
                                element(key, buildTomlArray {
                                    for (accessTransformer in accessTransformers) {
                                        element(accessTransformer)
                                    }
                                })
                            }
                        }
                    }

                    tomlPath.outputStream().use {
                        toml.encodeToStream(newMetadata, it)
                    }
                }
            }
        }
    }
}
