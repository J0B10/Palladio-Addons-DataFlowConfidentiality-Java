package edu.kit.kastel.dsis.fluidtrust.dataflow.analysis;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.emf.common.util.URI;
import org.palladiosimulator.dataflow.confidentiality.transformation.workflow.blackboards.KeyValueMDSDBlackboard;
import org.palladiosimulator.dataflow.confidentiality.transformation.workflow.jobs.LoadModelJob;

import de.uka.ipd.sdq.workflow.jobs.AbstractBlackboardInteractingJob;
import de.uka.ipd.sdq.workflow.jobs.CleanupFailedException;
import de.uka.ipd.sdq.workflow.jobs.JobFailedException;
import de.uka.ipd.sdq.workflow.jobs.SequentialBlackboardInteractingJob;
import de.uka.ipd.sdq.workflow.jobs.UserCanceledException;
import de.uka.ipd.sdq.workflow.mdsd.blackboard.ModelLocation;
import edu.kit.kastel.dsis.fluidtrust.casestudy.pcs.application.jobs.RunJavaBasedAnalysisJob;

public class DataflowAnalysisJob extends AbstractBlackboardInteractingJob<KeyValueMDSDBlackboard> {

	private final URI folderURI;
	protected static final String PCM_MODEL_PARTITION = "pcmModels";
	private static final String ALL_CHARACTERISTICS_RESULT_KEY = "resultAllCharacteristicsKey";
	private static final String VIOLATIONS_RESULT_KEY = "resultViolationsKey";

	public DataflowAnalysisJob(URI folderURI) {
		this.folderURI = folderURI;
	}

	@Override
	public void execute(IProgressMonitor monitor) throws JobFailedException, UserCanceledException {
		var job = new SequentialBlackboardInteractingJob<KeyValueMDSDBlackboard>();
		job.setBlackboard(new KeyValueMDSDBlackboard());

		try {
			var usageModelLocation = new ModelLocation(PCM_MODEL_PARTITION, this.getURI(folderURI, "usagemodel"));
			var allocationLocation = new ModelLocation(PCM_MODEL_PARTITION, this.getURI(folderURI, "allocation"));

			var loadUsageModelJob = new LoadModelJob<KeyValueMDSDBlackboard>(
					Arrays.asList(usageModelLocation, allocationLocation));
			job.add(loadUsageModelJob);

			var runAnalysisJob = new RunJavaBasedAnalysisJob(usageModelLocation, allocationLocation,
					ALL_CHARACTERISTICS_RESULT_KEY, VIOLATIONS_RESULT_KEY);
			job.add(runAnalysisJob);
		} catch (IOException | URISyntaxException e) {
			throw new JobFailedException("Failure finding models", e);
		}

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

	private URI getURI(final URI folder, final String modelFileExtension) throws IOException, URISyntaxException {
		final var path = this.getPath(folder);
		final var modelFile = Files.walk(path).filter(file -> file.toString().endsWith(modelFileExtension)).findAny()
				.get().getFileName().toString();
		return folder.appendSegment(modelFile);
	}

	@SuppressWarnings("deprecation")
	private Path getPath(final URI uri) throws URISyntaxException, IOException {
		final var url = new URL(uri.toString());
		return Paths.get(Platform.asLocalURL(url).toURI().getSchemeSpecificPart());
	}

}
