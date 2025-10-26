package earth.terrarium.cloche.target.forge.neo

import earth.terrarium.cloche.api.target.NeoforgeTarget
import earth.terrarium.cloche.modId
import earth.terrarium.cloche.target.CompilationInternal
import earth.terrarium.cloche.target.forge.ForgeLikeTargetImpl
import earth.terrarium.cloche.tasks.data.FabricMod
import earth.terrarium.cloche.tasks.data.NeoForgeMods
import earth.terrarium.cloche.tasks.data.decodeFromStream
import earth.terrarium.cloche.tasks.data.encodeToStream
import earth.terrarium.cloche.tasks.data.toml
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import net.msrandom.minecraftcodev.core.MinecraftCodevPlugin.Companion.json
import net.msrandom.minecraftcodev.core.operatingSystemName
import net.msrandom.minecraftcodev.core.utils.zipFileSystem
import net.msrandom.minecraftcodev.fabric.MinecraftCodevFabricPlugin
import net.msrandom.minecraftcodev.forge.MinecraftCodevForgePlugin
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.named
import java.util.jar.JarFile
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

                    val metadata: NeoForgeMods = tomlPath.inputStream().use(toml::decodeFromStream)

                    if (metadata.accessTransformers.any { it.file == accessTransformerPathName }) {
                        return@use
                    }

                    tomlPath.outputStream().use {
                        toml.encodeToStream(
                            metadata.copy(
                                accessTransformers = metadata.accessTransformers + NeoForgeMods.AccessTransformer(
                                    accessTransformerPathName
                                )
                            ), it
                        )
                    }
                }
            }
        }
    }
}
