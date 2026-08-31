package fixture.srg;

public final class DirectSrg extends fixture.analysis.CompileOrdinary implements fixture.analysis.RuntimeOrdinary {
    private net.fabricmc.loader.api.FabricLoader targetEnvironment;
    private final fixture.analysis.RemapLinkage remapLinkage = new fixture.analysis.RemapLinkage();

    @Override
    public String runtimeOrdinarySrg() {
        return "snr-t015-runtime-ordinary-srg";
    }

    public String invokeAllSrg() {
        return compileOrdinarySrg() + "|" + runtimeOrdinarySrg() + "|" + remapLinkage.remapLinkageSrg();
    }

    public String directSrg() {
        return "snr-t014-direct-srg-20260802-1";
    }
}