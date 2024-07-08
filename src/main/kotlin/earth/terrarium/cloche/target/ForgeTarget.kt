package earth.terrarium.cloche.target

import earth.terrarium.cloche.ClocheDependencyHandler
import earth.terrarium.cloche.ClocheExtension
import earth.terrarium.cloche.ClochePlugin
import net.msrandom.minecraftcodev.accesswidener.accessWidenersConfigurationName
import net.msrandom.minecraftcodev.core.MinecraftCodevExtension
import net.msrandom.minecraftcodev.core.task.DownloadMinecraftMappings
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseName
import net.msrandom.minecraftcodev.forge.MinecraftCodevForgePlugin
import net.msrandom.minecraftcodev.forge.PatchedMinecraftCodevExtension
import net.msrandom.minecraftcodev.forge.patchesConfigurationName
import net.msrandom.minecraftcodev.forge.runs.ForgeRunsDefaultsContainer
import net.msrandom.minecraftcodev.forge.task.ResolvePatchedMinecraft
import net.msrandom.minecraftcodev.mixins.mixinsConfigurationName
import net.msrandom.minecraftcodev.remapper.mappingsConfigurationName
import net.msrandom.minecraftcodev.remapper.task.RemapJar
import net.msrandom.minecraftcodev.runs.MinecraftRunConfigurationBuilder
import net.msrandom.minecraftcodev.runs.nativesConfigurationName
import org.gradle.api.Action
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.SourceSet
import org.gradle.language.jvm.tasks.ProcessResources
import java.io.File

abstract class ForgeTarget(private val name: String) : MinecraftTarget {
    private val minecraftForgeClasspath = project.configurations.create(lowerCamelCaseName(name, "minecraftForgeClasspath"))

    private val downloadClientMappings = project.tasks.register(lowerCamelCaseName("download", name, "clientMappings"), DownloadMinecraftMappings::class.java) {
        it.version.set(minecraftVersion)
        it.server.set(false)
    }

    private val resolvePatchedMinecraft = project.tasks.register(lowerCamelCaseName("resolve", name, "patchedMinecraft"), ResolvePatchedMinecraft::class.java) {
        it.version.set(minecraftVersion)
        it.clientMappings.set(downloadClientMappings.flatMap(DownloadMinecraftMappings::output))
        it.patches.from(main.sourceSet.map(SourceSet::patchesConfigurationName).flatMap(project.configurations::named))
    }

