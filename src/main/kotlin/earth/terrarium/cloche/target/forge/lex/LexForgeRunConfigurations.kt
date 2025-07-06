package earth.terrarium.cloche.target.forge.lex

import earth.terrarium.cloche.target.TargetCompilation
import earth.terrarium.cloche.target.forge.ForgeRunConfigurations
import net.msrandom.minecraftcodev.forge.runs.ForgeRunConfigurationData
import net.msrandom.minecraftcodev.runs.MinecraftRunConfiguration
import org.gradle.api.provider.Provider
import javax.inject.Inject

internal abstract class LexForgeRunConfigurations @Inject constructor(target: ForgeTargetImpl) : ForgeRunConfigurations<ForgeTargetImpl>(target) {
    override fun configureData(data: ForgeRunConfigurationData, compilation: Provider<TargetCompilation>) {
        super.configureData(data, compilation)

        data.generateMcpToSrg.set(target.generateMcpToSrg)
        data.mixinConfigs.from(compilation.map(TargetCompilation::mixins))
    }

    override fun configureData(data: ForgeRunConfigurationData, compilation: TargetCompilation) {
        super.configureData(data, compilation)

        data.generateMcpToSrg.set(target.generateMcpToSrg)
        data.mixinConfigs.from(compilation.mixins)
    }

    override fun applyDefault(run: MinecraftRunConfiguration) {
        super.applyDefault(run)
        run.jvmArgs("-Dcodev.naming.mappingsPath=${target.loadMappingsTask.get().output.get()}")
    }
}
