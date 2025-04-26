package earth.terrarium.cloche

import earth.terrarium.cloche.target.MinecraftTargetInternal
import earth.terrarium.cloche.target.TargetCompilation
import earth.terrarium.cloche.target.configureSourceSet
import earth.terrarium.cloche.target.fabric.FabricTargetImpl
import earth.terrarium.cloche.target.modConfigurationName
import net.msrandom.minecraftcodev.core.MinecraftOperatingSystemAttribute
import net.msrandom.minecraftcodev.core.VERSION_MANIFEST_URL
import net.msrandom.minecraftcodev.core.getVersionList
import net.msrandom.minecraftcodev.core.operatingSystemName
import net.msrandom.minecraftcodev.core.task.CachedMinecraftParameters
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.getGlobalCacheDirectory
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.mixins.mixinsConfigurationName
import net.msrandom.minecraftcodev.runs.downloadAssetsTaskName
import net.msrandom.minecraftcodev.runs.extractNativesTaskName
import net.msrandom.minecraftcodev.runs.task.DownloadAssets
import net.msrandom.minecraftcodev.runs.task.ExtractNatives
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

fun Project.javaExecutableFor(version: Provider<String>, cacheParameters: CachedMinecraftParameters): Provider<RegularFile> {
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

context(Project)
internal fun handleTarget(target: MinecraftTargetInternal, singleTarget: Boolean) {
    fun add(compilation: TargetCompilation) {
        val sourceSet = compilation.sourceSet

        createCompilationVariants(compilation, sourceSet, true)

        configureSourceSet(sourceSet, target, compilation, singleTarget)

        project.configurations.resolvable(modConfigurationName(sourceSet.compileClasspathConfigurationName)) {
            it.shouldResolveConsistentlyWith(project.configurations.getByName(sourceSet.compileClasspathConfigurationName))

            it.extendsFrom(project.configurations.getByName(modConfigurationName(sourceSet.compileOnlyConfigurationName)))
            it.extendsFrom(project.configurations.getByName(modConfigurationName(sourceSet.compileOnlyApiConfigurationName)))
            it.extendsFrom(project.configurations.getByName(modConfigurationName(sourceSet.apiConfigurationName)))
            it.extendsFrom(project.configurations.getByName(modConfigurationName(sourceSet.implementationConfigurationName)))
        }

        project.configurations.resolvable(modConfigurationName(sourceSet.runtimeClasspathConfigurationName)) {
            it.shouldResolveConsistentlyWith(project.configurations.getByName(sourceSet.runtimeClasspathConfigurationName))

            it.extendsFrom(project.configurations.getByName(modConfigurationName(sourceSet.runtimeOnlyConfigurationName)))
            it.extendsFrom(project.configurations.getByName(modConfigurationName(sourceSet.apiConfigurationName)))
            it.extendsFrom(project.configurations.getByName(modConfigurationName(sourceSet.implementationConfigurationName)))
        }

        val copyMixins = tasks.register(lowerCamelCaseGradleName("copy", target.featureName, compilation.featureName, "mixins"), Copy::class.java) {
            it.from(configurations.named(compilation.sourceSet.mixinsConfigurationName))
            it.destinationDir = project.layout.buildDirectory.dir("mixins").get().dir(target.namePath).dir(compilation.namePath).asFile
        }

        sourceSet.resources.srcDir(copyMixins.map(Copy::getDestinationDir))

        target.registerAccessWidenerMergeTask(compilation)
        target.addJarInjects(compilation)

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

        for (name in resolvableConfigurationNames) {
            configurations.named(name) { configuration ->
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
    }

    add(target.main)

    target.data.onConfigured {
        add(it)
        it.addClasspathDependency(target.main)
    }

    target.test.onConfigured {
        add(it)
        it.addClasspathDependency(target.main)
    }

    if (target is FabricTargetImpl) {
        target.client.onConfigured { client ->
            add(client)
            client.addClasspathDependency(target.main)

            client.data.onConfigured { data ->
                add(data)
                data.addClasspathDependency(client)
                data.addClasspathDependency(target.main)

                target.data.onConfigured {
                    data.addClasspathDependency(it)
                }
            }

            client.test.onConfigured { test ->
                add(test)
                test.addClasspathDependency(client)
                test.addClasspathDependency(target.main)

                target.test.onConfigured {
                    test.addClasspathDependency(it)
                }
            }
        }
    }

    project.tasks.named(target.sourceSet.downloadAssetsTaskName, DownloadAssets::class.java) {
        it.version.set(target.minecraftVersion)
    }

    project.tasks.named(target.sourceSet.extractNativesTaskName, ExtractNatives::class.java) {
        it.version.set(target.minecraftVersion)
    }

    project.artifacts.add(Dependency.ARCHIVES_CONFIGURATION, target.includeJarTask)
}
