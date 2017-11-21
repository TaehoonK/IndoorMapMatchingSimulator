package edu.pnu.stem.indoor.util.mapmatching.etc;

/**
 * Created by STEM_KTH on 2017-07-25.
 * @author Taehoon Kim, Pusan National University, STEM Lab.
 */
public class HiddenMarkovModel {
    private int numOfState;
    private int numOfObservation;
    private int[] initStateP;
    private double[][] matrixA;
    private double[][] matrixB;

    private double[][] rawMatrixB;

    public HiddenMarkovModel(int stateNum) {
        numOfState = stateNum;
        numOfObservation = numOfState;
        initStateP = new int[numOfState];
        matrixA = new double[numOfState][numOfState];
        matrixB = new double[numOfState][numOfObservation];

        rawMatrixB = new double[numOfState][numOfObservation];
    }

    public int getNumOfState() { return  numOfState; }

    public double[][] getMatrixA() {
        return matrixA;
    }

    public double[][] getMatrixB() {
        return matrixB;
    }

    public double[][] getRawMatrixB() {
        return rawMatrixB;
    }

    public void setInitStateP(int initStateIndex) {
        this.initStateP[initStateIndex] = 1;
    }

    public void setMatrixA(double[][] matrixA) {
        this.matrixA = matrixA;
    }

    public void setMatrixB(double[][] matrixB) {
        this.matrixB = matrixB;
    }

    public void setRawMatrixB(double[][] rawMatrixB) {
        this.rawMatrixB = rawMatrixB;
    }

    public void Clear(){
        for(int i = 0; i < numOfState; i++) {
            initStateP[i] = 0;
            for(int j = 0; j < numOfState; j++) {
                matrixA[i][j] = 0;
                matrixB[i][j] = 0;

                rawMatrixB[i][j] = 0;
            }
        }
    }

    /**
     * Calculates the probability that this model has generated the given sequence.
     * Evaluation problem. Given the HMM  M = (A, B, pi) and  the observation
     * sequence O = {o1, o2, ..., oK}, calculate the probability that model
     * M has generated sequence O. This can be computed efficiently using the
     * either the Viterbi or the Forward algorithms.
     * @param observations A sequence of observations
     * @return The probability that the given sequence has been generated by this model
     * */
    public double evaluate(int[] observations) {

        if(observations == null)
            throw new IllegalArgumentException("observation is null");

        if(observations.length == 0)
            return 0.0;

        return forwardAlgorithm(observations);
    }

    /**
     * Baum-Welch forward pass
     * reference : http://courses.media.mit.edu/2010fall/mas622j/ProblemSets/ps4/tutorial.pdf
     * @param observations A sequence of observations
     * @return The probability that the given sequence has been generated by this model
     * */
    private double forwardAlgorithm(int[] observations) {
        int T = observations.length;

        double[][] fwd = new double[T][numOfState];
        double[] coefficients = new double[T];

        // 1. Initialization
        for(int i = 0; i < numOfState; i++) {
            coefficients[0] += fwd[0][i] = initStateP[i] * matrixB[i][observations[0]];
        }

        // 2. Induction
        for(int t = 1; t < T; t++) {
            for(int i = 0; i < numOfState; i++) {
                double p = matrixB[i][observations[t]];
                double sum = 0.0;

                for(int j = 0; j < numOfState; j++) {
                    sum += fwd[t-1][j] * matrixA[j][i];
                }
                fwd[t][i] = sum * p;
            }
        }
        double probabilitySum = 0;
        for(int i = 0; i < numOfState; i++) {
            probabilitySum += fwd[T-1][i];
        }

        return probabilitySum;
    }
}