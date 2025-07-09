package earth.terrarium.cloche

import earth.terrarium.cloche.api.target.MinecraftTarget
import earth.terrarium.cloche.target.MinecraftTargetInternal
import earth.terrarium.cloche.target.fabric.FabricTargetImpl
import earth.terrarium.cloche.target.forge.ForgeLikeTargetImpl
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.remapper.task.RemapTask
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Nested
import javax.inject.Inject
import kotlin.reflect.KClass

private typealias Classpath = FileCollection

private typealias TaskInput = Provider<RegularFile>

private typealias IntermediaryMinecraftProvider = Map<String, Pair<TaskInput, Classpath>>

internal abstract class IntermediaryMinecraftProviders {
    abstract val providers: MapProperty<KClass<out MinecraftTarget>, (MinecraftTarget) -> IntermediaryMinecraftProvider>
        @Nested get

    abstract val project: Project
        @Inject get

    init {
        register<FabricTargetImpl> {
            mapOf(
                "common" to
                        (it.remapCommonMinecraftIntermediary.flatMap { it.outputFile } to it.commonLibrariesConfiguration),
                "client" to
                        (it.remapClientMinecraftIntermediary.flatMap { it.outputFile } to
                                project.files(
                                    it.commonLibrariesConfiguration,
                                    it.clientLibrariesConfiguration,
                                    it.remapCommonMinecraftIntermediary.flatMap { it.outputFile })),
            )
        }

        register<ForgeLikeTargetImpl> {
            mapOf("" to (it.resolvePatchedMinecraft.flatMap { it.output } to it.minecraftLibrariesConfiguration))
        }
    }

    internal inline fun <reified T : MinecraftTarget> register(noinline provider: (T) -> IntermediaryMinecraftProvider) {
        providers.put(T::class, provider as (MinecraftTarget) -> IntermediaryMinecraftProvider)
    }

    private fun getProvider(target: MinecraftTarget) = providers.flatMap {
        // FIXME Use `flatMap` for https://github.com/gradle/gradle/issues/12388. Should be fixed after Gradle 9 published.
        project.provider {
            it[target::class] ?: it.firstNotNullOfOrNull { (key, value) ->
                if (key.isInstance(target)) value
                else null
            }
        } as Provider<(MinecraftTarget) -> IntermediaryMinecraftProvider>
    }

    operator fun get(target: MinecraftTarget): Provider<IntermediaryMinecraftProvider> =
        getProvider(target).map { it.invoke(target) }
}

internal fun MinecraftTargetInternal.getRemappedMinecraftByNamespace(
    namespace: String,
    providers: IntermediaryMinecraftProviders,
): FileCollection {
    if (namespace == minecraftRemapNamespace.get()) {
        return main.info.intermediaryMinecraftClasspath
    }

    val providers = providers[this].orNull ?: error("Unknown target type $this")

    val intermediaryJars = providers.map { (variant, input) ->
        project.tasks.maybeRegister<RemapTask>(
            lowerCamelCaseGradleName(
                "remap",
                featureName,
                variant,
                "minecraft",
                namespace
            )
        ) {
            it.group = "minecraft-transforms"

            it.inputFile.set(input.first)
            it.sourceNamespace.set(minecraftRemapNamespace)
            it.targetNamespace.set(namespace)

            it.classpath.from(input.second)

            it.mappings.set(loadMappingsTask.flatMap { it.output })

            it.outputFile.set(outputDirectory.zip(minecraftVersion) { dir, version ->
                dir.file("minecraft-$version-$variant-$namespace.jar")
            })
        }
    }

    return project.files(intermediaryJars)
}