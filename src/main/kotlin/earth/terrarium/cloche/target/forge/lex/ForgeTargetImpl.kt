package earth.terrarium.cloche.target.forge.lex

import earth.terrarium.cloche.NO_NAME_MAPPING_ATTRIBUTE
import earth.terrarium.cloche.api.target.ForgeTarget
import earth.terrarium.cloche.api.target.compilation.Compilation
import earth.terrarium.cloche.target.CompilationInternal
import earth.terrarium.cloche.target.forge.ForgeLikeTargetImpl
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.core.utils.zipFileSystem
import net.msrandom.minecraftcodev.forge.MinecraftCodevForgePlugin
import net.msrandom.minecraftcodev.forge.task.GenerateMcpToSrg
import net.msrandom.minecraftcodev.remapper.task.LoadMappings
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.register
import java.io.File
import javax.inject.Inject
import kotlin.io.path.exists

internal abstract class ForgeTargetImpl @Inject constructor(name: String) : ForgeLikeTargetImpl(name), ForgeTarget {
    override val runs = objectFactory.newInstance<LexForgeRunConfigurations>(this)

    override val group
        @Internal
        get() = "net.minecraftforge"

    override val artifact
        @Internal
        get() = "forge"

    override val minecraftRemapNamespace: Provider<String>
        get() = providerFactory.provider { MinecraftCodevForgePlugin.SRG_MAPPINGS_NAMESPACE }

    val generateMcpToSrg = project.tasks.register<GenerateMcpToSrg>(
        lowerCamelCaseGradleName("generate", featureName, "mcpToSrg"),
    ) {
        mappings.set(loadMappingsTask.flatMap(LoadMappings::output))
    }

    init {
        project.dependencies.add(minecraftLibrariesConfiguration.name, "net.msrandom:codev-forge-runtime:0.1.1")

        removeNameMappingService(main)

        data.onConfigured(::removeNameMappingService)
        test.onConfigured(::removeNameMappingService)

        minecraftLibrariesConfiguration.attributes.attribute(NO_NAME_MAPPING_ATTRIBUTE, true)
    }

    private fun removeNameMappingService(compilation: Compilation) {
        project.configurations.named(compilation.sourceSet.compileClasspathConfigurationName) {
            attributes.attribute(NO_NAME_MAPPING_ATTRIBUTE, true)
        }

        project.configurations.named(compilation.sourceSet.runtimeClasspathConfigurationName) {
            attributes.attribute(NO_NAME_MAPPING_ATTRIBUTE, true)
        }
    }

    override fun version(minecraftVersion: String, loaderVersion: String) =
        "$minecraftVersion-$loaderVersion"

    override fun addJarInjects(compilation: CompilationInternal) {
        project.tasks.named<Jar>(compilation.sourceSet.jarTaskName) {
            manifest {
                attributes["MixinConfigs"] = object {
                    override fun toString(): String {
                        return compilation.mixins.joinToString(",", transform = File::getName)
                    }
                }
            }

            // TODO This is fundamentally broken as we are trying to access the not-yet-generated Jar to check if it contains a specific file,
            //  to change the manifest used to generate said Jar
/*            doFirst {
                this as Jar

                val accessTransformerName = "accesstransformer.cfg"

                zipFileSystem(archiveFile.get().asFile.toPath()).use {
                    if (it.getPath("META-INF", accessTransformerName).exists()) {
                        manifest.attributes["FMLAT"] = accessTransformerName
                    }
                }
            }*/
        }
    }
}
