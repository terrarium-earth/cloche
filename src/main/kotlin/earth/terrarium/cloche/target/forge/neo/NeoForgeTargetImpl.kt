package earth.terrarium.cloche.target.forge.neo

import earth.terrarium.cloche.NEOFORGE
import earth.terrarium.cloche.api.target.NeoforgeTarget
import earth.terrarium.cloche.target.CompilationInternal
import earth.terrarium.cloche.target.forge.ForgeLikeTargetImpl
import earth.terrarium.cloche.target.getModFiles
import net.msrandom.minecraftcodev.core.operatingSystemName
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.forge.MinecraftCodevForgePlugin
import net.msrandom.minecraftcodev.forge.task.ResolvePatchedMinecraft
import net.msrandom.minecraftcodev.mixins.mixinsConfigurationName
import net.msrandom.minecraftcodev.runs.task.WriteClasspathFile
import org.gradle.api.attributes.Attribute
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import javax.inject.Inject

private val NEOFORGE_DISTRIBUTION_ATTRIBUTE = Attribute.of("net.neoforged.distribution", String::class.java)
private val NEOFORGE_OPERATING_SYSTEM_ATTRIBUTE = Attribute.of("net.neoforged.operatingsystem", String::class.java)

internal abstract class NeoForgeTargetImpl @Inject constructor(name: String) : ForgeLikeTargetImpl(name), NeoforgeTarget {
    final override val group
        get() = "net.neoforged"

    final override val artifact
        get() = "neoforge"

    final override val loaderName get() = NEOFORGE

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

    override val writeLegacyClasspath = project.tasks.register(
        lowerCamelCaseGradleName("write", featureName, "legacyClasspath"),
        WriteClasspathFile::class.java,
    ) { task ->
        configureLegacyClasspath(task, sourceSet)
    }

    override val writeLegacyDataClasspath = project.tasks.register(
        lowerCamelCaseGradleName("write", featureName, "dataLegacyClasspath"),
        WriteClasspathFile::class.java,
    ) { task ->
        data.onConfigured { data ->
            configureLegacyClasspath(task, data.sourceSet)
        }
    }

    override val writeLegacyTestClasspath = project.tasks.register(
        lowerCamelCaseGradleName("write", featureName, "testLegacyClasspath"),
        WriteClasspathFile::class.java,
    ) { task ->
        test.onConfigured { test ->
            configureLegacyClasspath(task, test.sourceSet)
        }
    }

    init {
        minecraftLibrariesConfiguration.attributes {
            it.attribute(NEOFORGE_DISTRIBUTION_ATTRIBUTE, "client")

            it.attribute(
                NEOFORGE_OPERATING_SYSTEM_ATTRIBUTE,
                operatingSystemName(),
            )
        }

        generateModsToml.configure {
            it.loaderDependencyVersion.set(metadata.loaderVersion.orElse(loaderVersionRange("1")))

            it.output.set(metadataDirectory.map {
                it.dir("META-INF").file("neoforge.mods.toml")
            })

            it.mixinConfigs.from(project.configurations.named(sourceSet.mixinsConfigurationName))
        }
    }

    private fun configureLegacyClasspath(task: WriteClasspathFile, sourceSet: SourceSet) {
        val classpath = project.files()

        classpath.from(minecraftLibrariesConfiguration)
        classpath.from(resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::clientExtra))
        classpath.from(project.configurations.named(sourceSet.runtimeClasspathConfigurationName))

        task.classpath.from(classpath - project.getModFiles(sourceSet.runtimeClasspathConfigurationName, isTransitive = false))
    }

    private fun addAttributes(sourceSet: SourceSet) {
        project.configurations.named(sourceSet.compileClasspathConfigurationName) {
            it.attributes.attribute(NEOFORGE_DISTRIBUTION_ATTRIBUTE, "client")
            it.attributes.attribute(
                NEOFORGE_OPERATING_SYSTEM_ATTRIBUTE,
                operatingSystemName(),
            )
        }

        project.configurations.named(sourceSet.runtimeClasspathConfigurationName) {
            it.attributes.attribute(NEOFORGE_DISTRIBUTION_ATTRIBUTE, "client")
            it.attributes
                .attribute(
                    NEOFORGE_OPERATING_SYSTEM_ATTRIBUTE,
                    operatingSystemName(),
                )
        }
    }

    override fun initialize(isSingleTarget: Boolean) {
        super.initialize(isSingleTarget)

        addAttributes(sourceSet)

        data.onConfigured {
            addAttributes(it.sourceSet)
        }

        test.onConfigured {
            addAttributes(it.sourceSet)
        }
    }

    final override fun version(minecraftVersion: String, loaderVersion: String) =
        loaderVersion

    override fun addJarInjects(compilation: CompilationInternal) {}
}
