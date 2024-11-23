package earth.terrarium.cloche

import earth.terrarium.cloche.metadata.ModMetadata
import earth.terrarium.cloche.target.*
import groovy.lang.Closure
import groovy.lang.DelegatesTo
import net.msrandom.minecraftcodev.fabric.FabricInstallerComponentMetadataRule
import org.gradle.api.Action
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
    fun fabric(name: String, @DelegatesTo(FabricTarget::class) configure: Closure<*>): FabricTarget = fabric(name, configure::call)
    fun fabric(name: String, configure: Action<FabricTarget>): FabricTarget

    fun forge(): ForgeTarget = forge(FORGE)
    fun forge(name: String): ForgeTarget = forge(name) {}
    fun forge(@DelegatesTo(ForgeTarget::class) configure: Closure<*>): ForgeTarget = forge(FORGE, configure)
    fun forge(configure: Action<ForgeTarget>): ForgeTarget = forge(FORGE, configure)
    fun forge(name: String, @DelegatesTo(ForgeTarget::class) configure: Closure<*>): ForgeTarget = forge(name, configure::call)
    fun forge(name: String, configure: Action<ForgeTarget>): ForgeTarget

    fun neoforge(): NeoforgeTarget = neoforge(NEOFORGE)
    fun neoforge(name: String): NeoforgeTarget = neoforge(name) {}
    fun neoforge(@DelegatesTo(NeoforgeTarget::class) configure: Closure<*>): NeoforgeTarget = neoforge(NEOFORGE, configure)
    fun neoforge(configure: Action<NeoforgeTarget>): NeoforgeTarget = neoforge(NEOFORGE, configure)
    fun neoforge(name: String, @DelegatesTo(NeoforgeTarget::class) configure: Closure<*>): NeoforgeTarget = neoforge(name, configure::call)
    fun neoforge(name: String, configure: Action<NeoforgeTarget>): NeoforgeTarget
}

class SingleTargetConfigurator(private val project: Project, private val extension: ClocheExtension) : TargetContainer {
    internal var target: MinecraftTarget? = null

    override fun fabric(name: String, configure: Action<FabricTarget>): FabricTarget {
        project.dependencies.components {
            it.withModule("net.fabricmc:fabric-loader", FabricInstallerComponentMetadataRule::class.java) {
                it.params(VARIANT_ATTRIBUTE, PublicationVariant.Common, PublicationVariant.Client, false)
            }
        }

        return target(name, FabricTargetImpl::class.java, configure)
    }

    override fun forge(name: String, configure: Action<ForgeTarget>) = target(name, ForgeTargetImpl::class.java, configure)
    override fun neoforge(name: String, configure: Action<NeoforgeTarget>) = target(name, NeoForgeTargetImpl::class.java, configure)

    private fun <T : MinecraftTarget> target(name: String, type: Class<out T>, configure: Action<T>): T {
        if (extension.targets.isNotEmpty()) {
            throw UnsupportedOperationException("A target or multiple targets have already been configured. Can not set single target to $name")
        }

        if (extension.commonTargets.isNotEmpty()) {
            throw UnsupportedOperationException("A common target has been configured. Can not use single target mode with target $name")
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

        (instance as MinecraftTargetInternal).initialize(true)

        addTarget(extension, project, instance, true)

        return instance.also(configure::execute).also {
            target = it
        }
    }
}

open class ClocheExtension @Inject constructor(private val project: Project, objects: ObjectFactory) : TargetContainer {
    val minecraftVersion: Property<String> = objects.property(String::class.java)

    internal val commonTargets = objects.domainObjectContainer(CommonTarget::class.java) {
        objects.newInstance(CommonTargetInternal::class.java, it)
    }

    internal val targets: PolymorphicDomainObjectContainer<MinecraftTarget> = objects.polymorphicDomainObjectContainer(MinecraftTarget::class.java).apply {
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

    internal val mappingActions = mutableListOf<Action<MappingsBuilder>>()

    private val usedTargetTypes = hashSetOf<Class<out MinecraftTarget>>()

    fun common(): CommonTarget = common(COMMON)
    fun common(name: String): CommonTarget = common(name) {}
    fun common(@DelegatesTo(CommonTarget::class) configure: Closure<*>): CommonTarget = common(NEOFORGE, configure)
    fun common(configure: Action<CommonTarget>): CommonTarget = common(NEOFORGE, configure)
    fun common(name: String, @DelegatesTo(CommonTarget::class) configure: Closure<*>): CommonTarget = common(name, configure::call)

    fun common(name: String, configure: Action<CommonTarget>): CommonTarget {
        singleTargetConfigurator.target?.let {
            throw UnsupportedOperationException("Can not create common target $name, single target already configured as ${it.name}")
        }

        val target = commonTargets.findByName(name)
            ?.also(configure::execute)
            ?: commonTargets.create(name, configure)

        if (name != COMMON) {
            target.dependsOn(common())
        }

        return target
    }

    fun <T : MinecraftTarget> singleTarget(configurator: SingleTargetConfigurator.() -> T) = singleTargetConfigurator.configurator()

    override fun fabric(name: String, configure: Action<FabricTarget>): FabricTarget = target(name, {
        project.dependencies.components {
            it.withModule("net.fabricmc:fabric-loader", FabricInstallerComponentMetadataRule::class.java) {
                it.params(VARIANT_ATTRIBUTE, PublicationVariant.Common, PublicationVariant.Client, false)
            }
        }
    }, configure)

    override fun forge(name: String, configure: Action<ForgeTarget>): ForgeTarget = target(name, {}, configure)

    override fun neoforge(name: String, configure: Action<NeoforgeTarget>): NeoforgeTarget = target(name, {}, configure)

    private fun <T : MinecraftTarget> target(name: String, type: Class<T>, setupTargetType: () -> Unit = {}, configure: Action<in T>): T {
        singleTargetConfigurator.target?.let {
            throw UnsupportedOperationException("Can not create new target $name, single target already configured as ${it.name}")
        }

        if (usedTargetTypes.add(type)) {
            setupTargetType()
        }

        val common = common()

        return targets.withType(type)
            .findByName(name)
            ?.also(configure::execute)
            ?: targets.create(name, type) {
                configure.execute(it)

                it.dependsOn(common)
            }
    }

    private inline fun <reified T : MinecraftTarget> target(name: String, noinline setupTargetType: () -> Unit = {}, configure: Action<in T>) =
        target(name, T::class.java, setupTargetType, configure)

    fun mappings(action: Action<MappingsBuilder>) {
        mappingActions.add(action)
    }

    fun metadata(action: Action<ModMetadata>) {
        action.execute(metadata)
    }
}
