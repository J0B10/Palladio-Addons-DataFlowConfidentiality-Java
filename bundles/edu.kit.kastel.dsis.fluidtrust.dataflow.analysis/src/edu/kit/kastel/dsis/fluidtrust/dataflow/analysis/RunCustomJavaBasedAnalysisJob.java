package edu.kit.kastel.dsis.fluidtrust.dataflow.analysis;

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

public class RunCustomJavaBasedAnalysisJob extends AbstractBlackboardInteractingJob<KeyValueMDSDBlackboard> {

	private final ModelLocation usageModelLocation;
	private final ModelLocation allocationModelLocation;
	private final String allCharacteristicsResultKey;
	private final String violationResultKey;

	public RunCustomJavaBasedAnalysisJob(ModelLocation usageModelLocation, ModelLocation allocationModelLocation,
			String allCharacteristicsResultKey, String violationResultKey) {
		this.usageModelLocation = usageModelLocation;
		this.allocationModelLocation = allocationModelLocation;
		this.allCharacteristicsResultKey = allCharacteristicsResultKey;
		this.violationResultKey = violationResultKey;
	}

	@Override
	public void execute(IProgressMonitor monitor) throws JobFailedException, UserCanceledException {
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

		// Hard coded, change the following line's findViolation call to test
		var detectedViolations = findViolationsTravelPlanner(dataDictionaries, allCharacteristics);
		getBlackboard().put(violationResultKey, detectedViolations);
		monitor.worked(1);
		monitor.done();
	}

	private ActionBasedQueryResult findViolationsTravelPlanner(List<PCMDataDictionary> dataDictionaries,
			ActionBasedQueryResult allCharacteristics) throws JobFailedException {
		var enumCharacteristicTypes = getAllEnumCharacteristicTypes(dataDictionaries);
		System.out.println("\n\nCHARACTERISTIC TYPES --------------------");
		enumCharacteristicTypes.forEach(entry -> System.out.println(entry.getName()));

		var ctGrantedRoles = findByName(enumCharacteristicTypes, "GrantedRoles");
		var ctAssignedRoles = findByName(enumCharacteristicTypes, "AssignedRoles");

		var violations = new ActionBasedQueryResult();

		System.out.println("\n\nELEMENTS AND CHARACTERISTICS --------------------");

		for (var resultEntry : allCharacteristics.getResults().entrySet()) {
			for (var queryResult : resultEntry.getValue()) {

				var availableVariables = queryResult.getDataCharacteristics().keySet();

				for (String variable : availableVariables) {
					var grantedRoles = queryResult.getDataCharacteristics().get(variable).stream()
							.filter(cv -> cv.getCharacteristicType() == ctGrantedRoles)
							.map(CharacteristicValue::getCharacteristicLiteral).map(it -> it.getName())
							.collect(Collectors.toList());

					var assignedRoles = queryResult.getNodeCharacteristics().stream()
							.filter(cv -> cv.getCharacteristicType() == ctAssignedRoles)
							.map(val -> val.getCharacteristicLiteral()).map(it -> it.getName())
							.collect(Collectors.toList());

					var element = getElementRepresentation(queryResult.getElement());

					System.out.println(element + ", " + variable + ", " + grantedRoles.toString() + ", "
							+ assignedRoles.toString());

					// Actual constraint
					if(!grantedRoles.stream().anyMatch(it -> assignedRoles.contains(it))) {
						violations.addResult(resultEntry.getKey(), queryResult);
					}
				}
			}
		}

		System.out.println("\n\nVIOLATIONS --------------------");
		violations.getResults().forEach((sequence, resultDTO) -> {
			resultDTO.forEach(it -> System.out.println(getElementRepresentation(it.getElement())));

			System.out.println("-- with sequence --");

			// Find path from newest entry level system call (might not be enough though)
			var pruneSequence = sequence.stream().takeWhile(
					it -> !resultDTO.stream().map(e -> e.getElement()).collect(Collectors.toList()).contains(it))
					.collect(Collectors.toList());

			var sysCalls = pruneSequence.stream()
					.filter(e -> ((Entity) e.getElement()).eClass().getName().equals("EntryLevelSystemCall"))
					.collect(Collectors.toList());
			var lastEntryLevelSystemCall = sysCalls.stream().skip(sysCalls.stream().count() - 1).findFirst().get();

			var finalSequence = pruneSequence.stream().dropWhile(it -> !it.equals(lastEntryLevelSystemCall));

			finalSequence.forEach(it -> System.out.println(getElementRepresentation(it)));

			System.out.println("\n\n");
		});

		return violations;
	}

