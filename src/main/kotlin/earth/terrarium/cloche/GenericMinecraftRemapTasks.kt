package earth.terrarium.cloche

import earth.terrarium.cloche.api.attributes.RemapNamespaceAttribute
import earth.terrarium.cloche.api.target.FabricTarget
import earth.terrarium.cloche.api.target.ForgeLikeTarget
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
import javax.inject.Inject
import kotlin.reflect.KClass

private typealias Classpath = FileCollection

private typealias TaskInput = Provider<RegularFile>

private typealias TargetMinecraftProvider = MapProperty<String, TaskInput>

private typealias Namespace = String

internal abstract class DefinedMinecraftProviders {
    abstract val project: Project
        @Inject get

    protected val objects
        get() = project.objects

    @Suppress("UNCHECKED_CAST")
    val providers =
        objects.mapProperty(
            KClass::class.java as Class<KClass<out MinecraftTarget>>,
            MapProperty::class.java as Class<MapProperty<String, (MinecraftTarget) -> TargetMinecraftProvider>>
        ).convention(mutableMapOf())

    @Suppress("UNCHECKED_CAST")
    val classpaths =
        objects.mapProperty(
            KClass::class.java as Class<KClass<out MinecraftTarget>>,
            MapProperty::class.java as Class<MapProperty<String, (MinecraftTarget, String) -> FileCollection>>
        ).convention(mutableMapOf())

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

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T : MinecraftTarget> registerClasspath(
        namespace: Namespace,
        noinline provider: (T, String) -> Classpath
    ) {
        val currentMap = classpaths.get()[T::class] ?: run {
            val newMap = objects.mapProperty(
                String::class.java,
                Object::class.java as Class<(MinecraftTarget, String) -> FileCollection>
            )
            newMap.convention(mutableMapOf())
            classpaths.put(T::class, newMap)
            newMap
        }
        currentMap.put(namespace, provider as (MinecraftTarget, String) -> Classpath)
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T : MinecraftTarget> registerProvider(
        namespace: Namespace,
        noinline provider: (T) -> Map<String, TaskInput>
    ) {
        val currentMap = providers.get()[T::class] ?: run {
            val newMap = objects.mapProperty(
                String::class.java,
                java.lang.Object::class.java as Class<(MinecraftTarget) -> MapProperty<String, TaskInput>>
            )
            newMap.convention(mutableMapOf())
            providers.put(T::class, newMap)
            newMap
        }

        currentMap.put(namespace) {
            objects.mapProperty(String::class.java, Provider::class.java as Class<TaskInput>).value(provider(it as T))
        }
    }

    private fun getProvider(target: MinecraftTarget): MapProperty<Namespace, (MinecraftTarget) -> TargetMinecraftProvider>? =
        providers.get()[target::class]
            ?: providers.get().entries.firstOrNull { (key, _) -> key.isInstance(target) }?.value

    fun getProvider(target: MinecraftTarget, namespace: Namespace): TargetMinecraftProvider? =
        getProvider(target)?.get()?.get(namespace)?.invoke(target)

    internal fun getClasspath(target: MinecraftTarget): MapProperty<Namespace, (MinecraftTarget, String) -> Classpath> {
        val map = objects.mapProperty(
            String::class.java,
            java.lang.Object::class.java as Class<(MinecraftTarget, String) -> FileCollection>
        )
        map.convention(mutableMapOf())
        return classpaths.get()[target::class] ?: map
    }

    fun getClasspath(target: MinecraftTarget, namespace: Namespace): Classpath =
        getClasspath(target).get()[namespace]?.invoke(target, namespace) ?: project.files()
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
        return project.files(provider.map { it.values })
    }

    val intermediaryNamespace = target.minecraftRemapNamespace.get().takeUnless { it.isEmpty() } ?: when (target) {
        is FabricTarget -> RemapNamespaceAttribute.INTERMEDIARY
        is ForgeLikeTarget -> RemapNamespaceAttribute.SEARGE
        else -> error("No intermediary namespace found for $this")
    }
    val intermediaryJarProvider =
        providers.getProvider(this, intermediaryNamespace) ?: error("No intermediary provider found for $this")
    val intermediaryJars = objectFactory.listProperty(RegularFile::class.java)

    intermediaryJarProvider.map {
        it.forEach { (variant, input) ->
            val task = project.tasks.maybeRegister<RemapTask>(
                lowerCamelCaseGradleName(
                    "remap",
                    featureName,
                    variant,
                    "minecraft",
                    namespace
                )
            ) {
                group = "minecraft-transforms"

                inputFile.set(input)
                sourceNamespace.set(intermediaryNamespace)
                targetNamespace.set(namespace)

                classpath.from(providers.getClasspath(this@getRemappedMinecraftByNamespace, namespace))

                mappings.set(loadMappingsTask.flatMap { it.output })

                outputFile.set(outputDirectory.zip(minecraftVersion) { dir, version ->
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
            }

            project.tasks.named(ClochePlugin.IDE_SYNC_TASK_NAME) {
                dependsOn(task)
            }

            intermediaryJars.add(task.flatMap { it.outputFile })
        }
    }

    return project.files(intermediaryJars)
}