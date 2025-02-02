package earth.terrarium.cloche.target

import earth.terrarium.cloche.NEOFORGE
import net.msrandom.minecraftcodev.mixins.mixinsConfigurationName
import org.gradle.api.attributes.Attribute
import org.gradle.api.provider.Provider
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import javax.inject.Inject

private val NEOFORGE_DISTRIBUTION_ATTRIBUTE = Attribute.of("net.neoforged.distribution", String::class.java)
private val NEOFORGE_OPERATING_SYSTEM_ATTRIBUTE = Attribute.of("net.neoforged.operatingsystem", String::class.java)

internal abstract class NeoForgeTargetImpl @Inject constructor(name: String): ForgeTargetImpl(name), NeoforgeTarget {
    final override val group
        get() = "net.neoforged"

    final override val artifact
        get() = "neoforge"

    final override val loaderName get() = NEOFORGE

    override val remapNamespace: Provider<String>
        get() = hasMappings.flatMap {
            if (it) {
                super<ForgeTargetImpl>.remapNamespace
            } else {
                providerFactory.provider { "" }
            }
        }

    init {
        minecraftLibrariesConfiguration.attributes {
            it.attribute(NEOFORGE_DISTRIBUTION_ATTRIBUTE, "client")
            it.attribute(NEOFORGE_OPERATING_SYSTEM_ATTRIBUTE, DefaultNativePlatform.host().operatingSystem.toFamilyName())
        }

        generateModsToml.configure {
            it.loaderDependencyVersion.set("1")

            it.output.set(metadataDirectory.map {
                it.file("neoforge.mods.toml")
            })

            it.mixinConfigs.from(project.configurations.named(main.sourceSet.mixinsConfigurationName))
        }
    }

    override fun initialize(isSingleTarget: Boolean) {
        super.initialize(isSingleTarget)

        main.dependencies {
            project.configurations.named(it.sourceSet.compileClasspathConfigurationName) {
                it.attributes.attribute(NEOFORGE_DISTRIBUTION_ATTRIBUTE, "client")
                it.attributes.attribute(NEOFORGE_OPERATING_SYSTEM_ATTRIBUTE, DefaultNativePlatform.host().operatingSystem.toFamilyName())
            }

            project.configurations.named(it.sourceSet.runtimeClasspathConfigurationName) {
                it.attributes.attribute(NEOFORGE_DISTRIBUTION_ATTRIBUTE, "client")
                it.attributes
                    .attribute(NEOFORGE_OPERATING_SYSTEM_ATTRIBUTE, DefaultNativePlatform.host().operatingSystem.toFamilyName())
            }
        }
    }

    override fun createData(): RunnableTargetCompilation {
        return super.createData().apply {
            dependencies {
                project.configurations.named(it.sourceSet.compileClasspathConfigurationName) {
                    it.attributes.attribute(NEOFORGE_DISTRIBUTION_ATTRIBUTE, "client")
                    it.attributes.attribute(NEOFORGE_OPERATING_SYSTEM_ATTRIBUTE, DefaultNativePlatform.host().operatingSystem.toFamilyName())
                }

                project.configurations.named(it.sourceSet.runtimeClasspathConfigurationName) {
                    it.attributes.attribute(NEOFORGE_DISTRIBUTION_ATTRIBUTE, "client")
                    it.attributes
                        .attribute(NEOFORGE_OPERATING_SYSTEM_ATTRIBUTE, DefaultNativePlatform.host().operatingSystem.toFamilyName())
                }
            }
        }
    }

    final override fun version(minecraftVersion: String, loaderVersion: String) =
        loaderVersion

    override fun addJarInjects(compilation: CompilationInternal) {}
}
