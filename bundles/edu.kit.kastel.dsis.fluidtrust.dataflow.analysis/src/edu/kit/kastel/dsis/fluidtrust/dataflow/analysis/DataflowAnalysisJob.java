package edu.kit.kastel.dsis.fluidtrust.dataflow.analysis;

import java.util.Arrays;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.emf.common.util.URI;
import org.palladiosimulator.dataflow.confidentiality.transformation.workflow.blackboards.KeyValueMDSDBlackboard;
import org.palladiosimulator.dataflow.confidentiality.transformation.workflow.jobs.LoadModelJob;

import de.uka.ipd.sdq.workflow.jobs.AbstractBlackboardInteractingJob;
import de.uka.ipd.sdq.workflow.jobs.CleanupFailedException;
import de.uka.ipd.sdq.workflow.jobs.JobFailedException;
import de.uka.ipd.sdq.workflow.jobs.SequentialBlackboardInteractingJob;
import de.uka.ipd.sdq.workflow.jobs.UserCanceledException;
import de.uka.ipd.sdq.workflow.mdsd.blackboard.ModelLocation;

public class DataflowAnalysisJob extends AbstractBlackboardInteractingJob<KeyValueMDSDBlackboard> {

	private final URI allocationURI;
	private final URI usageURI;
	protected static final String PCM_MODEL_PARTITION = "pcmModels";
	private static final String ALL_CHARACTERISTICS_RESULT_KEY = "resultAllCharacteristicsKey";
	private static final String VIOLATIONS_RESULT_KEY = "resultViolationsKey";

	public DataflowAnalysisJob(URI allocationURI, URI usageURI) {
		this.usageURI = usageURI;
		this.allocationURI = allocationURI;
	}

	@Override
	public void execute(IProgressMonitor monitor) throws JobFailedException, UserCanceledException {
		var job = new SequentialBlackboardInteractingJob<KeyValueMDSDBlackboard>();
		job.setBlackboard(new KeyValueMDSDBlackboard());

		var usageModelLocation = new ModelLocation(PCM_MODEL_PARTITION, usageURI);
		var allocationLocation = new ModelLocation(PCM_MODEL_PARTITION, allocationURI);

		var loadUsageModelJob = new LoadModelJob<KeyValueMDSDBlackboard>(
				Arrays.asList(usageModelLocation, allocationLocation));
		job.add(loadUsageModelJob);

		var runAnalysisJob = new RunCustomJavaBasedAnalysisJob(usageModelLocation, allocationLocation,
				ALL_CHARACTERISTICS_RESULT_KEY, VIOLATIONS_RESULT_KEY);
		job.add(runAnalysisJob);
		
		job.execute(monitor);
	}

	@Override
	public void cleanup(IProgressMonitor monitor) throws CleanupFailedException {
		// TODO Auto-generated method stub

	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return "DataflowAnalysisJob";
	}

}
