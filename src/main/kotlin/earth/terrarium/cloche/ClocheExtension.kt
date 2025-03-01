package earth.terrarium.cloche

import earth.terrarium.cloche.api.MappingsBuilder
import earth.terrarium.cloche.api.metadata.ModMetadata
import earth.terrarium.cloche.api.target.CommonTarget
import earth.terrarium.cloche.api.target.FabricTarget
import earth.terrarium.cloche.api.target.ForgeTarget
import earth.terrarium.cloche.api.target.MinecraftTarget
import earth.terrarium.cloche.api.target.NeoforgeTarget
import earth.terrarium.cloche.target.CommonTargetInternal
import earth.terrarium.cloche.target.MinecraftTargetInternal
import earth.terrarium.cloche.target.fabric.FabricTargetImpl
import earth.terrarium.cloche.target.forge.lex.ForgeTargetImpl
import earth.terrarium.cloche.target.forge.neo.NeoForgeTargetImpl
import groovy.lang.Closure
import groovy.lang.DelegatesTo
import net.msrandom.minecraftcodev.fabric.FabricInstallerComponentMetadataRule
import org.gradle.api.Action
import org.gradle.api.DomainObjectCollection
import org.gradle.api.PolymorphicDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

internal const val FORGE = "forge"
internal const val FABRIC = "fabric"
internal const val NEOFORGE = "neoforge"
internal const val COMMON = "common"

@JvmDefaultWithoutCompatibility
interface TargetContainer {
    fun fabric(): FabricTarget = fabric(FABRIC)
    fun fabric(name: String): FabricTarget = fabric(name) {}
    fun fabric(@DelegatesTo(FabricTarget::class) configure: Closure<*>): FabricTarget = fabric(FABRIC, configure)
    fun fabric(configure: Action<FabricTarget>): FabricTarget = fabric(FABRIC, configure)
    fun fabric(name: String, @DelegatesTo(FabricTarget::class)  configure: Closure<*>): FabricTarget = fabric(name) {
        configure.rehydrate(it, this, this).call()
    }
    fun fabric(name: String, configure: Action<FabricTarget>): FabricTarget

    fun forge(): ForgeTarget = forge(FORGE)
    fun forge(name: String): ForgeTarget = forge(name) {}
    fun forge(@DelegatesTo(ForgeTarget::class) configure: Closure<*>): ForgeTarget = forge(FORGE, configure)
    fun forge(configure: Action<ForgeTarget>): ForgeTarget = forge(FORGE, configure)
    fun forge(name: String, @DelegatesTo(ForgeTarget::class) configure: Closure<*>): ForgeTarget = forge(name) {
        configure.rehydrate(it, this, this).call()
    }
    fun forge(name: String, configure: Action<ForgeTarget>): ForgeTarget

    fun neoforge(): NeoforgeTarget = neoforge(NEOFORGE)
    fun neoforge(name: String): NeoforgeTarget = neoforge(name) {}
    fun neoforge(@DelegatesTo(NeoforgeTarget::class) configure: Closure<*>): NeoforgeTarget = neoforge(NEOFORGE, configure)
    fun neoforge(configure: Action<NeoforgeTarget>): NeoforgeTarget = neoforge(NEOFORGE, configure)
    fun neoforge(name: String, @DelegatesTo(NeoforgeTarget::class) configure: Closure<*>): NeoforgeTarget = neoforge(name) {
        configure.rehydrate(it, this, this).call()
    }
    fun neoforge(name: String, configure: Action<NeoforgeTarget>): NeoforgeTarget
}

class SingleTargetConfigurator(private val project: Project, private val extension: ClocheExtension) : TargetContainer {
    internal var target: MinecraftTarget<*>? = null

    override fun fabric(name: String, configure: Action<FabricTarget>): FabricTarget {
        project.dependencies.components {
            it.withModule("net.fabricmc:fabric-loader", FabricInstallerComponentMetadataRule::class.java) {
                it.params(SIDE_ATTRIBUTE, PublicationSide.Common, PublicationSide.Client, false)
            }
        }

        return target(name, FabricTargetImpl::class.java, configure)
    }

    override fun forge(name: String, configure: Action<ForgeTarget>) = target(name, ForgeTargetImpl::class.java, configure)
    override fun neoforge(name: String, configure: Action<NeoforgeTarget>) = target(name, NeoForgeTargetImpl::class.java, configure)

