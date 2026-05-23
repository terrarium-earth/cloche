package earth.terrarium.cloche.target

import earth.terrarium.cloche.ClochePlugin
import earth.terrarium.cloche.REMAPPED_ATTRIBUTE
import earth.terrarium.cloche.WITHOUT_DATA_ATTRIBUTE
import earth.terrarium.cloche.addClasspathDependency
import earth.terrarium.cloche.addDataClasspathDependency
import earth.terrarium.cloche.api.attributes.CompilationAttributes
import earth.terrarium.cloche.api.attributes.ModDistribution
import earth.terrarium.cloche.target.compilation.TargetCompilation
import earth.terrarium.cloche.target.compilation.configureSourceSet
import earth.terrarium.cloche.target.compilation.createCompilationVariants
import earth.terrarium.cloche.target.compilation.externalApiConfigurationName
import earth.terrarium.cloche.target.compilation.externalCompileConfigurationName
import earth.terrarium.cloche.target.compilation.externalRuntimeConfigurationName
import earth.terrarium.cloche.target.compilation.localImplementationConfigurationName
import earth.terrarium.cloche.target.compilation.localRuntimeConfigurationName
import earth.terrarium.cloche.target.compilation.modConfigurationName
import earth.terrarium.cloche.target.compilation.resolvableModConfigurationName
import earth.terrarium.cloche.target.compilation.resolvableNonModConfigurationName
import earth.terrarium.cloche.target.compilation.sourceSetName
import earth.terrarium.cloche.target.fabric.FabricTargetImpl
import earth.terrarium.cloche.util.configureClassesAndResourcesVariant
import earth.terrarium.cloche.util.optionalDir
import earth.terrarium.cloche.util.withIdeaModule
import net.msrandom.minecraftcodev.core.MinecraftOperatingSystemAttribute
import net.msrandom.minecraftcodev.core.VERSION_MANIFEST_URL
import net.msrandom.minecraftcodev.core.getVersionList
import net.msrandom.minecraftcodev.core.operatingSystemName
import net.msrandom.minecraftcodev.core.task.CachedMinecraftParameters
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.getGlobalCacheDirectory
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.runs.downloadAssetsTaskName
import net.msrandom.minecraftcodev.runs.extractNativesTaskName
import net.msrandom.minecraftcodev.runs.task.DownloadAssets
import net.msrandom.minecraftcodev.runs.task.ExtractNatives
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationVariant
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyCollector
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

internal const val MOD_ID_CATEGORY = "mod-id"

fun Project.javaExecutableFor(
    version: Provider<String>,
    cacheParameters: CachedMinecraftParameters,
): Provider<RegularFile> {
    val javaVersion = version.flatMap { version ->
        cacheParameters.directory.flatMap { cacheDirectory ->
            cacheParameters.versionManifestUrl.flatMap { url ->
                cacheParameters.getIsOffline().map { offline ->
                    val version = getVersionList(cacheDirectory.asFile.toPath(), url, offline).version(version)

                    JavaLanguageVersion.of(version.javaVersion.majorVersion)
                }
            }
        }
    }

    return extension<JavaToolchainService>().launcherFor {
        languageVersion.set(javaVersion)
    }.map { it.executablePath }
}

@Suppress("UnstableApiUsage")
private fun TargetCompilation<*>.addModDependencies(configurationName: String, collector: DependencyCollector, modCollector: DependencyCollector) {
    project.configurations.dependencyScope(modConfigurationName(configurationName)) {
        addCollectedDependencies(modCollector)
    }

    project.configurations.named(configurationName) {
        addCollectedDependencies(collector)
    }
}

