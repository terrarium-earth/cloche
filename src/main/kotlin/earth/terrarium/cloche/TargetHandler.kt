package earth.terrarium.cloche

import earth.terrarium.cloche.api.attributes.CompilationAttributes
import earth.terrarium.cloche.target.*
import earth.terrarium.cloche.target.fabric.FabricTargetImpl
import earth.terrarium.cloche.util.optionalDir
import net.msrandom.minecraftcodev.core.MinecraftOperatingSystemAttribute
import net.msrandom.minecraftcodev.core.VERSION_MANIFEST_URL
import net.msrandom.minecraftcodev.core.getVersionList
import net.msrandom.minecraftcodev.core.operatingSystemName
import net.msrandom.minecraftcodev.core.task.CachedMinecraftParameters
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.getGlobalCacheDirectory
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.core.utils.named
import net.msrandom.minecraftcodev.runs.downloadAssetsTaskName
import net.msrandom.minecraftcodev.runs.extractNativesTaskName
import net.msrandom.minecraftcodev.runs.task.DownloadAssets
import net.msrandom.minecraftcodev.runs.task.ExtractNatives
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyCollector
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

internal const val MOD_ID_CATEGORY = "mod-id"
internal const val REMAPPED_SUBVARIANT_NAME = "remapped"
internal const val CLASSES_AND_RESOURCES_SUBVARIANT_NAME = "classesAndResources"

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
        it.languageVersion.set(javaVersion)
    }.map { it.executablePath }
}

