/*
package earth.terrarium.cloche

import earth.terrarium.cloche.extend
import earth.terrarium.cloche.target.CommonTargetInternal
import earth.terrarium.cloche.target.MinecraftTargetInternal
import net.msrandom.minecraftcodev.accesswidener.accessWidenersConfigurationName
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.mixins.mixinsConfigurationName
import net.msrandom.virtualsourcesets.MultiplatformStructure
import net.msrandom.virtualsourcesets.SourceSetDependencyInfo
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet

private fun setDependenciesWithData(common: CommonTargetInternal, dependencies: SourceSetDependencyInfo) {
    common.dependsOn.all { dependency ->
        val data = (dependency as CommonTargetInternal).withData()

        dependencies.dependsOn(data.sourceSet) {
            it.dependsOn(dependency.main.sourceSet)

            setDependenciesWithData(dependency, it)
        }
    }
}

private fun setDependenciesWithClient(common: CommonTargetInternal, dependencies: SourceSetDependencyInfo) {
    common.dependsOn.all { dependency ->
        val client = (dependency as CommonTargetInternal).withClient()

        dependencies.dependsOn(client.sourceSet) {
            it.dependsOn(dependency.main.sourceSet)

            setDependenciesWithClient(dependency, it)
        }
    }
}

private fun addMainDependencies(
    common: CommonTargetInternal,
    objectFactory: ObjectFactory,
    dependencies: SourceSetDependencyInfo
) {
    common.dependsOn.all { dependency ->
        dependencies.dependsOn(dependency.main.sourceSet) {
            addMainDependencies(dependency as CommonTargetInternal, objectFactory, it)
        }
    }
}

private fun addIncludedClientDependencies(common: CommonTargetInternal, dependencies: SourceSetDependencyInfo) {
    val client = common.client

    if (client != null) {
        dependencies.dependsOn(client.sourceSet) {
            it.dependsOn(common.main.sourceSet)

            common.dependsOn.all { dependency ->
                addIncludedClientDependencies(dependency as CommonTargetInternal, it)
            }
        }
    }
}

internal fun MinecraftTargetInternal.addDependencies(
    dependency: CommonTargetInternal,
    includedClient: Provider<Boolean>
) {
    compilations.all {
        val structure = it.sourceSet.extension<MultiplatformStructure>()

        if (it.name == ClochePlugin.DATA_COMPILATION_NAME) {
            structure.dependsOn(dependency.withData().sourceSet) {
                it.dependsOn(dependency.main.sourceSet)

                setDependenciesWithData(dependency, it)
            }
        } else if (it.name == ClochePlugin.CLIENT_COMPILATION_NAME) {
            structure.dependsOn(dependency.withClient().sourceSet) {
                it.dependsOn(dependency.main.sourceSet)

                setDependenciesWithClient(dependency, it)
            }
        } else if (it.name == SourceSet.MAIN_SOURCE_SET_NAME) {
            structure.dependsOn(dependency.main.sourceSet) {
                addMainDependencies(dependency, project.objects, it)
            }
        }
    }

    project.afterEvaluate {
        if (includedClient.get()) {
            val client = dependency.client

            if (client != null) {
                main.sourceSet.extension<MultiplatformStructure>().dependsOn(client.sourceSet) {
                    it.dependsOn(dependency.main.sourceSet)

                    dependency.dependsOn.all { dependency ->
                        addIncludedClientDependencies(dependency as CommonTargetInternal, it)
                    }
                }
            }
        }
    }
}
*/
