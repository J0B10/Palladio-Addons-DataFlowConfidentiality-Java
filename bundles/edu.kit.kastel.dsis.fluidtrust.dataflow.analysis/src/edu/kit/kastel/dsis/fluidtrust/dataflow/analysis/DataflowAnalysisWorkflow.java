package edu.kit.kastel.dsis.fluidtrust.dataflow.analysis;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.eclipse.emf.common.util.URI;
import org.eclipse.xtext.linking.impl.AbstractCleaningLinker;
import org.eclipse.xtext.linking.impl.DefaultLinkingService;
import org.eclipse.xtext.parser.antlr.AbstractInternalAntlrParser;
import org.eclipse.xtext.resource.containers.ResourceSetBasedAllContainersStateProvider;
import org.palladiosimulator.dataflow.confidentiality.pcm.dddsl.DDDslStandaloneSetup;
import org.palladiosimulator.dataflow.confidentiality.transformation.workflow.blackboards.KeyValueMDSDBlackboard;

import de.uka.ipd.sdq.workflow.jobs.SequentialBlackboardInteractingJob;
import tools.mdsd.library.standalone.initialization.StandaloneInitializationException;
import tools.mdsd.library.standalone.initialization.emfprofiles.EMFProfileInitializationTask;
import tools.mdsd.library.standalone.initialization.log4j.Log4jInitilizationTask;

public class DataflowAnalysisWorkflow extends SequentialBlackboardInteractingJob<KeyValueMDSDBlackboard> {

	public DataflowAnalysisWorkflow(URI allocationURI, URI usageURI, RunCustomJavaBasedAnalysisJob runAnalysisJob) {

		// TODO: Stephans Test Initilaizer der Palladio Erweiterung anschauen
		//IProfileRegistry.eINSTANCE.getClass();

		try {
			new EMFProfileInitializationTask("org.palladiosimulator.dataflow.confidentiality.pcm.model.profile",
					"profile.emfprofile_diagram").initilizationWithoutPlatform();
		} catch (final StandaloneInitializationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		DDDslStandaloneSetup.doSetup();
		try {
			new Log4jInitilizationTask().initilizationWithoutPlatform();
		} catch (StandaloneInitializationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		BasicConfigurator.configure(new ConsoleAppender(new PatternLayout("%d{HH:mm:ss,SSS} %m%n")));
		Logger.getLogger(AbstractInternalAntlrParser.class).setLevel(Level.WARN);
		Logger.getLogger(DefaultLinkingService.class).setLevel(Level.WARN);
		Logger.getLogger(ResourceSetBasedAllContainersStateProvider.class).setLevel(Level.WARN);
		Logger.getLogger(AbstractCleaningLinker.class).setLevel(Level.WARN);

		// build and run job
		this.add(new DataflowAnalysisJob(allocationURI, usageURI, runAnalysisJob));
	}

}
