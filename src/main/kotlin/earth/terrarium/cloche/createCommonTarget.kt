@file:Suppress("UNCHECKED_CAST")

package earth.terrarium.cloche

import earth.terrarium.cloche.target.*
import net.msrandom.minecraftcodev.core.MinecraftCodevExtension
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseName
import net.msrandom.minecraftcodev.intersection.IntersectionMinecraftCodevExtension
import net.msrandom.minecraftcodev.mixins.dependency.mixinsConfigurationName
import net.msrandom.virtualsourcesets.VirtualExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetContainer
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

private val commonSourceSet = KotlinCompile::class.memberProperties
    .first { it.name == "commonSourceSet" }
    .apply { isAccessible = true } as KProperty1<KotlinCompile, ConfigurableFileCollection>

private val multiPlatformEnabled = KotlinCompile::class.memberProperties
    .first { it.name == "multiPlatformEnabled" }
    .apply { isAccessible = true } as KProperty1<KotlinCompile, Property<Boolean>>

fun Project.createCommonTarget(common: CommonTarget, edges: Iterable<MinecraftTarget>) {
    val cloche = extension<ClocheExtension>()
    val intersections = extension<MinecraftCodevExtension>().extension<IntersectionMinecraftCodevExtension>()
    val sourceSets = extension<SourceSetContainer>()

    val main = edges.map { it to it.main as RunnableCompilationInternal }
    val test = edges.map { it to it.test as RunnableCompilationInternal }
    val client = edges.takeIf { it.all { it is ClientTarget && (it.client as RunnableCompilationInternal).createSourceSet } }?.map { it to (it as ClientTarget).client as RunnableCompilationInternal }
    val data = edges.takeIf { it.all { it.data != null } }?.map { it to it.data as RunnableCompilationInternal }

    val sourceSetName = { target: ClocheTarget, compilationName: String ->
        when {
            target.name == ClocheExtension::common.name -> compilationName
            compilationName == SourceSet.MAIN_SOURCE_SET_NAME -> target.name
            else -> lowerCamelCaseName(target.name, compilationName)
        }
    }

    fun intersection(compilations: List<Pair<MinecraftTarget, RunnableCompilationInternal>>): Provider<in ModuleDependency> {
        for ((target, compilation) in compilations) {
            compilation.process(sourceSets.maybeCreate(sourceSetName(target, compilation.name)))
        }

        if (compilations.size == 1) {
            return compilations.first().second.dependency
        }

        return project
            .provider { compilations.map { (_, compilation) -> compilation.dependency.get() } }
            .map { intersections(*it.toTypedArray()) }
    }

    fun add(name: String, compilation: CompilationInternal, dependencyProvider: Provider<in ModuleDependency>) {
        val sourceSet = sourceSets.maybeCreate(sourceSetName(common, name))

        compilation.process(sourceSet)

        project.dependencies.addProvider(sourceSet.implementationConfigurationName, dependencyProvider)
        project.dependencies.add(sourceSet.compileOnlyConfigurationName, "net.msrandom:multiplatform-annotations:1.0.0")

        for (edge in edges) {
            val edgeDependant = sourceSets.findByName(sourceSetName(edge, name)) ?: run {
                if (name == ClochePlugin.CLIENT_COMPILATION_NAME) {
                    sourceSets.findByName(sourceSetName(edge, SourceSet.MAIN_SOURCE_SET_NAME))
                } else {
                    null
                }
            } ?: continue

            edgeDependant.extension<VirtualExtension>().dependsOn.add(sourceSet)

            if (cloche.useKotlin.get()) {
                val kotlin = extension<KotlinSourceSetContainer>()

                val kotlinSourceSet = kotlin.sourceSets.getByName(edgeDependant.name)

                kotlinSourceSet.dependsOn(kotlin.sourceSets.getByName(sourceSet.name))

                tasks.named(edgeDependant.getCompileTaskName("kotlin"), KotlinCompile::class.java) {
                    multiPlatformEnabled.get(it).set(true)
                    commonSourceSet.get(it).from(sourceSet.allSource)
                }
            }

            project.dependencies.add(edgeDependant.annotationProcessorConfigurationName, "net.msrandom:multiplatform-processor:1.0.7")

            project.extend(edgeDependant.mixinsConfigurationName, sourceSet.mixinsConfigurationName)
        }

        if (name == SourceSet.MAIN_SOURCE_SET_NAME) {
            return
        }

        val dependency = sourceSets.findByName(sourceSetName(common, SourceSet.MAIN_SOURCE_SET_NAME)) ?: return

        sourceSet.extension<VirtualExtension>().dependsOn.add(dependency)

        if (cloche.useKotlin.get()) {
            val kotlin = extension<KotlinSourceSetContainer>()

            kotlin.sourceSets.getByName(sourceSet.name).dependsOn(kotlin.sourceSets.getByName(dependency.name))
        }

        project.extend(sourceSet.mixinsConfigurationName, dependency.mixinsConfigurationName)
    }

    add(SourceSet.MAIN_SOURCE_SET_NAME, common.main as CompilationInternal, intersection(main))
    add(SourceSet.TEST_SOURCE_SET_NAME, common.test as CompilationInternal, intersection(test))
    client?.let(::intersection)?.let { add(ClochePlugin.CLIENT_COMPILATION_NAME, common.client as CompilationInternal, it) }
    data?.let(::intersection)?.let { add(ClochePlugin.DATA_COMPILATION_NAME, common.data as CompilationInternal, it) }
}
