package edu.kit.kastel.dsis.fluidtrust.datacharacteristic.analysis.jobs;

import java.util.ArrayList;
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
import org.palladiosimulator.pcm.seff.ResourceDemandingSEFF;
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
import edu.kit.kastel.dsis.fluidtrust.casestudy.pcs.analysis.dto.CharacteristicValue;
import edu.kit.kastel.dsis.fluidtrust.datacharacteristic.analysis.DataDTO;

public class PropagateCharacteristicJob extends AbstractBlackboardInteractingJob<KeyValueMDSDBlackboard> {

    private final ModelLocation usageModelLocation;
    private final ModelLocation allocationModelLocation;
    private final String modelOutputURL;

    public PropagateCharacteristicJob(ModelLocation usageModelLocation, ModelLocation allocationModelLocation,
            String modelOutputURL) {
        this.usageModelLocation = usageModelLocation;
        this.allocationModelLocation = allocationModelLocation;
        this.modelOutputURL = modelOutputURL;
    }

    @Override
    public void execute(IProgressMonitor monitor) throws JobFailedException, UserCanceledException {
        var usageModel = (UsageModel) getBlackboard().getContents(this.usageModelLocation).get(0);
        var allocationModel = (Allocation) getBlackboard().getContents(this.allocationModelLocation).get(0);
        var modelPartition = getBlackboard().getPartition(this.usageModelLocation.getPartitionID());
        var dataDictionaries = modelPartition.getElement(DictionaryPackage.eINSTANCE.getPCMDataDictionary()).stream()
                .filter(PCMDataDictionary.class::isInstance).map(PCMDataDictionary.class::cast)
                .collect(Collectors.toList());

        var dataOutputList = new ArrayList<DataDTO>();

        getCharacteristics(usageModel, allocationModel, dataDictionaries, dataOutputList);

        getBlackboard().put(this.modelOutputURL, dataOutputList);

    }

    private void getCharacteristics(UsageModel usageModel, Allocation allocationModel,
            List<PCMDataDictionary> dataDictionaries, ArrayList<DataDTO> dataOutputList) {
        var queryEngine = createQueryEngine(usageModel, allocationModel);

        var allCharacteristics = findAllCharacteristics(queryEngine, dataDictionaries);



        for (var set : allCharacteristics.getResults().entrySet()) {
            for (var queryResult : set.getValue()) {
                if (queryResult.getElement().getElement() instanceof SetVariableAction) {
                    var action = (SetVariableAction) queryResult.getElement().getElement();
                    var assemblyContext = queryResult.getElement().getContext().get(0);

                    for (var result : queryResult.getDataCharacteristics().entrySet()) {
                        var criticality = result.getValue().stream()
                                .filter(e -> e.getCharacteristicType().getName().equals("DataCriticality")).findAny();
                        var criticalityLevel = criticality.isPresent()
                                ? criticality.get().getCharacteristicLiteral().getName()
                                : "notSet";

                        var dto = new DataDTO(assemblyContext.getEntityName(), assemblyContext.getId(),
                                    ((ResourceDemandingSEFF) action.eContainer()).getDescribedService__SEFF()
                                            .getEntityName(),
                                    result.getKey(), criticalityLevel);


                        dataOutputList.add(dto);
                    }

                }

            }

        }
    }

    @Override
    public void cleanup(IProgressMonitor monitor) throws CleanupFailedException {
        // TODO Auto-generated method stub

    }

    @Override
    public String getName() {
        return "Propagate Data";
    }

    private CharacteristicsQueryEngine createQueryEngine(UsageModel usageModel, Allocation allocationModel) {
        var actionSequenceFinder = new ActionSequenceFinderImpl();
        var actionSequences = actionSequenceFinder.findActionSequencesForUsageModel(usageModel);
        var characteristicsCalculator = new CharacteristicsCalculator(allocationModel);
        var queryEngine = new CharacteristicsQueryEngine(characteristicsCalculator, actionSequences);
        return queryEngine;
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

}
