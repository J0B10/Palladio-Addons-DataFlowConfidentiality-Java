package edu.kit.kastel.dsis.fluidtrust.dataflow.analysis;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.emf.ecore.resource.Resource;
import org.junit.jupiter.api.Test;

import de.uka.ipd.sdq.workflow.jobs.JobFailedException;
import de.uka.ipd.sdq.workflow.jobs.UserCanceledException;

public class ConfidentialityAnalysisTest extends TestBase {

	@Override
	protected List<String> getModelsPath() {
		// TODO Auto-generated method stub
		return new ArrayList<>();
	}

	@Override
	protected void assignValues(List<Resource> list) {
		// TODO Auto-generated method stub
	}
	
	@Test
	public void test() throws JobFailedException, UserCanceledException {
		System.out.println("Hello World!");
		
		final var folderURI = TestInitializer.getModelURI("models/InternationalOnlineShop");
		final var workflow = new DataflowAnalysisWorkflow(folderURI);
		workflow.execute(new NullProgressMonitor());
	}

}
