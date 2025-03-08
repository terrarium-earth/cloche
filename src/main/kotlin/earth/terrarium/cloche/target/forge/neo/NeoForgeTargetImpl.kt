package earth.terrarium.cloche.target.forge.neo

import earth.terrarium.cloche.NEOFORGE
import earth.terrarium.cloche.api.target.NeoforgeTarget
import earth.terrarium.cloche.target.CompilationInternal
import earth.terrarium.cloche.target.forge.ForgeLikeTargetImpl
import earth.terrarium.cloche.target.modConfigurationName
import net.msrandom.minecraftcodev.core.operatingSystemName
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.forge.task.GenerateLegacyClasspath
import net.msrandom.minecraftcodev.forge.task.ResolvePatchedMinecraft
import net.msrandom.minecraftcodev.mixins.mixinsConfigurationName
import org.gradle.api.artifacts.result.ResolvedComponentResult
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

    override val remapNamespace: Provider<String>
        get() = hasMappings.flatMap {
            if (it) {
                super<ForgeLikeTargetImpl>.remapNamespace
            } else {
                providerFactory.provider { "" }
            }
        }

    override val generateLegacyClasspath = project.tasks.register(
        lowerCamelCaseGradleName("generate", featureName, "legacyClasspath"),
        GenerateLegacyClasspath::class.java,
    ) { task ->
        configureLegacyClasspath(task, sourceSet)
    }

    override val generateLegacyDataClasspath = project.tasks.register(
        lowerCamelCaseGradleName("generate", featureName, "dataLegacyClasspath"),
        GenerateLegacyClasspath::class.java,
    ) { task ->
        data.onConfigured { data ->
            configureLegacyClasspath(task, data.sourceSet)
        }
    }

    override val generateLegacyTestClasspath = project.tasks.register(
        lowerCamelCaseGradleName("generate", featureName, "testLegacyClasspath"),
        GenerateLegacyClasspath::class.java,
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

    private fun configureLegacyClasspath(task: GenerateLegacyClasspath, sourceSet: SourceSet) {
        val runtimeClasspath = project.configurations.named(sourceSet.runtimeClasspathConfigurationName)

        val classpath = project.files()

        classpath.from(minecraftLibrariesConfiguration)
        classpath.from(resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::clientExtra))
        classpath.from(runtimeClasspath)

        val modDependencies = project.configurations.named(modConfigurationName(sourceSet.runtimeClasspathConfigurationName))

        val modFiles = runtimeClasspath.zip(modDependencies, ::Pair).map { (classpath, modDependencies) ->
            val componentIdentifiers = modDependencies.incoming.resolutionResult.allComponents.map(ResolvedComponentResult::getId) - modDependencies.incoming.resolutionResult.root.id

            classpath.incoming.artifactView {
                // Use an artifact view with a component filter to preserve artifact transforms, no variant reselection to ensure consistency
                it.componentFilter(componentIdentifiers::contains)
            }.files
        }

        task.classpath.from(classpath - project.files(modFiles))
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
