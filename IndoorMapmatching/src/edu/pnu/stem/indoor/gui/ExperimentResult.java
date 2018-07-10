package edu.pnu.stem.indoor.gui;

import java.util.HashMap;

class ExperimentResult {
    String id;
    HashMap<String, Double> accuracy;
    HashMap<String, Double> trueCount;
    int numTrajectoryPoint;
    double[] trajectoryLength;  // original | indoor filtered | ground truth
    double[] averageError;  // original | indoor filtered
    double[] varianceError; // original | indoor filtered

    ExperimentResult(String id) {
        this.id = id;
        accuracy = new HashMap<>();
        trueCount = new HashMap<>();
        trajectoryLength = new double[3];
        averageError = new double[2];
        varianceError = new double[2];
    }
}
