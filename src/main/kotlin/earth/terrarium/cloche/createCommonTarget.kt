package earth.terrarium.cloche

import earth.terrarium.cloche.target.*
import net.msrandom.minecraftcodev.accesswidener.accessWidenersConfigurationName
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.intersection.JarIntersection
import net.msrandom.minecraftcodev.mixins.mixinsConfigurationName
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.compile.JavaCompile

private const val GENERATE_JAVA_EXPECT_STUBS_OPTION = "generateExpectStubs"

context(Project) fun createCommonTarget(common: CommonTarget, edges: Iterable<MinecraftTarget>) {
    val main = edges.map { it to it.main as RunnableCompilationInternal }
    val client = edges.takeIf { it.any { it is ClientTarget } }?.map { it to ((it as? ClientTarget)?.client ?: it.main) as RunnableCompilationInternal }
    val data = edges.map { it to it.data as RunnableCompilationInternal }

    fun intersection(compilations: List<Pair<MinecraftTarget, RunnableCompilationInternal>>): FileCollection {
        if (compilations.size == 1) {
            return compilations.first().second.finalMinecraftFiles
        }

        val name = lowerCamelCaseGradleName("create", *compilations.map { (target) -> target.name }.toTypedArray(), "intersection")

        val createIntersection = project.tasks.withType(JarIntersection::class.java).findByName(name) ?: project.tasks.create(name, JarIntersection::class.java) {
            for ((_, compilation) in compilations) {
                it.files.from(compilation.finalMinecraftFiles)
            }
        }

        project.addSetupTask(name)

        return files(createIntersection.output)
    }

    fun SourceSet.addDependencyIntersection(edgeCompilations: List<Pair<MinecraftTarget, RunnableCompilationInternal>>, configurationName: SourceSet.() -> String) {
        val edgeDependencies = edgeCompilations.map { (target, compilation) ->
            with(target) {
                project.configurations.getByName(compilation.sourceSet.configurationName()).dependencies.toList()
            }
        }

        val intersection = edgeDependencies.reduce { acc, dependencies ->
            acc.mapNotNull { a ->
                if (a.group == null || a !is ModuleDependency) {
                    return@mapNotNull null
                }

                val b = dependencies.find { b ->
                    a.group == b.group && a.name == b.name
                } ?: return@mapNotNull null

                if (a.version.isNullOrBlank()) {
                    if (b.version.isNullOrBlank()) {
                        a
                    } else {
                        b
                    }
                } else {
                    if (a.version!! > b.version!!) {
                        b
                    } else {
                        a
                    }
                }
            }
        }

        for (dependency in intersection) {
            project.dependencies.add(configurationName(), dependency)
        }
    }

    fun add(name: String, compilation: CommonCompilation, edgeCompilations: List<Pair<MinecraftTarget, RunnableCompilationInternal>>) {
        val sourceSet = with(common) {
            compilation.sourceSet
        }

        sourceSet.compileClasspath += intersection(edgeCompilations)

        project.dependencies.add(sourceSet.compileOnlyConfigurationName, "net.msrandom:java-expect-actual-annotations:1.0.0")
        project.dependencies.add(sourceSet.annotationProcessorConfigurationName, JAVA_EXPECT_ACTUAL_ANNOTATION_PROCESSOR)
        project.dependencies.add(sourceSet.accessWidenersConfigurationName, compilation.accessWideners)
        project.dependencies.add(sourceSet.mixinsConfigurationName, compilation.mixins)

        sourceSet.addDependencyIntersection(edgeCompilations, SourceSet::getImplementationConfigurationName)
        sourceSet.addDependencyIntersection(edgeCompilations, SourceSet::getApiConfigurationName)
        sourceSet.addDependencyIntersection(edgeCompilations, SourceSet::getCompileOnlyApiConfigurationName)
        sourceSet.addDependencyIntersection(edgeCompilations, SourceSet::getCompileOnlyConfigurationName)
        sourceSet.addDependencyIntersection(edgeCompilations, SourceSet::getRuntimeOnlyConfigurationName)

        tasks.named(sourceSet.compileJavaTaskName, JavaCompile::class.java) {
            it.options.compilerArgs.add("-A$GENERATE_JAVA_EXPECT_STUBS_OPTION")
        }

        val dependencyHandler = ClocheDependencyHandler(project, sourceSet)

        for (dependencySetupAction in compilation.dependencySetupActions) {
            dependencySetupAction.execute(dependencyHandler)
        }

        for (edge in edges) {
            val edgeCompilation = when (name) {
                ClochePlugin.CLIENT_COMPILATION_NAME -> (edge as? ClientTarget)?.client ?: edge.main
                ClochePlugin.DATA_COMPILATION_NAME -> edge.data
                else -> edge.main
            } as RunnableCompilationInternal

            val edgeDependant = with(edge) {
                edgeCompilation.sourceSet
            }

            edgeDependant.linkStatically(sourceSet)
        }

        if (name == SourceSet.MAIN_SOURCE_SET_NAME) {
            return
        }

        val mainDependency = with(common) {
            (common.main as CommonCompilation).sourceSet
        }

        sourceSet.linkDynamically(mainDependency)
    }

    add(SourceSet.MAIN_SOURCE_SET_NAME, common.main as CommonCompilation, main)
    client?.let { add(ClochePlugin.CLIENT_COMPILATION_NAME, common.client as CommonCompilation, it) }
    add(ClochePlugin.DATA_COMPILATION_NAME, common.data as CommonCompilation, data)
}
