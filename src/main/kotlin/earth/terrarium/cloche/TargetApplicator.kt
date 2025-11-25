package earth.terrarium.cloche

import earth.terrarium.cloche.target.CommonCompilation
import earth.terrarium.cloche.target.CommonTargetInternal
import earth.terrarium.cloche.target.CommonTopLevelCompilation
import earth.terrarium.cloche.target.CompilationInternal
import earth.terrarium.cloche.target.MinecraftTargetInternal
import earth.terrarium.cloche.target.fabric.FabricClientSecondarySourceSets
import earth.terrarium.cloche.target.fabric.FabricTargetImpl
import net.msrandom.minecraftcodev.core.MinecraftDependenciesOperatingSystemMetadataRule
import net.msrandom.minecraftcodev.core.VERSION_MANIFEST_URL
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.getGlobalCacheDirectory
import net.msrandom.virtualsourcesets.SourceSetStaticLinkageInfo
import org.gradle.api.Project
import org.gradle.kotlin.dsl.all
import org.gradle.kotlin.dsl.listProperty

internal fun applyTargets(project: Project, cloche: ClocheExtension) {
    cloche.targets.all {
        val target = this

        target as MinecraftTargetInternal

        addTarget(cloche, project, target)

        target.dependsOn.all {
            val dependency = this

            dependency as CommonTargetInternal

            fun CommonTargetInternal.setDependenciesWithClient(targetClient: FabricClientSecondarySourceSets): CommonTopLevelCompilation {
                targetClient.data.onConfigured {
                    data.configure()
                }

                targetClient.test.onConfigured {
                    test.configure()
                }

                dependsOn.all {
                    this as CommonTargetInternal

                    setDependenciesWithClient(targetClient)
                }

                return client()
            }

            fun CommonTargetInternal.setDependenciesWithData(): CommonCompilation {
                dependsOn.all {
                    this as CommonTargetInternal

                    setDependenciesWithData()
                }

                return data()
            }

            fun CommonTargetInternal.setDependenciesWithTest(): CommonCompilation {
                dependsOn.all {
                    this as CommonTargetInternal

                    setDependenciesWithTest()
                }

                return test()
            }

            fun CommonTargetInternal.addIncludedClientWeakLinks(info: SourceSetStaticLinkageInfo) {
                client.onConfigured {
                    it.data.onConfigured { data ->
                        this.data.onConfigured { commonData ->
                            println("(source dependency) ($target only) $data -> $commonData")
                            info.weakTreeLink(data.sourceSet, commonData.sourceSet)
                        }
                    }

                    it.test.onConfigured { test ->
                        this.test.onConfigured { commonTest ->
                            println("(source dependency) ($target only) $test -> $commonTest")
                            info.weakTreeLink(test.sourceSet, commonTest.sourceSet)
                        }
                    }

                    println("(source dependency) ($target only) $it -> $main")
                    info.weakTreeLink(it.sourceSet, sourceSet)
                }

                dependsOn.all {
                    this as CommonTargetInternal

                    addIncludedClientWeakLinks(info)
                }
            }

            with(project) {
                target.data.onConfigured { data ->
                    val dependency = dependency.setDependenciesWithData()

                    data.addSourceDependency(dependency)
                }

                target.test.onConfigured { test ->
                    val dependency = dependency.setDependenciesWithTest()

                    test.addSourceDependency(dependency)
                }

                if (target is FabricTargetImpl) {
                    target.client.onConfigured {
                        val dependency = dependency.setDependenciesWithClient(it)

                        it.addSourceDependency(dependency)

                        it.data.onConfigured { data ->
                            data.addSourceDependency(dependency.data())
                        }

                        it.test.onConfigured { test ->
                            test.addSourceDependency(dependency.test())
                        }
                    }
                }

                val staticLinkage = target.sourceSet.extension<SourceSetStaticLinkageInfo>()

                target.main.addSourceDependency(dependency.main)

                target.onClientIncluded {
                    dependency.client.onConfigured { client ->
                        target.main.addSourceDependency(client)

                        target.data.onConfigured { targetData ->
                            client.data.onConfigured { clientData ->
                                targetData.addSourceDependency(clientData)
                            }
                        }

                        target.test.onConfigured { targetTest ->
                            client.test.onConfigured { clientTest ->
                                targetTest.addSourceDependency(clientTest)
                            }
                        }
                    }

                    dependency.addIncludedClientWeakLinks(staticLinkage)
                }
            }
        }
    }

    cloche.commonTargets.all {
        val commonTarget = this

        commonTarget as CommonTargetInternal

        commonTarget.dependsOn.all {
            val dependency = this

            dependency as CommonTargetInternal

            with(project) {
                fun add(compilation: CompilationInternal, dependencyCompilation: CommonCompilation) {
                    compilation.addSourceDependency(dependencyCompilation)
                }

                add(commonTarget.main, dependency.main)

                commonTarget.data.onConfigured { data ->
                    dependency.data.onConfigured { dependencyData ->
                        add(data, dependencyData)
                    }
                }

                commonTarget.test.onConfigured { test ->
                    dependency.test.onConfigured { dependencyTest ->
                        add(test, dependencyTest)
                    }
                }

                commonTarget.client.onConfigured { client ->
                    dependency.client.onConfigured { dependencyClient ->
                        add(client, dependencyClient)

                        commonTarget.data.onConfigured { data ->
                            dependency.data.onConfigured { dependencyData ->
                                add(data, dependencyData)
                            }
                        }

                        commonTarget.test.onConfigured { test ->
                            dependency.test.onConfigured { dependencyTest ->
                                add(test, dependencyTest)
                            }
                        }
                    }
                }
            }
        }

        with(project) {
            val objects = objects

            val onlyCommonOfType = commonTarget.commonType.flatMap { type ->
                val types = objects.listProperty<String>()

                for (target in cloche.commonTargets) {
                    types.add((target as CommonTargetInternal).commonType)
                }

                types.map { it ->
                    it.count { it == type } == 1
                }
            }.orElse(true)

            createCommonTarget(commonTarget, onlyCommonOfType)
        }
    }

    // afterEvaluate needed because of the component rule using providers
    project.afterEvaluate {
        val targets = if (cloche.singleTargetConfigurator.target == null) {
            cloche.targets
        } else {
            listOf(cloche.singleTargetConfigurator.target!!)
        }

        project.dependencies.components.all<MinecraftDependenciesOperatingSystemMetadataRule> {
            params(
                getGlobalCacheDirectory(project),
                targets.map { it.minecraftVersion.get() },
                VERSION_MANIFEST_URL,
                project.gradle.startParameter.isOffline,
            )
        }
    }
}
