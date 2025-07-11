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
import org.gradle.api.provider.Provider
import javax.inject.Inject
import kotlin.reflect.KClass

private typealias Classpath = FileCollection

private typealias TaskInput = Provider<RegularFile>

private typealias TargetMinecraftProvider = Map<String, TaskInput>

private typealias Namespace = String

internal abstract class DefinedMinecraftProviders {
    val providers: MutableMap<KClass<out MinecraftTarget>, MutableMap<Namespace, (MinecraftTarget) -> TargetMinecraftProvider>> =
        hashMapOf()

    val classpaths: MutableMap<KClass<out MinecraftTarget>, MutableMap<Namespace, (MinecraftTarget, String) -> Classpath>> =
        hashMapOf()

    abstract val project: Project
        @Inject get

    init {
        registerClasspath<FabricTargetImpl>(RemapNamespaceAttribute.INTERMEDIARY) { target, variant ->
            when (variant) {
                "common" -> target.commonLibrariesConfiguration
                "client" -> project.files(
                    target.commonLibrariesConfiguration,
                    target.clientLibrariesConfiguration,
                    target.resolveCommonMinecraft.flatMap { it.output })

                else -> error("Unknown variant $variant")
            }
        }

        registerProvider<FabricTargetImpl>(RemapNamespaceAttribute.OBF) {
            mapOf(
                "common" to it.resolveCommonMinecraft.flatMap { it.output },
                "client" to it.resolveClientMinecraft.flatMap { it.output },
            )
        }

        registerProvider<FabricTargetImpl>(RemapNamespaceAttribute.INTERMEDIARY) {
            mapOf(
                "common" to it.remapCommonMinecraftIntermediary.flatMap { it.outputFile },
                "client" to it.remapClientMinecraftIntermediary.flatMap { it.outputFile },
            )
        }

        registerClasspath<ForgeLikeTargetImpl>(RemapNamespaceAttribute.SEARGE) { target, _ ->
            target.minecraftLibrariesConfiguration
        }

        registerProvider<ForgeLikeTargetImpl>(RemapNamespaceAttribute.SEARGE) {
            mapOf("" to it.resolvePatchedMinecraft.flatMap { it.output })
        }
    }

    private inline fun <reified T : MinecraftTarget> registerClasspath(
        namespace: Namespace,
        noinline provider: (T, String) -> Classpath
    ) {
        val map = classpaths.computeIfAbsent(T::class) { hashMapOf() }
        map[namespace] = provider as (MinecraftTarget, String) -> Classpath
    }

    private inline fun <reified T : MinecraftTarget> registerProvider(
        namespace: Namespace,
        noinline provider: (T) -> TargetMinecraftProvider
    ) {
        val map = providers.computeIfAbsent(T::class) { hashMapOf() }
        map[namespace] = provider as (MinecraftTarget) -> TargetMinecraftProvider
    }

    private fun getProvider(target: MinecraftTarget) =
        providers[target::class] ?: providers.firstNotNullOfOrNull { (key, value) ->
            if (key.isInstance(target)) value
            else null
        } as MutableMap<Namespace, (MinecraftTarget) -> TargetMinecraftProvider>

    fun getProvider(target: MinecraftTarget, namespace: Namespace): TargetMinecraftProvider? =
        getProvider(target)[namespace]?.invoke(target)

    private fun getClasspath(target: MinecraftTarget) = classpaths.computeIfAbsent(target::class) { hashMapOf() }

    fun getClasspath(target: MinecraftTarget, namespace: Namespace): Classpath =
        getClasspath(target)[namespace]?.invoke(target, namespace) ?: project.files()
}

internal fun MinecraftTargetInternal.getRemappedMinecraftByNamespace(
    namespace: String,
    providers: DefinedMinecraftProviders,
): FileCollection {
    if (namespace == minecraftRemapNamespace.get()) {
        return main.info.intermediaryMinecraftClasspath
    }

    val provider = providers.getProvider(this, namespace)
    if (provider != null) {
        return project.files(provider.map { it.value })
    }

    val intermediaryJarProvider = providers.getProvider(this, target.minecraftRemapNamespace.get())
        ?: error("No intermediary provider found for $this")
    val intermediaryJars = intermediaryJarProvider.map { (variant, input) ->
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

            it.inputFile.set(input)
            it.sourceNamespace.set(minecraftRemapNamespace)
            it.targetNamespace.set(namespace)

            it.classpath.from(providers.getClasspath(this, namespace))

            it.mappings.set(loadMappingsTask.flatMap { it.output })

            it.outputFile.set(outputDirectory.zip(minecraftVersion) { dir, version ->
                dir.file(buildString {
                    append("minecraft")
                    append("-")
                    append(version)
                    if (variant.isNotBlank()) {
                        append("-")
                        append(variant)
                    }
                    append("-")
                    append(namespace)
                    append(".jar")
                })
            })
        }.flatMap { it.outputFile }
    }

    return project.files(intermediaryJars)
}