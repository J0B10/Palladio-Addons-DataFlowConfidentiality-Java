package edu.kit.kastel.dsis.fluidtrust.datacharacteristic.analysis;

public class DataDTO {

    private final String dataID;

    private final String criticality;

    public DataDTO(final String assemblyContextName, final String assemblyID, final String method, final String data,
            final String criticality) {
        this.dataID = String.format("%s:%s:%s:%s", assemblyContextName, assemblyID, method, data);
        this.criticality = criticality;
    }

    public final String getDataID() {
        return this.dataID;
    }

    public final String getCriticality() {
        return this.criticality;
    }

}
