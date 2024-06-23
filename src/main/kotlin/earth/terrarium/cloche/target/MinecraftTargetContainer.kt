package earth.terrarium.cloche.target

import org.gradle.api.Project
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.DefaultPolymorphicDomainObjectContainer
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.instantiation.InstantiatorFactory
import javax.inject.Inject
import kotlin.reflect.KClass

open class MinecraftTargetContainer @Inject constructor(
    project: Project,
    instantiatorFactory: InstantiatorFactory,
    collectionCallbackActionDecorator: CollectionCallbackActionDecorator
) : DefaultPolymorphicDomainObjectContainer<MinecraftTarget>(
    MinecraftTarget::class.java,
    instantiatorFactory.decorateLenient((project as ProjectInternal).services),
    collectionCallbackActionDecorator
) {
    init {
        apply {
            addTargetType<FabricTarget>()
            addTargetType<ForgeTarget>()
            addTargetType<NeoForgeTarget>()
            addTargetType<QuiltTarget>()
        }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun <T : MinecraftTarget> addTargetType(type: Class<T>) = registerBinding(type, type)

    @JvmSynthetic
    fun <T : MinecraftTarget> addTargetType(type: KClass<T>) = addTargetType(type.java)

    @JvmSynthetic
    inline fun <reified T : MinecraftTarget> addTargetType() = addTargetType(T::class)
}
