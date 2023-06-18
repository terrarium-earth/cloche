package earth.terrarium.cloche

import earth.terrarium.cloche.target.*
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject
import kotlin.reflect.KClass

open class ClocheExtension @Inject constructor(project: Project, objects: ObjectFactory) {
    val minecraftVersion: Property<String> = objects.property(String::class.java)
    val useKotlinMultiplatform: Property<Boolean> = objects.property(Boolean::class.java)

    val commonTargets = objects.domainObjectContainer(CommonTarget::class.java)
    val targets: MinecraftTargetContainer = objects.newInstance(MinecraftTargetContainer::class.java)

    internal val mappingActions = mutableListOf<Action<MappingsBuilder>>()

    @JvmOverloads
    fun common(name: String = ::common.name, configure: Action<CommonTarget>? = null) = commonTargets.findByName(name)
        ?.also { configure?.execute(it) }
        ?: configure?.let { commonTargets.create(name, it) }
        ?: commonTargets.create(name)

    @JvmOverloads
    fun fabric(name: String = ::fabric.name, configure: Action<FabricTarget>? = null) = target<FabricTarget>(name, configure)

    @JvmOverloads
    fun forge(name: String = ::forge.name, configure: Action<ForgeTarget>? = null) = target<ForgeTarget>(name, configure)

    @JvmOverloads
    fun quilt(name: String = ::quilt.name, configure: Action<QuiltTarget>? = null) = target<QuiltTarget>(name, configure)

    fun <T : MinecraftTarget> target(name: String, type: Class<T>, configure: Action<T>? = null) =
        targets.withType(type)
            .findByName(name)
            ?.also { configure?.execute(it) }
            ?: configure?.let { targets.create(name, type, it) }
            ?: targets.create(name, type)

    @JvmSynthetic
    fun <T : MinecraftTarget> target(name: String, type: KClass<T>, configure: Action<T>? = null) = target(name, type.java, configure)

    inline fun <reified T : MinecraftTarget> target(name: String, configure: Action<T>? = null) = target(name, T::class, configure)

    fun mappings(action: Action<MappingsBuilder>) {
        mappingActions.add(action)
    }
}
