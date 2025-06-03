package earth.terrarium.cloche.target.forge.lex

import earth.terrarium.cloche.target.forge.ForgeRunConfigurations
import net.msrandom.minecraftcodev.forge.runs.ForgeRunConfigurationData
import net.msrandom.minecraftcodev.mixins.mixinsConfigurationName
import net.msrandom.minecraftcodev.runs.MinecraftRunConfiguration
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import javax.inject.Inject

internal abstract class LexForgeRunConfigurations @Inject constructor(target: ForgeTargetImpl) : ForgeRunConfigurations<ForgeTargetImpl>(target) {
    override fun configureData(data: ForgeRunConfigurationData, sourceSet: Provider<SourceSet>) {
        super.configureData(data, sourceSet)

        data.generateMcpToSrg.set(target.generateMcpToSrg)
        data.mixinConfigs.from(sourceSet.flatMap { project.configurations.named(it.mixinsConfigurationName) })
    }

    override fun configureData(data: ForgeRunConfigurationData, sourceSet: SourceSet) {
        super.configureData(data, sourceSet)

        data.generateMcpToSrg.set(target.generateMcpToSrg)
        data.mixinConfigs.from(project.configurations.named(sourceSet.mixinsConfigurationName))
    }

    override fun applyDefault(run: MinecraftRunConfiguration) {
        super.applyDefault(run)
        run.jvmArgs("-Dcodev.naming.mappingsPath=${target.loadMappingsTask.get().output.get()}")
    }
}
