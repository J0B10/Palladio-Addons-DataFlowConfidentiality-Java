package edu.kit.kastel.dsis.fluidtrust.datacharacteristic.analysis;

public class DataDTO {

    private String dataID;


    private String criticality;

    public DataDTO(String assemblyContextName, String assemblyID, String method, String data, String criticality) {
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