@Suppress("UnstableApiUsage")
private fun TargetCompilation<*>.addDependencies() {
    addModDependencies(sourceSet.implementationConfigurationName, dependencyHandler.implementation, dependencyHandler.modImplementation)
    addModDependencies(sourceSet.runtimeOnlyConfigurationName, dependencyHandler.runtimeOnly, dependencyHandler.modRuntimeOnly)
    addModDependencies(sourceSet.compileOnlyConfigurationName, dependencyHandler.compileOnly, dependencyHandler.modCompileOnly)
    addModDependencies(sourceSet.localRuntimeConfigurationName, dependencyHandler.localRuntime, dependencyHandler.modLocalRuntime)
    addModDependencies(sourceSet.localImplementationConfigurationName, dependencyHandler.localImplementation, dependencyHandler.modLocalImplementation)

    if (!isTest) {
        addModDependencies(
            sourceSet.compileOnlyApiConfigurationName,
            dependencyHandler.compileOnlyApi,
            dependencyHandler.modCompileOnlyApi
        )
        addModDependencies(sourceSet.apiConfigurationName, dependencyHandler.api, dependencyHandler.modApi)
    }

    project.configurations.named(sourceSet.annotationProcessorConfigurationName) {
        addCollectedDependencies(dependencyHandler.annotationProcessor)
    }

    project.configurations.named(sourceSet.externalRuntimeConfigurationName) {
        addCollectedDependencies(dependencyHandler.externalRuntime)
    }

    project.configurations.named(sourceSet.externalCompileConfigurationName) {
        addCollectedDependencies(dependencyHandler.externalCompile)
    }

    project.configurations.named(sourceSet.externalApiConfigurationName) {
        addCollectedDependencies(dependencyHandler.externalApi)
    }

    fun Configuration.extendModCompileClasspath() {
        extendsFrom(project.configurations.named(modConfigurationName(sourceSet.compileOnlyConfigurationName)))
        extendsFrom(project.configurations.named(modConfigurationName(sourceSet.implementationConfigurationName)))
        extendsFrom(project.configurations.named(modConfigurationName(sourceSet.localImplementationConfigurationName)))

        if (!isTest) {
            extendsFrom(project.configurations.named(modConfigurationName(sourceSet.compileOnlyApiConfigurationName)))
            extendsFrom(project.configurations.named(modConfigurationName(sourceSet.apiConfigurationName)))
        }
    }

    fun Configuration.extendModRuntimeClasspath() {
        extendsFrom(project.configurations.named(modConfigurationName(sourceSet.runtimeOnlyConfigurationName)))
        extendsFrom(project.configurations.named(modConfigurationName(sourceSet.implementationConfigurationName)))

        extendsFrom(project.configurations.named(modConfigurationName(sourceSet.localRuntimeConfigurationName)))
        extendsFrom(project.configurations.named(modConfigurationName(sourceSet.localImplementationConfigurationName)))

        if (!isTest) {
            extendsFrom(project.configurations.named(modConfigurationName(sourceSet.apiConfigurationName)))
        }
    }

    project.configurations.resolvable(resolvableModConfigurationName(sourceSet.compileClasspathConfigurationName)) {
        isTransitive = false

        shouldResolveConsistentlyWith(project.configurations.getByName(sourceSet.compileClasspathConfigurationName))

        extendModCompileClasspath()
    }

    project.configurations.resolvable(resolvableModConfigurationName(sourceSet.runtimeClasspathConfigurationName)) {
        isTransitive = false

        shouldResolveConsistentlyWith(project.configurations.getByName(sourceSet.runtimeClasspathConfigurationName))

        extendModRuntimeClasspath()
    }

    project.configurations.resolvable(resolvableNonModConfigurationName(sourceSet.compileClasspathConfigurationName)) {
        shouldResolveConsistentlyWith(project.configurations.getByName(sourceSet.compileClasspathConfigurationName))

        extendsFrom(project.configurations.named(sourceSet.compileOnlyConfigurationName))
        extendsFrom(project.configurations.named(sourceSet.implementationConfigurationName))
        extendsFrom(project.configurations.named(sourceSet.localImplementationConfigurationName))

        if (!isTest) {
            extendsFrom(project.configurations.named(sourceSet.compileOnlyApiConfigurationName))
            extendsFrom(project.configurations.named(sourceSet.apiConfigurationName))
        }
    }

    project.configurations.resolvable(resolvableNonModConfigurationName(sourceSet.runtimeClasspathConfigurationName)) {
        shouldResolveConsistentlyWith(project.configurations.getByName(sourceSet.runtimeClasspathConfigurationName))

        extendsFrom(project.configurations.named(sourceSet.runtimeOnlyConfigurationName))
        extendsFrom(project.configurations.named(sourceSet.implementationConfigurationName))

        extendsFrom(project.configurations.named(sourceSet.localRuntimeConfigurationName))
        extendsFrom(project.configurations.named(sourceSet.localImplementationConfigurationName))

        if (!isTest) {
            extendsFrom(project.configurations.named(sourceSet.apiConfigurationName))
        }
    }

    project.configurations.named(sourceSet.compileClasspathConfigurationName) {
        extendsFrom(project.configurations.named(sourceSet.localImplementationConfigurationName))

        extendModCompileClasspath()
    }

    project.configurations.named(sourceSet.runtimeClasspathConfigurationName) {
        extendsFrom(project.configurations.named(sourceSet.localImplementationConfigurationName))
        extendsFrom(project.configurations.named(sourceSet.localRuntimeConfigurationName))

        extendModRuntimeClasspath()
    }

    project.configurations.named(sourceSet.apiElementsConfigurationName) {
        extendsFrom(project.configurations.named(modConfigurationName(sourceSet.apiConfigurationName)))
        extendsFrom(project.configurations.named(modConfigurationName(sourceSet.compileOnlyApiConfigurationName)))

        extendsFrom(project.configurations.named(sourceSet.externalCompileConfigurationName))
        extendsFrom(project.configurations.named(sourceSet.externalApiConfigurationName))
    }

    project.configurations.named(sourceSet.runtimeElementsConfigurationName) {
        extendsFrom(project.configurations.named(modConfigurationName(sourceSet.runtimeOnlyConfigurationName)))
        extendsFrom(project.configurations.named(modConfigurationName(sourceSet.implementationConfigurationName)))

        extendsFrom(project.configurations.named(sourceSet.externalRuntimeConfigurationName))
        extendsFrom(project.configurations.named(sourceSet.externalApiConfigurationName))
    }
}

