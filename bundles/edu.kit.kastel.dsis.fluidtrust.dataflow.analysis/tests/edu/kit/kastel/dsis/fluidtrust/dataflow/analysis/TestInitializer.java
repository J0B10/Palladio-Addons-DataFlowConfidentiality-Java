package edu.kit.kastel.dsis.fluidtrust.dataflow.analysis;

import org.eclipse.emf.common.util.URI;

import tools.mdsd.library.standalone.initialization.StandaloneInitializationException;
import tools.mdsd.library.standalone.initialization.StandaloneInitializerBuilder;
import tools.mdsd.library.standalone.initialization.log4j.Log4jInitilizationTask;

public class TestInitializer {

    private TestInitializer() {
        assert false;
    }

    public static void init() throws StandaloneInitializationException {
        StandaloneInitializerBuilder.builder()
                .registerProjectURI(Activator.class,
                        "edu.kit.kastel.dsis.fluidtrust.dataflow.analysis")
                .addCustomTask(new Log4jInitilizationTask()).build().init();
    }

    public static URI getModelURI(final String relativeModelPath) {
        return getRelativePluginURI(relativeModelPath);
    }

    private static URI getRelativePluginURI(final String relativePath) {
        return URI.createPlatformPluginURI(
               "/edu.kit.kastel.dsis.fluidtrust.dataflow.analysis/" + relativePath, false);
    }

}