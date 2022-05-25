package edu.kit.kastel.dsis.fluidtrust.dataflow.analysis;

import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.IProgressMonitor;
import org.palladiosimulator.dataflow.confidentiality.pcm.model.confidentiality.dictionary.DictionaryPackage;
import org.palladiosimulator.dataflow.confidentiality.pcm.model.confidentiality.dictionary.PCMDataDictionary;
import org.palladiosimulator.dataflow.confidentiality.transformation.workflow.blackboards.KeyValueMDSDBlackboard;
import org.palladiosimulator.dataflow.dictionary.characterized.DataDictionaryCharacterized.EnumCharacteristicType;
import org.palladiosimulator.pcm.allocation.Allocation;
import org.palladiosimulator.pcm.core.entity.Entity;
import org.palladiosimulator.pcm.seff.SetVariableAction;
import org.palladiosimulator.pcm.usagemodel.UsageModel;

import de.uka.ipd.sdq.workflow.jobs.AbstractBlackboardInteractingJob;
import de.uka.ipd.sdq.workflow.jobs.CleanupFailedException;
import de.uka.ipd.sdq.workflow.jobs.JobFailedException;
import de.uka.ipd.sdq.workflow.jobs.UserCanceledException;
import de.uka.ipd.sdq.workflow.mdsd.blackboard.ModelLocation;
import edu.kit.kastel.dsis.fluidtrust.casestudy.pcs.analysis.ActionSequenceFinderImpl;
import edu.kit.kastel.dsis.fluidtrust.casestudy.pcs.analysis.CharacteristicsCalculator;
import edu.kit.kastel.dsis.fluidtrust.casestudy.pcs.analysis.CharacteristicsQueryEngine;
import edu.kit.kastel.dsis.fluidtrust.casestudy.pcs.analysis.dto.ActionBasedQueryImpl;
import edu.kit.kastel.dsis.fluidtrust.casestudy.pcs.analysis.dto.ActionBasedQueryResult;
import edu.kit.kastel.dsis.fluidtrust.casestudy.pcs.analysis.dto.ActionSequenceElement;
import edu.kit.kastel.dsis.fluidtrust.casestudy.pcs.analysis.dto.CallingActionSequenceElement;
import edu.kit.kastel.dsis.fluidtrust.casestudy.pcs.analysis.dto.CharacteristicValue;

public abstract class RunCustomJavaBasedAnalysisJob extends AbstractBlackboardInteractingJob<KeyValueMDSDBlackboard> {

	private ModelLocation usageModelLocation;
	private ModelLocation allocationModelLocation;
	private String allCharacteristicsResultKey;
	private String violationResultKey;
	
	public RunCustomJavaBasedAnalysisJob prepareJob(ModelLocation usageModelLocation, ModelLocation allocationModelLocation,
			String allCharacteristicsResultKey, String violationResultKey) {
		this.usageModelLocation = usageModelLocation;
		this.allocationModelLocation = allocationModelLocation;
		this.allCharacteristicsResultKey = allCharacteristicsResultKey;
		this.violationResultKey = violationResultKey;
		return this;
	}

	@Override
	public void execute(IProgressMonitor monitor) throws JobFailedException, UserCanceledException {
		if (usageModelLocation == null || allocationModelLocation == null || allCharacteristicsResultKey == null || violationResultKey == null) {
			throw new IllegalStateException("prepare job before executing it");
		}
		
		var usageModel = (UsageModel) getBlackboard().getContents(usageModelLocation).get(0);
		var allocationModel = (Allocation) getBlackboard().getContents(allocationModelLocation).get(0);
		var modelPartition = getBlackboard().getPartition(usageModelLocation.getPartitionID());
		var dataDictionaries = modelPartition.getElement(DictionaryPackage.eINSTANCE.getPCMDataDictionary()).stream()
				.filter(PCMDataDictionary.class::isInstance).map(PCMDataDictionary.class::cast)
				.collect(Collectors.toList());

		monitor.beginTask("Execute queries", 3);

		var queryEngine = createQueryEngine(usageModel, allocationModel);
		monitor.worked(1);

		var allCharacteristics = findAllCharacteristics(queryEngine, dataDictionaries);
		getBlackboard().put(allCharacteristicsResultKey, allCharacteristics);
		monitor.worked(1);

		var detectedViolations = findViolations(dataDictionaries, allCharacteristics);
		getBlackboard().put(violationResultKey, detectedViolations);
		monitor.worked(1);
		monitor.done();
	}
	
	protected abstract ActionBasedQueryResult findViolations(List<PCMDataDictionary> dataDictionaries,
			ActionBasedQueryResult allCharacteristics) throws JobFailedException;

	protected EnumCharacteristicType findByName(Collection<EnumCharacteristicType> enumCharacteristicTypes, String name)
			throws JobFailedException {
		return enumCharacteristicTypes.stream().filter(ct -> ct.getName().equals(name)).findFirst()
				.orElseThrow(() -> new JobFailedException("Could not find " + name + " characteristic type."));
	}

	protected ActionBasedQueryResult findAllCharacteristics(CharacteristicsQueryEngine queryEngine,
			Collection<PCMDataDictionary> dataDictionaries) {
		var allCharacteristicValues = getAllEnumCharacteristicTypes(dataDictionaries).stream()
				.flatMap(c -> c.getType().getLiterals().stream().map(l -> new CharacteristicValue(c, l)))
				.collect(Collectors.toList());

		var query = new ActionBasedQueryImpl(Objects::nonNull, allCharacteristicValues, allCharacteristicValues);
		return queryEngine.query(query);
	}

	protected Collection<EnumCharacteristicType> getAllEnumCharacteristicTypes(
			Collection<PCMDataDictionary> dataDictionaries) {
		return dataDictionaries.stream().map(PCMDataDictionary::getCharacteristicTypes).flatMap(Collection::stream)
				.filter(EnumCharacteristicType.class::isInstance).map(EnumCharacteristicType.class::cast)
				.collect(Collectors.toList());
	}

	protected CharacteristicsQueryEngine createQueryEngine(UsageModel usageModel, Allocation allocationModel) {
		var actionSequenceFinder = new ActionSequenceFinderImpl();
		var actionSequences = actionSequenceFinder.findActionSequencesForUsageModel(usageModel);
		var characteristicsCalculator = new CharacteristicsCalculator(allocationModel);
		var queryEngine = new CharacteristicsQueryEngine(characteristicsCalculator, actionSequences);
		return queryEngine;
	}

	@Override
	public void cleanup(IProgressMonitor monitor) throws CleanupFailedException {
		// nothing to do here
	}

	protected String getElementRepresentation(ActionSequenceElement<?> element) {
		Entity modelElement = (Entity) element.getElement();
		String modelElementTypeName = modelElement.eClass().getName();
		if (element instanceof CallingActionSequenceElement<?>) {
			if (((CallingActionSequenceElement<?>) element).isCallingPart()) {
				modelElementTypeName = String.format("%s (%s)", modelElementTypeName, "calling");
			} else {
				modelElementTypeName = String.format("%s (%s)", modelElementTypeName, "returning");
			}
		}

		return String.format("%s (%s) of type %s", modelElement.getEntityName(), modelElement.getId(),
				modelElementTypeName);
	}

	@Override
	public String getName() {
		return "Analysis Run";
	}

}