    private val remapJarTask = project.tasks.register(lowerCamelCaseName("remap", name, "minecraft"), RemapJar::class.java) {
        it.destinationDirectory.set(it.temporaryDir)
        it.archiveBaseName.set("minecraft")
        it.archiveVersion.set(name)
        it.archiveClassifier.set("named")
        it.sourceNamespace.set(if (remapNamespace == null) "named" else "srg")
        it.targetNamespace.set("named")

        it.mappings.from(sourceSet.map(SourceSet::mappingsConfigurationName).flatMap(project.configurations::named))
        it.input.set(resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::output))
        it.classpath.from(minecraftForgeClasspath)
    }

    final override val main: TargetCompilation = run {
        project.objects.newInstance(TargetCompilation::class.java, name, SourceSet.MAIN_SOURCE_SET_NAME, remapJarTask.flatMap(RemapJar::getArchiveFile))
    }

    final override val test: TargetCompilation = run {
        project.objects.newInstance(TargetCompilation::class.java, name, SourceSet.TEST_SOURCE_SET_NAME, remapJarTask.flatMap(RemapJar::getArchiveFile))
    }

    /*    private val client: TargetCompilation = run {
            project.objects.newInstance(TargetCompilation::class.java, name, ClochePlugin.CLIENT_COMPILATION_NAME, remapJarTask.flatMap(RemapJar::getArchiveFile))
        }*/

    final override val data: TargetCompilation = run {
        project.objects.newInstance(TargetCompilation::class.java, name, ClochePlugin.DATA_COMPILATION_NAME, remapJarTask.flatMap(RemapJar::getArchiveFile))
    }

    override val accessWideners get() = main.accessWideners
    override val mixins get() = main.mixins

    override val sourceSet get() = main.sourceSet

    private var hasMappings = false

    override val remapNamespace: String?
        get() = MinecraftCodevForgePlugin.SRG_MAPPINGS_NAMESPACE

    abstract val userdevClassifier: Property<String>
        @Input
        @Optional
        get

    open val group
        @Internal
        get() = "net.minecraftforge"

    open val artifact
        @Internal
        get() = "forge"

    override val loaderAttributeName get() = "forge"

    init {
        main.dependencies { dependencies ->
            val userdev = minecraftVersion.flatMap { minecraftVersion ->
                loaderVersion.flatMap { forgeVersion ->
                    userdevClassifier.orElse("userdev").map { userdev ->
                        "$group:$artifact:${version(minecraftVersion, forgeVersion)}:$userdev"
                    }
                }
            }

            // project.dependencies.addProvider(main.sourceSet.get().mappingsConfigurationName, userdev)
            project.dependencies.addProvider(main.sourceSet.get().patchesConfigurationName, userdev)

            val codevPatched = project.extension<MinecraftCodevExtension>().extension<PatchedMinecraftCodevExtension>()

            dependencies.implementation(main.dependency)

            minecraftForgeClasspath.dependencies.addAllLater(minecraftVersion.zip(sourceSet, ::Pair).flatMap { (version, sourceSet) ->
                codevPatched.dependencies(version, project.configurations.getByName(sourceSet.patchesConfigurationName))
            })

            project.configurations.named(dependencies.implementation.configurationName) {
                it.extendsFrom(minecraftForgeClasspath)
            }

            project.configurations.named(main.sourceSet.get().nativesConfigurationName) {
                val codev = project.extension<MinecraftCodevExtension>()

                it.dependencies.addAllLater(minecraftVersion.flatMap(codev::nativeDependencies))
            }

            project.dependencies.add(main.sourceSet.get().mixinsConfigurationName, main.mixins)
            project.dependencies.add(main.sourceSet.get().accessWidenersConfigurationName, main.accessWideners)

            if (!hasMappings && remapNamespace != null) {
                project.dependencies.add(main.sourceSet.get().mappingsConfigurationName, project.files(downloadClientMappings.flatMap(DownloadMinecraftMappings::output)))
            }

            project.tasks.named(main.sourceSet.get().processResourcesTaskName, ProcessResources::class.java) {
                it.from(project.configurations.named(main.sourceSet.get().mixinsConfigurationName))
            }
        }

        test.dependencies {
            project.dependencies.add(test.sourceSet.get().mixinsConfigurationName, test.mixins)
            project.dependencies.add(test.sourceSet.get().accessWidenersConfigurationName, test.accessWideners)
        }

        data.dependencies {
            project.dependencies.add(data.sourceSet.get().mixinsConfigurationName, data.mixins)
            project.dependencies.add(data.sourceSet.get().accessWidenersConfigurationName, data.accessWideners)

            it.implementation(data.dependency)
        }

        main.runConfiguration {
            it.defaults.extension<ForgeRunsDefaultsContainer>().server(minecraftVersion) { serverConfig ->
                serverConfig.mixinConfigs.set(project.configurations.named(main.sourceSet.get().mixinsConfigurationName).map { it.files.map(File::getName) })
            }
        }

        /*        client.runConfiguration {
                    it.defaults.extension<ForgeRunsDefaultsContainer>().client(minecraftVersion) { clientConfig ->
                        clientConfig.mixinConfigs.set(project.configurations.named(main.sourceSet.get().mixinsConfigurationName).map { it.files.map(File::getName) })
                    }
                }*/

        test.runConfiguration {
            // it.defaults.extension<ForgeRunsDefaultsContainer>().gameTestServer()
            it.mainClass("test")
        }

        data.runConfiguration {
            it.defaults.extension<ForgeRunsDefaultsContainer>().data(minecraftVersion) { datagen ->
                datagen.mixinConfigs.set(project.configurations.named(main.sourceSet.get().mixinsConfigurationName).map { it.files.map(File::getName) })

                datagen.modId.set(project.extension<ClocheExtension>().metadata.modId)

                datagen.outputDirectory.set(datagenDirectory)
            }
        }
    }

    override fun getName() = name

    protected open fun version(minecraftVersion: String, loaderVersion: String) =
        "$minecraftVersion-$loaderVersion"

    override fun dependencies(action: Action<ClocheDependencyHandler>) = main.dependencies(action)
    override fun runConfiguration(action: Action<MinecraftRunConfigurationBuilder>) = main.runConfiguration(action)

    override fun data(action: Action<RunnableCompilation>?) {
        action?.execute(data)
    }

    override fun test(action: Action<RunnableCompilation>?) {
        action?.execute(test)
    }

    override fun mappings(action: Action<MappingsBuilder>) {
        hasMappings = true

        val mappings = mutableListOf<MappingDependencyProvider>()

        action.execute(MappingsBuilder(project, mappings))

        main.dependencies {
            for (mapping in mappings) {
                project.dependencies.addProvider(main.sourceSet.get().mappingsConfigurationName, minecraftVersion.map(mapping))
            }
        }
    }
}