@Suppress("UnstableApiUsage")
private fun TargetCompilation<*>.addModDependencies(configurationName: String, collector: DependencyCollector, modCollector: DependencyCollector) {
    val modImplementation =
        project.configurations.dependencyScope(modConfigurationName(configurationName)) {
            it.addCollectedDependencies(modCollector)
        }

    project.configurations.named(configurationName) {
        it.addCollectedDependencies(collector)

        it.extendsFrom(modImplementation.get())
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
        it.addCollectedDependencies(dependencyHandler.annotationProcessor)
    }

    project.configurations.resolvable(modConfigurationName(sourceSet.compileClasspathConfigurationName)) {
        it.isTransitive = false

        it.shouldResolveConsistentlyWith(project.configurations.getByName(sourceSet.compileClasspathConfigurationName))

        it.extendsFrom(project.configurations.getByName(modConfigurationName(sourceSet.compileOnlyConfigurationName)))
        it.extendsFrom(project.configurations.getByName(modConfigurationName(sourceSet.implementationConfigurationName)))
        it.extendsFrom(project.configurations.getByName(modConfigurationName(sourceSet.localImplementationConfigurationName)))

        if (!isTest) {
            it.extendsFrom(project.configurations.getByName(modConfigurationName(sourceSet.compileOnlyApiConfigurationName)))
            it.extendsFrom(project.configurations.getByName(modConfigurationName(sourceSet.apiConfigurationName)))
        }
    }

    project.configurations.resolvable(modConfigurationName(sourceSet.runtimeClasspathConfigurationName)) {
        it.isTransitive = false

        it.shouldResolveConsistentlyWith(project.configurations.getByName(sourceSet.runtimeClasspathConfigurationName))

        it.extendsFrom(project.configurations.getByName(modConfigurationName(sourceSet.runtimeOnlyConfigurationName)))
        it.extendsFrom(project.configurations.getByName(modConfigurationName(sourceSet.localRuntimeConfigurationName)))
        it.extendsFrom(project.configurations.getByName(modConfigurationName(sourceSet.implementationConfigurationName)))

        it.extendsFrom(project.configurations.getByName(modConfigurationName(sourceSet.localRuntimeConfigurationName)))
        it.extendsFrom(project.configurations.getByName(modConfigurationName(sourceSet.localImplementationConfigurationName)))

        if (!isTest) {
            it.extendsFrom(project.configurations.getByName(modConfigurationName(sourceSet.apiConfigurationName)))
        }
    }

    project.configurations.named(sourceSet.compileClasspathConfigurationName) {
        it.extendsFrom(project.configurations.getByName(sourceSet.localImplementationConfigurationName))
    }

    project.configurations.named(sourceSet.runtimeClasspathConfigurationName) {
        it.extendsFrom(project.configurations.getByName(sourceSet.localImplementationConfigurationName))
        it.extendsFrom(project.configurations.getByName(sourceSet.localRuntimeConfigurationName))
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

        val copyMixins = tasks.register(
            lowerCamelCaseGradleName("copy", target.featureName, compilation.featureName, "mixins"),
            Copy::class.java
        ) {
            it.from(compilation.mixins)

            it.destinationDir = layout.buildDirectory.dir("mixins").get().optionalDir(target.namePath).dir(compilation.namePath).asFile
        }

        project.tasks.named(sourceSet.processResourcesTaskName, ProcessResources::class.java) {
            it.from(copyMixins.map(Copy::getDestinationDir))
        }

        project.ideaModule(sourceSet) {
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
        tasks.named(sourceSet.compileJavaTaskName, JavaCompile::class.java) { compile ->
            compile.options.release.set(javaVersion)
        }

        plugins.withId("org.jetbrains.kotlin.jvm") {
            tasks.named(sourceSet.getCompileTaskName("kotlin"), KotlinCompile::class.java) {
                it.compilerOptions.jvmTarget.set(javaVersion.map {
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

        val configurationNames = resolvableConfigurationNames + consumableConfigurationNames

        for (name in libraryConsumableConfigurationNames) {
            if (compilation.isTest) {
                continue
            }

            configurations.named(name) { configuration ->
                // TODO Can this be avoided? maybe publishing remapped Jars in a separate variant if our named namespace is stable(like mojang mappings)?
                //  Alternatively could we find the exact artifact by checking if the files match? or via the build dependencies?
                configuration.artifacts.clear()

                project.artifacts.add(name, compilation.includeJarTask!!)

                val remappedVariant = configuration.outgoing.variants.create(REMAPPED_SUBVARIANT_NAME) {
                    it.attributes.attribute(REMAPPED_ATTRIBUTE, true)
                    it.attributes.attribute(INCLUDE_TRANSFORMED_OUTPUT_ATTRIBUTE, false)

                    it.artifact(tasks.named(sourceSet.jarTaskName))
                }

                components.named("java") {
                    it as AdhocComponentWithVariants

                    it.withVariantsFromConfiguration(configuration) {
                        if (it.configurationVariant.name == remappedVariant.name) {
                            it.skip()
                        }
                    }
                }

                configuration.outgoing.variants.named { it == "classes" || it == "resources" }.configureEach {
                    it.attributes.attribute(REMAPPED_ATTRIBUTE, true)
                    it.attributes.attribute(INCLUDE_TRANSFORMED_OUTPUT_ATTRIBUTE, false)
                }
            }
        }

        if (!compilation.isTest) {
            configurations.named(sourceSet.runtimeElementsConfigurationName) { configuration ->
                val classesAndResourcesVariants = configuration.outgoing.variants.named { it == "classes" || it == "resources" }

                val classesAndResourcesVariant = configuration.outgoing.variants.maybeCreate(
                    CLASSES_AND_RESOURCES_SUBVARIANT_NAME
                ).also {
                    it.attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.CLASSES_AND_RESOURCES))

                    classesAndResourcesVariants.configureEach { variant ->
                        variant.artifacts.configureEach { artifact ->
                            it.artifact(artifact)
                        }
                    }
                }

                components.named("java") {
                    it as AdhocComponentWithVariants

                    it.withVariantsFromConfiguration(configuration) {
                        if (it.configurationVariant.name == classesAndResourcesVariant.name) {
                            it.skip()
                        }
                    }
                }
            }
        }

        for (name in resolvableConfigurationNames) {
            configurations.named(name) { configuration ->
                configuration.attributes(compilation::resolvableAttributes)

                configuration.attributes.attribute(
                    MinecraftOperatingSystemAttribute.attribute,
                    objects.named(
                        MinecraftOperatingSystemAttribute::class.java,
                        operatingSystemName(),
                    ),
                )
            }
        }

        for (name in configurationNames) {
            configurations.findByName(name)?.attributes(compilation::attributes)
        }

        if (testName != null) {
            val sourceSetName = sourceSetName(target, testName)

            project.extension<SourceSetContainer>().named { it == sourceSetName }.configureEach {
                for (name in listOf(it.compileClasspathConfigurationName, it.runtimeClasspathConfigurationName)) {
                    project.configurations.named(name) {
                        it.attributes(compilation::attributes)
                        it.attributes(compilation::resolvableAttributes)
                    }
                }
            }
        }
    }

    addCompilation(target.main, SourceSet.TEST_SOURCE_SET_NAME)

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
            addCompilation(client, "${ClochePlugin.CLIENT_COMPILATION_NAME}:${SourceSet.TEST_SOURCE_SET_NAME}")
            client.addClasspathDependency(target.main)

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

    tasks.named(target.sourceSet.downloadAssetsTaskName, DownloadAssets::class.java) {
        it.minecraftVersion.set(target.minecraftVersion)
    }

    tasks.named(target.sourceSet.extractNativesTaskName, ExtractNatives::class.java) {
        it.minecraftVersion.set(target.minecraftVersion)
    }

    configurations.named(target.sourceSet.runtimeElementsConfigurationName) { configuration ->
        val variant = configuration.outgoing.variants.create("includeTransformed") {
            it.attributes
                .attribute(INCLUDE_TRANSFORMED_OUTPUT_ATTRIBUTE, true)
                .attribute(CompilationAttributes.SIDE, PublicationSide.Joined)

            it.artifact(target.finalJar)
        }

        components.named("java") {
            it as AdhocComponentWithVariants

            it.withVariantsFromConfiguration(configuration) {
                if (it.configurationVariant.name == variant.name) {
                    it.skip()
                }
            }
        }
    }

    artifacts.add(Dependency.ARCHIVES_CONFIGURATION, target.finalJar)
}
