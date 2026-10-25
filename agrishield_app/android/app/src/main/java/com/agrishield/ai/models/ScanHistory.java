package com.agrishield.ai.models;

public class ScanHistory {
    private long id;
    private String imagePath;
    private String cropName;
    private String diseaseName;
    private float confidence;
    private long timestamp;

    public ScanHistory() {}

    public ScanHistory(long id, String imagePath, String cropName, String diseaseName, float confidence, long timestamp) {
        this.id = id;
        this.imagePath = imagePath;
        this.cropName = cropName;
        this.diseaseName = diseaseName;
        this.confidence = confidence;
        this.timestamp = timestamp;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public String getCropName() { return cropName; }
    public void setCropName(String cropName) { this.cropName = cropName; }

    public String getDiseaseName() { return diseaseName; }
    public void setDiseaseName(String diseaseName) { this.diseaseName = diseaseName; }

    public float getConfidence() { return confidence; }
    public void setConfidence(float confidence) { this.confidence = confidence; }

    public int getConfidencePercentage() { return Math.round(confidence * 100); }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
