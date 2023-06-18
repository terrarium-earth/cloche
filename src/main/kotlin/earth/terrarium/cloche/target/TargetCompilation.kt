package earth.terrarium.cloche.target

import earth.terrarium.cloche.ClocheDependencyHandler
import net.msrandom.minecraftcodev.accesswidener.dependency.accessWidenersConfigurationName
import net.msrandom.minecraftcodev.forge.dependency.patchesConfigurationName
import net.msrandom.minecraftcodev.mixins.dependency.mixinsConfigurationName
import net.msrandom.minecraftcodev.remapper.dependency.mappingsConfigurationName
import org.apache.commons.lang3.StringUtils
import org.gradle.api.Action
import org.gradle.api.tasks.SourceSet
import org.jetbrains.kotlin.gradle.plugin.HasKotlinDependencies
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmCompilation
import javax.inject.Inject

abstract class TargetCompilation @Inject constructor(private val name: String) : Compilation {
    val sourceSetName: String
        get() {
            validate()

            return sourceSet?.name ?: kotlinSourceSet?.name ?: kotlinCompilation!!.defaultSourceSetName
        }

    private var sourceSet: SourceSet? = null
    private var kotlinSourceSet: KotlinSourceSet? = null
    private var kotlinCompilation: KotlinJvmCompilation? = null

    val mappingsConfigurationName get() = map(SourceSet::mappingsConfigurationName, HasKotlinDependencies::mappingsConfigurationName)
    val accessWidenersConfigurationName get() = map(SourceSet::accessWidenersConfigurationName, HasKotlinDependencies::accessWidenersConfigurationName)
    val mixinsConfigurationName get() = map(SourceSet::mixinsConfigurationName, HasKotlinDependencies::mixinsConfigurationName)
    val patchesConfigurationName get() = map(SourceSet::patchesConfigurationName, HasKotlinDependencies::patchesConfigurationName)

    val dependencySetupActions = mutableListOf<Action<ClocheDependencyHandler>>()

    internal fun process(sourceSet: SourceSet?, kotlinSourceSet: KotlinSourceSet?, kotlinCompilation: KotlinJvmCompilation?) {
        this.sourceSet = sourceSet
        this.kotlinSourceSet = kotlinSourceSet
        this.kotlinCompilation = kotlinCompilation

        validate()
    }

    private fun validate() = require(listOfNotNull(sourceSet, kotlinSourceSet, kotlinCompilation).size == 1) { "Either sourceSet or kotlinSourceSet or kotlinCompilation or has to be specified." }

    override fun dependencies(action: Action<ClocheDependencyHandler>) {
        dependencySetupActions.add(action)
    }

    override fun getName() = name

    private fun <T> map(sourceSetMapper: SourceSet.() -> T, kotlinMapper: HasKotlinDependencies.() -> T): T {
        validate()

        sourceSet?.let {
            return it.sourceSetMapper()
        }

        kotlinSourceSet?.let {
            return it.kotlinMapper()
        }

        return kotlinCompilation!!.kotlinMapper()
    }
}
