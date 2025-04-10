package earth.terrarium.cloche.target.forge.lex

import earth.terrarium.cloche.target.forge.ForgeRunConfigurations
import net.msrandom.minecraftcodev.runs.MinecraftRunConfiguration
import javax.inject.Inject

internal abstract class LexForgeRunConfigurations @Inject constructor(target: ForgeTargetImpl) : ForgeRunConfigurations(target) {
    override fun applyDefault(run: MinecraftRunConfiguration) {
        super.applyDefault(run)
        run.jvmArgs("-Dcodev.naming.mappingsPath=${target.loadMappingsTask.get().output.get()}")
    }
}