    private fun <T : MinecraftTarget<*>> target(name: String, type: Class<out T>, configure: Action<T>): T {
        extension.targets.all {
            throw UnsupportedOperationException("Target ${it.name} has been configured. Can not set single target to $name")
        }

        extension.commonTargets.all {
            throw UnsupportedOperationException("Common target ${it.name} has been configured. Can not use single target mode with target $name")
        }

        target?.let {
            if (it.name != name) {
                throw UnsupportedOperationException("Single target already set as ${it.name}. Can not set single target to $name")
            } else if (it.javaClass != type) {
                throw UnsupportedOperationException("Single target with name $name is set as type ${it.javaClass}, but was queried as $type")
            }

            @Suppress("UNCHECKED_CAST")
            configure.execute(it as T)

            return it
        }

        val instance = project.objects.newInstance(type, name)

        (instance as MinecraftTargetInternal<*>).initialize(true)

        addTarget(extension, project, instance, true)

        return instance.also(configure::execute).also {
            target = it
        }
    }
}

open class ClocheExtension @Inject constructor(private val project: Project, objects: ObjectFactory) : TargetContainer {
    val minecraftVersion: Property<String> = objects.property(String::class.java)

    val commonTargets = objects.domainObjectContainer(CommonTarget::class.java) {
        objects.newInstance(CommonTargetInternal::class.java, it)
    }

    val targets: PolymorphicDomainObjectContainer<MinecraftTarget<*>> = objects.polymorphicDomainObjectContainer(MinecraftTarget::class.java).apply {
        registerFactory(FabricTarget::class.java) {
            objects.newInstance(FabricTargetImpl::class.java, it)
        }

        registerFactory(ForgeTarget::class.java) {
            objects.newInstance(ForgeTargetImpl::class.java, it)
        }

        registerFactory(NeoforgeTarget::class.java) {
            objects.newInstance(NeoForgeTargetImpl::class.java, it)
        }
    }

    val metadata: ModMetadata = objects.newInstance(ModMetadata::class.java)

    internal val singleTargetConfigurator = SingleTargetConfigurator(project, this)

    internal val mappingActions = project.objects.domainObjectSet(Action::class.java) as DomainObjectCollection<Action<MappingsBuilder>>

    init {
        var fabricConfigured = false

        targets.withType(FabricTarget::class.java).whenObjectAdded {
            if (!fabricConfigured) {
                fabricConfigured = true

                project.dependencies.components {
                    it.withModule("net.fabricmc:fabric-loader", FabricInstallerComponentMetadataRule::class.java) {
                        it.params(SIDE_ATTRIBUTE, PublicationSide.Common, PublicationSide.Client, false)
                    }
                }
            }
        }

        targets.all {
            it.dependsOn(common())
        }

        commonTargets.all {
            if (it.name != COMMON) {
                it.dependsOn(common())
            }
        }
    }

    fun common(): CommonTarget = common(COMMON)
    fun common(name: String): CommonTarget = common(name) {}
    fun common(@DelegatesTo(CommonTarget::class) configure: Closure<*>): CommonTarget = common(COMMON, configure)
    fun common(configure: Action<CommonTarget>): CommonTarget = common(COMMON, configure)
    fun common(name: String, @DelegatesTo(CommonTarget::class) configure: Closure<*>): CommonTarget = common(name) {
        configure.rehydrate(it, this, this).call()
    }

    fun common(name: String, configure: Action<CommonTarget>): CommonTarget =
        commonTargets.maybeCreate(name).also(configure::execute)

    fun <T : MinecraftTarget<*>> singleTarget(configurator: SingleTargetConfigurator.() -> T) = singleTargetConfigurator.configurator()

    override fun fabric(name: String, configure: Action<FabricTarget>): FabricTarget = target(name, configure)

    override fun forge(name: String, configure: Action<ForgeTarget>): ForgeTarget = target(name, configure)

    override fun neoforge(name: String, configure: Action<NeoforgeTarget>): NeoforgeTarget = target(name, configure)

    private fun <T : MinecraftTarget<*>> target(name: String, type: Class<T>, configure: Action<in T>) =
        targets.maybeCreate(name, type).also(configure::execute)

    private inline fun <reified T : MinecraftTarget<*>> target(name: String, configure: Action<in T>) =
        target(name, T::class.java, configure)

    fun mappings(action: Action<MappingsBuilder>) {
        mappingActions.add(action)
    }

    fun metadata(action: Action<ModMetadata>) {
        action.execute(metadata)
    }
}