	private ActionBasedQueryResult findViolationsOnlineShop(List<PCMDataDictionary> dataDictionaries,
			ActionBasedQueryResult allCharacteristics) throws JobFailedException {
		var enumCharacteristicTypes = getAllEnumCharacteristicTypes(dataDictionaries);
		System.out.println("\n\nCHARACTERISTIC TYPES --------------------");
		enumCharacteristicTypes.forEach(entry -> System.out.println(entry.getName()));

		//var ctServerLocation = findByName(enumCharacteristicTypes, "ServerLocation");
		//var ctDataSensitivity = findByName(enumCharacteristicTypes, "DataSensitivity")
		var ctGrantedRoles = findByName(enumCharacteristicTypes, "GrantedRoles");
		var ctAssignedRoles = findByName(enumCharacteristicTypes, "AssignedRoles");

		var violations = new ActionBasedQueryResult();

		System.out.println("\n\nELEMENTS AND CHARACTERISTICS --------------------");

		for (var resultEntry : allCharacteristics.getResults().entrySet()) {
			for (var queryResult : resultEntry.getValue()) {

				var grantedRoles = queryResult.getDataCharacteristics().values().stream()
						.flatMap(Collection::stream)
						.filter(cv -> cv.getCharacteristicType() == ctGrantedRoles)
						.map(CharacteristicValue::getCharacteristicLiteral).map(it -> it.getName())
						.collect(Collectors.toList());

				var assignedRoles = queryResult.getDataCharacteristics().values().stream()
						.flatMap(Collection::stream).filter(cv -> cv.getCharacteristicType() == ctAssignedRoles)
						.map(val -> val.getCharacteristicLiteral()).map(it -> it.getName())
						.collect(Collectors.toList());

				var element = getElementRepresentation(queryResult.getElement());

				System.out.println(element + ", " + grantedRoles.toString() + ", " + assignedRoles.toString());

				// Actual constraint
				if (!grantedRoles.equals(assignedRoles)) {
					violations.addResult(resultEntry.getKey(), queryResult);
				}
			}
		}

		System.out.println("\n\nVIOLATIONS --------------------");
		violations.getResults().forEach((sequence, resultDTO) -> {
			resultDTO.forEach(it -> System.out.println(getElementRepresentation(it.getElement())));

			System.out.println("-- with sequence --");

			// Find path from newest entry level system call (might not be enough though)
			var pruneSequence = sequence.stream().takeWhile(
					it -> !resultDTO.stream().map(e -> e.getElement()).collect(Collectors.toList()).contains(it))
					.collect(Collectors.toList());

			var sysCalls = pruneSequence.stream()
					.filter(e -> ((Entity) e.getElement()).eClass().getName().equals("EntryLevelSystemCall"))
					.collect(Collectors.toList());
			var lastEntryLevelSystemCall = sysCalls.stream().skip(sysCalls.stream().count() - 1).findFirst().get();

			var finalSequence = pruneSequence.stream().dropWhile(it -> !it.equals(lastEntryLevelSystemCall));

			finalSequence.forEach(it -> System.out.println(getElementRepresentation(it)));

			System.out.println("\n\n");
		});
		if (violations.getResults().isEmpty()) {
			System.out.println(" none \n");
		}

		return violations;
	}

	private EnumCharacteristicType findByName(Collection<EnumCharacteristicType> enumCharacteristicTypes, String name)
			throws JobFailedException {
		return enumCharacteristicTypes.stream().filter(ct -> ct.getName().equals(name)).findFirst()
				.orElseThrow(() -> new JobFailedException("Could not find " + name + " characteristic type."));
	}

	private ActionBasedQueryResult findAllCharacteristics(CharacteristicsQueryEngine queryEngine,
			Collection<PCMDataDictionary> dataDictionaries) {
		var allCharacteristicValues = getAllEnumCharacteristicTypes(dataDictionaries).stream()
				.flatMap(c -> c.getType().getLiterals().stream().map(l -> new CharacteristicValue(c, l)))
				.collect(Collectors.toList());

		var query = new ActionBasedQueryImpl(Objects::nonNull, allCharacteristicValues, allCharacteristicValues);
		return queryEngine.query(query);
	}

	private Collection<EnumCharacteristicType> getAllEnumCharacteristicTypes(
			Collection<PCMDataDictionary> dataDictionaries) {
		return dataDictionaries.stream().map(PCMDataDictionary::getCharacteristicTypes).flatMap(Collection::stream)
				.filter(EnumCharacteristicType.class::isInstance).map(EnumCharacteristicType.class::cast)
				.collect(Collectors.toList());
	}

	private CharacteristicsQueryEngine createQueryEngine(UsageModel usageModel, Allocation allocationModel) {
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

	private String getElementRepresentation(ActionSequenceElement<?> element) {
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
