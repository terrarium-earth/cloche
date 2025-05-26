package earth.terrarium.cloche.target.forge.lex

import earth.terrarium.cloche.target.forge.ForgeRunConfigurations
import net.msrandom.minecraftcodev.forge.runs.ForgeRunConfigurationData
import net.msrandom.minecraftcodev.runs.MinecraftRunConfiguration
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import javax.inject.Inject

internal abstract class LexForgeRunConfigurations @Inject constructor(target: ForgeTargetImpl) : ForgeRunConfigurations<ForgeTargetImpl>(target) {
    override fun configureData(data: ForgeRunConfigurationData, sourceSet: Provider<SourceSet>) {
        super.configureData(data, sourceSet)

        data.generateMcpToSrg.set(target.generateMcpToSrg)
    }

    override fun configureData(data: ForgeRunConfigurationData, sourceSet: SourceSet) {
        super.configureData(data, sourceSet)

        data.generateMcpToSrg.set(target.generateMcpToSrg)
    }

    override fun applyDefault(run: MinecraftRunConfiguration) {
        super.applyDefault(run)
        run.jvmArgs("-Dcodev.naming.mappingsPath=${target.loadMappingsTask.get().output.get()}")
    }
}
