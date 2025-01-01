package earth.terrarium.cloche

import earth.terrarium.cloche.target.*
import net.msrandom.minecraftcodev.core.VERSION_MANIFEST_URL
import net.msrandom.minecraftcodev.core.getVersionList
import net.msrandom.minecraftcodev.core.task.CachedMinecraftParameters
import net.msrandom.minecraftcodev.core.task.convention
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.getGlobalCacheDirectory
import net.msrandom.minecraftcodev.includes.ExtractIncludes
import net.msrandom.minecraftcodev.remapper.RemapAction
import net.msrandom.minecraftcodev.remapper.mappingsConfigurationName
import net.msrandom.minecraftcodev.runs.RunsContainer
import org.gradle.api.Project
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.internal.extensions.core.serviceOf
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.nativeplatform.OperatingSystemFamily
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
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

    return serviceOf<JavaToolchainService>().launcherFor {
        it.languageVersion.set(javaVersion)
    }.map { it.executablePath }
}

private fun setupModTransformationPipeline(
    project: Project,
    target: MinecraftTargetInternal,
    remapNamespace: Provider<String>,
    main: SourceSet,
    intermediaryMinecraft: Provider<FileSystemLocation>,
) {
    project.dependencies.registerTransform(ExtractIncludes::class.java) {
        it.from.attribute(ModTransformationStateAttribute.ATTRIBUTE, ModTransformationStateAttribute.INITIAL)
        it.to.attribute(
            ModTransformationStateAttribute.ATTRIBUTE,
            ModTransformationStateAttribute.of(target, States.INCLUDES_EXTRACTED)
        )
    }

    // afterEvaluate needed as the registration of a transform is dependent on a lazy provider
    //  this can potentially be changed to a no-op transform but that's far slower
    project.afterEvaluate {
        if (remapNamespace.get().isEmpty()) {
            return@afterEvaluate
        }

        project.dependencies.registerTransform(RemapAction::class.java) {
            it.from.attribute(
                ModTransformationStateAttribute.ATTRIBUTE,
                ModTransformationStateAttribute.of(target, States.INCLUDES_EXTRACTED)
            )
            it.to.attribute(
                ModTransformationStateAttribute.ATTRIBUTE,
                ModTransformationStateAttribute.of(target, States.REMAPPED)
            )

            it.parameters {
                it.mappings.from(project.configurations.named(main.mappingsConfigurationName))

                it.sourceNamespace.set(remapNamespace)

                it.extraClasspath.from(project.files(intermediaryMinecraft))

                it.cacheParameters.convention(project)
                it.javaExecutable.set(project.javaExecutableFor(target.minecraftVersion, it.cacheParameters))
            }
        }
    }
}

context(Project)
internal fun handleTarget(target: MinecraftTargetInternal, singleTarget: Boolean) {
    fun add(compilation: TargetCompilation) {
        val sourceSet = compilation.sourceSet

        createCompilationVariants(compilation, sourceSet, true)

        configureSourceSet(sourceSet, target, compilation, singleTarget)

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
            val javaCompiler = javaVersion.flatMap { version ->
                project.serviceOf<JavaToolchainService>().compilerFor {
                    it.languageVersion.set(JavaLanguageVersion.of(version))
                }
            }

            compile.javaCompiler.set(javaCompiler)
        }

        plugins.withId("org.jetbrains.kotlin.jvm") {
            tasks.named(sourceSet.getCompileTaskName("kotlin"), KotlinCompile::class.java) {
                val javaLauncher = javaVersion.flatMap { version ->
                    serviceOf<JavaToolchainService>().launcherFor {
                        it.languageVersion.set(JavaLanguageVersion.of(version))
                    }
                }

                it.kotlinJavaToolchain.toolchain.use(javaLauncher)
            }
        }

        val main = if (compilation.name == SourceSet.MAIN_SOURCE_SET_NAME) {
            compilation
        } else {
            compilation.linkDynamically(target.main)

            target.main
        }

        setupModTransformationPipeline(
            project,
            target,
            target.remapNamespace,
            target.main.sourceSet,
            compilation.intermediaryMinecraftFile,
        )

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
                    OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE,
                    objects.named(
                        OperatingSystemFamily::class.java,
                        DefaultNativePlatform.host().operatingSystem.toFamilyName()
                    ),
                )
            }
        }

        for (name in configurationNames) {
            configurations.findByName(name)?.attributes(compilation::attributes)
        }

        val dependencyHandler = ClocheDependencyHandler(project, sourceSet)

        compilation.dependencySetupActions.all {
            it.execute(dependencyHandler)
        }
    }

    fun addRunnable(runnable: Runnable) {
        runnable as RunnableInternal

        val runnableName = if (runnable.name == SourceSet.MAIN_SOURCE_SET_NAME) {
            "server"
        } else {
            runnable.name
        }

        extension<RunsContainer>()
            .create(target.name + TARGET_NAME_PATH_SEPARATOR + runnableName) { builder ->
                runnable.runSetupActions.all {
                    it.execute(builder)
                }
            }
    }

    target.compilations.all(::add)
    target.runnables.all(::addRunnable)
}
