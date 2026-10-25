package com.agrishield.ai.models;

public class PredictionResult implements Comparable<PredictionResult> {
    private final String label;
    private final float confidence;
    private Disease disease;

    public PredictionResult(String label, float confidence) {
        this.label = label;
        this.confidence = confidence;
    }

    public String getLabel() { return label; }
    public float getConfidence() { return confidence; }

    public int getConfidencePercentage() {
        return Math.round(confidence * 100);
    }

    public Disease getDisease() { return disease; }
    public void setDisease(Disease disease) { this.disease = disease; }

    @Override
    public int compareTo(PredictionResult other) {
        return Float.compare(other.confidence, this.confidence);
    }
}