context(Project)
internal fun handleTarget(target: MinecraftTargetInternal) {
    fun addCompilation(compilation: TargetCompilation<*>, testName: String? = null) {
        val sourceSet = compilation.sourceSet

        if (!compilation.isTest) {
            createCompilationVariants(compilation, sourceSet, true)
        }

        configureSourceSet(sourceSet, target, compilation)

        compilation.addDependencies()

        val copyMixins = tasks.register<Copy>(
            lowerCamelCaseGradleName("copy", target.featureName, compilation.featureName, "mixins"),
        ) {
            from(compilation.mixins)

            destinationDir = layout.buildDirectory.dir("mixins").get().optionalDir(target.namePath).dir(compilation.namePath).asFile
        }

        project.tasks.named<ProcessResources>(sourceSet.processResourcesTaskName) {
            from(copyMixins.map(Copy::getDestinationDir))
        }

        project.withIdeaModule(sourceSet) {
            it.resourceDirs.add(copyMixins.get().destinationDir)
        }

        target.registerAccessWidenerMergeTask(compilation)

        if (!compilation.isTest) {
            target.addJarInjects(compilation)
        }

        val javaVersion = target.minecraftVersion.map {
            getVersionList(
                getGlobalCacheDirectory(project).toPath(),
                VERSION_MANIFEST_URL,
                gradle.startParameter.isOffline
            )
                .version(it)
                .javaVersion
                .majorVersion
        }

        // TODO do the same for the javadoc tasks
        tasks.named<JavaCompile>(sourceSet.compileJavaTaskName) {
            options.release.set(javaVersion)
        }

        plugins.withId(ClochePlugin.Companion.KOTLIN_JVM_PLUGIN_ID) {
            tasks.named<KotlinCompile>(sourceSet.getCompileTaskName("kotlin")) {
                compilerOptions.jvmTarget.set(javaVersion.map {
                    JvmTarget.fromTarget(JavaVersion.toVersion(it).toString())
                })
            }
        }

        val resolvableConfigurationNames = listOf(
            sourceSet.compileClasspathConfigurationName,
            sourceSet.runtimeClasspathConfigurationName,
        )

        val libraryConsumableConfigurationNames = listOf(
            sourceSet.apiElementsConfigurationName,
            sourceSet.runtimeElementsConfigurationName,
        )

        val consumableConfigurationNames = libraryConsumableConfigurationNames + listOf(
            sourceSet.javadocElementsConfigurationName,
            sourceSet.sourcesElementsConfigurationName,
        )

        for (name in libraryConsumableConfigurationNames) {
            if (compilation.isTest) {
                continue
            }

            configurations.named(name) {
                val configuration = this

                // TODO Can this be avoided? maybe publishing remapped Jars in a separate variant if our named namespace is stable(like mojang mappings)?
                //  Alternatively could we find the exact artifact by checking if the files match? or via the build dependencies?
                artifacts.clear()

                project.artifacts.add(name, compilation.includeJarTask!!)
            }
        }

        if (!compilation.isTest) {
            configureClassesAndResourcesVariant(sourceSet)
        }

        for (name in resolvableConfigurationNames) {
            configurations.named(name) {
                attributes(compilation::resolvableAttributes)

                attributes.attribute(
                    MinecraftOperatingSystemAttribute.attribute,
                    objects.named(operatingSystemName()),
                )
            }
        }

        for (name in consumableConfigurationNames) {
            configurations.findByName(name)?.attributes(compilation::consumableAttributes)
        }

        configurations.named(sourceSet.compileClasspathConfigurationName) {
            attributes {
                attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, named(LibraryElements.CLASSES))
            }
        }

        configurations.named(sourceSet.runtimeClasspathConfigurationName) {
            attributes {
                attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, named(LibraryElements.CLASSES_AND_RESOURCES))
            }
        }

        if (testName != null) {
            val sourceSetName = sourceSetName(target, testName)

            project.extension<SourceSetContainer>().named { it == sourceSetName }.configureEach {
                for (name in listOf(compileClasspathConfigurationName, runtimeClasspathConfigurationName)) {
                    project.configurations.named(name) {
                        attributes(compilation::resolvableAttributes)
                    }
                }
            }
        }
    }

    fun configureMainForData(main: TargetCompilation<*>) {
        val name = lowerCamelCaseGradleName(main.sourceSet.takeUnless(SourceSet::isMain)?.name, "runtimeWithoutDataElements")

        val runtimeElements = configurations.named(main.sourceSet.runtimeElementsConfigurationName)

        main.sourceSet.output.dir(mapOf("builtBy" to main.generateMetadataTask), main.metadataDirectory)
        main.sourceSet.output.dir(target.datagenDirectory)

        configurations.named(target.main.sourceSet.runtimeElementsConfigurationName) {
            outgoing {
                variants {
                    named(LibraryElements.RESOURCES) {
                        artifact(main.metadataDirectory) {
                            type = ArtifactTypeDefinition.JVM_RESOURCES_DIRECTORY
                        }

                        artifact(target.datagenDirectory) {
                            type = ArtifactTypeDefinition.JVM_RESOURCES_DIRECTORY
                        }
                    }
                }
            }
        }

        configurations.consumable(name) {
            attributes {
                addAllLater(runtimeElements.get().attributes)
                attribute(WITHOUT_DATA_ATTRIBUTE, true)
            }

            outgoing {
                capability("$group:${project.name}:$version")

                capability(main.capabilitySuffix.map {
                    "$group:${project.name}-$it:$version"
                })

                variants {
                    register(LibraryElements.CLASSES) {
                        val classes = runtimeElements.flatMap {
                            it.outgoing.variants.named(LibraryElements.CLASSES)
                        }

                        attributes {
                            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, named(LibraryElements.CLASSES))
                        }

                        artifacts.addAllLater(classes.map(ConfigurationVariant::getArtifacts))
                    }

                    register(LibraryElements.RESOURCES) {
                        val resources = runtimeElements.flatMap {
                            it.outgoing.variants.named(LibraryElements.RESOURCES)
                        }

                        attributes {
                            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, named(LibraryElements.RESOURCES))
                        }

                        artifacts.addAllLater(resources.map {
                            it.artifacts.matching { !main.generatedResourceOutputs.contains(it.file) }
                        })
                    }
                }
            }

            extendsFrom(runtimeElements)

            configureClassesAndResourcesVariant(this)
        }
    }

    addCompilation(target.main, SourceSet.TEST_SOURCE_SET_NAME)
    configureMainForData(target.main)

    target.data.onConfigured {
        addCompilation(it)
        it.addDataClasspathDependency(target.main)
    }

    target.test.onConfigured {
        addCompilation(it)
        it.addClasspathDependency(target.main)
    }

    if (target is FabricTargetImpl) {
        target.client.onConfigured { client ->
            addCompilation(client, ClochePlugin.CLIENT_TEST_COMPILATION_NAME)
            client.addClasspathDependency(target.main)

            configureMainForData(client)

            client.sourceSet.output.dir(target.datagenClientDirectory)

            configurations.named(target.main.sourceSet.runtimeElementsConfigurationName) {
                outgoing {
                    variants {
                        named(LibraryElements.RESOURCES) {
                            artifact(target.datagenClientDirectory) {
                                type = ArtifactTypeDefinition.JVM_RESOURCES_DIRECTORY
                            }
                        }
                    }
                }
            }

            client.data.onConfigured { data ->
                addCompilation(data)
                data.addDataClasspathDependency(client)
                data.addDataClasspathDependency(target.main)

                target.data.onConfigured {
                    data.addDataClasspathDependency(it)
                }
            }

            client.test.onConfigured { test ->
                addCompilation(test)
                test.addClasspathDependency(client)
                test.addClasspathDependency(target.main)

                target.test.onConfigured {
                    test.addClasspathDependency(it)
                }
            }
        }
    }

    tasks.named<DownloadAssets>(target.sourceSet.downloadAssetsTaskName) {
        minecraftVersion.set(target.minecraftVersion)
    }

    tasks.named<ExtractNatives>(target.sourceSet.extractNativesTaskName) {
        minecraftVersion.set(target.minecraftVersion)
    }

    artifacts.add(Dependency.ARCHIVES_CONFIGURATION, target.finalJar)
}
