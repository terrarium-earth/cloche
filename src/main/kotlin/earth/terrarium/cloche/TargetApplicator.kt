package earth.terrarium.cloche

import earth.terrarium.cloche.api.target.ClocheTarget
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
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator
import kotlin.collections.mapValues

internal fun applyTargets(project: Project, cloche: ClocheExtension) {
    fun getDependencies(target: ClocheTarget): Provider<List<CommonTargetInternal>> = project.provider {
        target.dependsOn
    }.flatMap {
        val list = project.objects.listProperty(CommonTargetInternal::class.java)

        for (target in it) {
            list.add(target as CommonTargetInternal)
            list.addAll(getDependencies(target))
        }

        list
    }

    val targetsProvider = project.provider {
        cloche.targets.map { it as MinecraftTargetInternal }
    }

    @Suppress("UNCHECKED_CAST")
    val targetDependencies = targetsProvider.flatMap { targets ->
        val association = project.objects.mapProperty(
            MinecraftTargetInternal::class.java,
            List::class.java
        ) as MapProperty<MinecraftTargetInternal<*>, List<CommonTargetInternal>>

        for (target in targets) {
            association.put(target, getDependencies(target))
        }

        association
    }

    @Suppress("UNCHECKED_CAST")
    val commonToTarget = targetDependencies.map {
        val association = hashMapOf<CommonTargetInternal, MutableSet<MinecraftTargetInternal<*>>>()

        for ((edgeTarget, dependencies) in it) {
            for (dependency in dependencies) {
                association.computeIfAbsent(dependency) { hashSetOf() }.add(edgeTarget as MinecraftTargetInternal)
            }
        }

        association as Map<CommonTargetInternal, Set<MinecraftTargetInternal<*>>>
    }

    @Suppress("UNCHECKED_CAST")
    val commons = project.objects.mapProperty(CommonTargetInternal::class.java, CommonInfo::class.java)

    commons.putAll(commonToTarget.map {
        it.mapValues { (_, edges) ->
            var commonType: String? = null
            var minecraftVersion: String? = null

            for (target in edges) {
                if (minecraftVersion == null) {
                    minecraftVersion = target.minecraftVersion.get()
                } else if (minecraftVersion != target.minecraftVersion.get()) {
                    minecraftVersion = null
                }

                if (commonType == null) {
                    commonType = target.commonType
                } else if (target.commonType != commonType) {
                    commonType = null
                    break
                }
            }

            CommonInfo(
                edges,
                commonType,
                minecraftVersion,
            )
        }
    })

    cloche.targets.all { target ->
        target as MinecraftTargetInternal

        target.initialize(false)

        addTarget(cloche, project, target, false)

        target.dependsOn.all { dependency ->
            dependency as CommonTargetInternal

            fun setDependenciesWithClient(targetClient: FabricClientSecondarySourceSets, common: CommonTargetInternal): CommonTopLevelCompilation {
                targetClient.data.onConfigured {
                    common.data.configure()
                }

                targetClient.test.onConfigured {
                    common.test.configure()
                }

                common.dependsOn.all {
                    setDependenciesWithClient(targetClient, it as CommonTargetInternal)
                }

                return common.client()
            }

            fun setDependenciesWithData(common: CommonTargetInternal): CompilationInternal {
                common.dependsOn.all {
                    setDependenciesWithData(it as CommonTargetInternal)
                }

                return common.data()
            }

            fun setDependenciesWithTest(common: CommonTargetInternal): CompilationInternal {
                common.dependsOn.all {
                    setDependenciesWithTest(it as CommonTargetInternal)
                }

                return common.data()
            }

            fun addIncludedClientWeakLinks(info: SourceSetStaticLinkageInfo, common: CommonTargetInternal) {
                common.client.onConfigured {
                    it.data.onConfigured { data ->
                        common.data.onConfigured { commonData ->
                            println("(source dependency) ($target only) $data -> $commonData")
                            info.weakTreeLink(data.sourceSet, commonData.sourceSet)
                        }
                    }

                    it.test.onConfigured { test ->
                        common.test.onConfigured { commonTest ->
                            println("(source dependency) ($target only) $test -> $commonTest")
                            info.weakTreeLink(test.sourceSet, commonTest.sourceSet)
                        }
                    }

                    println("(source dependency) ($target only) $it -> ${common.main}")
                    info.weakTreeLink(it.sourceSet, common.sourceSet)
                }

                common.dependsOn.all { dependency ->
                    dependency as CommonTargetInternal

                    addIncludedClientWeakLinks(info, dependency)
                }
            }

            with(project) {
                target.data.onConfigured { data ->
                    val dependency = setDependenciesWithData(dependency)

                    data.addSourceDependency(dependency)
                }

                target.test.onConfigured { test ->
                    val dependency = setDependenciesWithData(dependency)

                    test.addSourceDependency(dependency)
                }

                if (target is FabricTargetImpl) {
                    target.client.onConfigured {
                        val dependency = setDependenciesWithClient(it, dependency)

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

                    addIncludedClientWeakLinks(staticLinkage, dependency)
                }
            }
        }
    }

    cloche.commonTargets.all { commonTarget ->
        commonTarget as CommonTargetInternal

        commonTarget.dependsOn.all { dependency ->
            dependency as CommonTargetInternal

            with(project) {
                fun add(compilation: CompilationInternal, dependencyCompilation: CompilationInternal) {
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
            val commonInfo = commons.getting(commonTarget)

            val onlyCommonOfType = commons.zip(commonInfo, ::Pair).map { (commons, info) ->
                commons.count { it.value.type == info.type } == 1
            }

            createCommonTarget(commonTarget, commonInfo, onlyCommonOfType)
        }
    }

    // afterEvaluate needed because of the component rule using providers
    project.afterEvaluate {
        val targets = if (cloche.singleTargetConfigurator.target == null) {
            cloche.targets
        } else {
            listOf(cloche.singleTargetConfigurator.target!!)
        }

        project.dependencies.components.all(MinecraftDependenciesOperatingSystemMetadataRule::class.java) {
            it.params(
                getGlobalCacheDirectory(project),
                targets.map { it.minecraftVersion.get() },
                VERSION_MANIFEST_URL,
                project.gradle.startParameter.isOffline,
            )
        }
    }
}
