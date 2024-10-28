package earth.terrarium.cloche

import earth.terrarium.cloche.metadata.ModMetadata
import earth.terrarium.cloche.target.*
import net.msrandom.minecraftcodev.fabric.FabricInstallerComponentMetadataRule
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject
import kotlin.properties.Delegates
import kotlin.reflect.KClass

open class ClocheExtension @Inject constructor(private val project: Project, objects: ObjectFactory) {
    val minecraftVersion: Property<String> = objects.property(String::class.java)

    private val _commonTargets = objects.domainObjectContainer(CommonTargetInternal::class.java)

    @Suppress("UNCHECKED_CAST")
    val commonTargets: NamedDomainObjectCollection<CommonTarget>
        get() = _commonTargets as NamedDomainObjectCollection<CommonTarget>

    val targets: MinecraftTargetContainer = objects.newInstance(MinecraftTargetContainer::class.java)

    val metadata: ModMetadata = objects.newInstance(ModMetadata::class.java)

    internal var isSingleTargetMode by Delegates.notNull<Boolean>()

    internal val mappingActions = mutableListOf<Action<MappingsBuilder>>()

    private val usedTargetTypes = hashSetOf<Class<out MinecraftTarget>>()

    @JvmOverloads
    fun common(name: String = ::common.name, configure: Action<CommonTarget>? = null): CommonTarget = _commonTargets.findByName(name)
        ?.also { configure?.execute(it) }
        ?: configure?.let { _commonTargets.create(name, it) }
        ?: _commonTargets.create(name)

    @JvmOverloads
    fun fabric(name: String = ::fabric.name, configure: Action<MinecraftClientTarget>? = null): MinecraftClientTarget = target<FabricTarget>(name, {
        project.dependencies.components {
            it.withModule("net.fabricmc:fabric-loader", FabricInstallerComponentMetadataRule::class.java) {
                it.params(VARIANT_ATTRIBUTE, PublicationVariant.Common, PublicationVariant.Client, false)
            }
        }
    }, configure)

    @JvmOverloads
    fun forge(name: String = ::forge.name, configure: Action<MinecraftNoClientTarget>? = null): MinecraftNoClientTarget = target<ForgeTarget>(name, {
    }, configure)

    @JvmOverloads
    fun neoforge(name: String = ::neoforge.name, configure: Action<MinecraftNoClientTarget>? = null): MinecraftNoClientTarget = target<NeoForgeTarget>(name, {
    }, configure)

    private fun <T : MinecraftTarget> target(name: String, type: Class<T>, setupTargetType: () -> Unit = {}, configure: Action<in T>? = null): T {
        if (usedTargetTypes.add(type)) {
            setupTargetType()
        }

        return targets.withType(type)
            .findByName(name)
            ?.also { configure?.execute(it) }
            ?: configure?.let { targets.create(name, type, it) }
            ?: targets.create(name, type)
    }

    @JvmSynthetic
    fun <T : MinecraftTarget> target(name: String, type: KClass<T>, setupTargetType: () -> Unit = {}, configure: Action<in T>? = null) = target(name, type.java, setupTargetType)

    private inline fun <reified T : MinecraftTarget> target(name: String, noinline setupTargetType: () -> Unit = {}, configure: Action<in T>? = null) =
        target(name, T::class.java, setupTargetType, configure)

    fun mappings(action: Action<MappingsBuilder>) {
        mappingActions.add(action)
    }

    fun metadata(action: Action<ModMetadata>) {
        action.execute(metadata)
    }
}
