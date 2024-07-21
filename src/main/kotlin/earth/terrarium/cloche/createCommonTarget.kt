@file:Suppress("UNCHECKED_CAST")

package earth.terrarium.cloche

import earth.terrarium.cloche.target.*
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseName
import net.msrandom.minecraftcodev.intersection.JarIntersection
import net.msrandom.minecraftcodev.mixins.mixinsConfigurationName
import net.msrandom.virtualsourcesets.VirtualExtension
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

private val commonSourceSet = KotlinCompile::class.memberProperties
    .first { it.name == "commonSourceSet" }
    .apply { isAccessible = true } as KProperty1<KotlinCompile, ConfigurableFileCollection>

private val multiPlatformEnabled = KotlinCompile::class.memberProperties
    .first { it.name == "multiPlatformEnabled" }
    .apply { isAccessible = true } as KProperty1<KotlinCompile, Property<Boolean>>

context(Project) fun createCommonTarget(common: CommonTarget, edges: Iterable<MinecraftTarget>) {
    val sourceSets = extension<SourceSetContainer>()

    val main = edges.map { it to it.main as RunnableCompilationInternal }
    val client = edges.takeIf { it.any { it is ClientTarget } }?.map { it to ((it as? ClientTarget)?.client ?: it.main) as RunnableCompilationInternal }
    val data = edges.map { it to it.data as RunnableCompilationInternal }

    val sourceSetName = { target: ClocheTarget, compilationName: String ->
        when {
            target.name == ClocheExtension::common.name -> compilationName
            compilationName == SourceSet.MAIN_SOURCE_SET_NAME -> target.name
            else -> lowerCamelCaseName(target.name, compilationName)
        }
    }

    fun intersection(compilations: List<Pair<MinecraftTarget, RunnableCompilationInternal>>): FileCollection {
        val name = lowerCamelCaseName("create", *compilations.map { (target) -> target.name }.toTypedArray(), "intersection")

        val createIntersection = project.tasks.withType(JarIntersection::class.java).findByName(name) ?: project.tasks.create(name, JarIntersection::class.java) {
            for ((_, compilation) in compilations) {
                it.files.from(compilation.minecraftJar)
            }
        }

        project.addSetupTask(name)

        return files(createIntersection.output)
    }

    fun add(name: String, compilation: CompilationInternal, minecraftIntersection: FileCollection) {
        val sourceSet = with(common) {
            compilation.sourceSet
        }

        sourceSet.compileClasspath += minecraftIntersection

        project.dependencies.add(sourceSet.compileOnlyConfigurationName, "net.msrandom:multiplatform-annotations:1.0.0")

        tasks.named(sourceSet.compileJavaTaskName, JavaCompile::class.java) {
            it.options.compilerArgs.add("-AgenerateExpectStubs")
        }

        for (edge in edges) {
            val edgeDependant = sourceSets.findByName(sourceSetName(edge, name)) ?: run {
                if (name == ClochePlugin.CLIENT_COMPILATION_NAME) {
                    sourceSets.findByName(sourceSetName(edge, SourceSet.MAIN_SOURCE_SET_NAME))
                } else {
                    null
                }
            } ?: continue

            edgeDependant.extension<VirtualExtension>().dependsOn.add(sourceSet)

            plugins.withId(ClochePlugin.KOTLIN_JVM) {
                val kotlin = extension<KotlinJvmProjectExtension>()
                val kotlinCompilation = kotlin.target.compilations.getByName(edgeDependant.name)

                tasks.named(kotlinCompilation.compileKotlinTaskName, KotlinCompile::class.java) {
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

        project.extend(sourceSet.mixinsConfigurationName, dependency.mixinsConfigurationName)
    }

    add(SourceSet.MAIN_SOURCE_SET_NAME, common.main as CompilationInternal, intersection(main))
    client?.let(::intersection)?.let { add(ClochePlugin.CLIENT_COMPILATION_NAME, common.client as CompilationInternal, it) }
    add(ClochePlugin.DATA_COMPILATION_NAME, common.data as CompilationInternal, intersection(data))
}
