package com.agrishield.ai.models;

import java.util.List;

public class Disease {
    private String id; // Class key, e.g. "Apple___Apple_scab"
    private String cropName;
    private String diseaseName;
    private String symptoms;
    private String causes;
    private List<String> pesticides;
    private List<String> organicRemedies;
    private List<String> precautions;
    private List<String> preventionTips;

    public Disease() {}

    public Disease(String id, String cropName, String diseaseName, String symptoms, String causes,
                   List<String> pesticides, List<String> organicRemedies,
                   List<String> precautions, List<String> preventionTips) {
        this.id = id;
        this.cropName = cropName;
        this.diseaseName = diseaseName;
        this.symptoms = symptoms;
        this.causes = causes;
        this.pesticides = pesticides;
        this.organicRemedies = organicRemedies;
        this.precautions = precautions;
        this.preventionTips = preventionTips;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCropName() { return cropName; }
    public void setCropName(String cropName) { this.cropName = cropName; }

    public String getDiseaseName() { return diseaseName; }
    public void setDiseaseName(String diseaseName) { this.diseaseName = diseaseName; }

    public String getSymptoms() { return symptoms; }
    public void setSymptoms(String symptoms) { this.symptoms = symptoms; }

    public String getCauses() { return causes; }
    public void setCauses(String causes) { this.causes = causes; }

    public List<String> getPesticides() { return pesticides; }
    public void setPesticides(List<String> pesticides) { this.pesticides = pesticides; }

    public List<String> getOrganicRemedies() { return organicRemedies; }
    public void setOrganicRemedies(List<String> organicRemedies) { this.organicRemedies = organicRemedies; }

    public List<String> getPrecautions() { return precautions; }
    public void setPrecautions(List<String> precautions) { this.precautions = precautions; }

    public List<String> getPreventionTips() { return preventionTips; }
    public void setPreventionTips(List<String> preventionTips) { this.preventionTips = preventionTips; }

    public boolean isHealthy() {
        return diseaseName != null && diseaseName.toLowerCase().contains("healthy");
    }
}
