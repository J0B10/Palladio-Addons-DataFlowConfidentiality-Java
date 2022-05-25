package edu.kit.kastel.dsis.fluidtrust.dataflow.analysis;

import java.util.List;
import java.util.stream.Collectors;

import org.palladiosimulator.dataflow.confidentiality.pcm.model.confidentiality.dictionary.PCMDataDictionary;
import org.palladiosimulator.pcm.core.entity.Entity;

import de.uka.ipd.sdq.workflow.jobs.JobFailedException;
import edu.kit.kastel.dsis.fluidtrust.casestudy.pcs.analysis.dto.ActionBasedQueryResult;
import edu.kit.kastel.dsis.fluidtrust.casestudy.pcs.analysis.dto.CharacteristicValue;

public class RunTravelPlannerAnalysisJob extends RunCustomJavaBasedAnalysisJob {

	@Override
	protected ActionBasedQueryResult findViolations(List<PCMDataDictionary> dataDictionaries,
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

}
