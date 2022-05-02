package edu.kit.kastel.dsis.fluidtrust.dataflow.analysis;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.emf.ecore.resource.Resource;
import org.junit.jupiter.api.Disabled;
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
		
		final var allocationURI = TestInitializer.getModelURI("models/InternationalOnlineShop/default.allocation");
		final var usageURI = TestInitializer.getModelURI("models/InternationalOnlineShop/default.usagemodel");
		final var workflow = new DataflowAnalysisWorkflow(allocationURI, usageURI);
		workflow.execute(new NullProgressMonitor());
	}

	
	@Test
	public void testShop2() throws JobFailedException, UserCanceledException {
		System.out.println("Hello World!");
		final var allocationURI = TestInitializer.getModelURI("models/InternationalOnlineShop_paladio/default.allocation");
		final var usageURI = TestInitializer.getModelURI("models/InternationalOnlineShop_paladio/default.usagemodel");
		final var workflow = new DataflowAnalysisWorkflow(allocationURI, usageURI);
		workflow.execute(new NullProgressMonitor());
	}

	
	@Test
	public void testTravelPlanner() throws JobFailedException, UserCanceledException {
		System.out.println("Hello TravelPlanner!");
		final var allocationURI = TestInitializer.getModelURI("models/TravelPlanner/travelPlanner.allocation");
		final var usageURI = TestInitializer.getModelURI("models/TravelPlanner/travelPlanner.usagemodel");
		final var workflow = new DataflowAnalysisWorkflow(allocationURI, usageURI);
		workflow.execute(new NullProgressMonitor());
	}

}
