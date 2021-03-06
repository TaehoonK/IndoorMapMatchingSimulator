package edu.pnu.stem.indoor.util.mapmatching.etc;

/**
 *
 *
 * Created by STEM_KTH on 2017-07-25.
 * @author Taehoon Kim, Pusan National University, STEM Lab.
 */
public class HiddenMarkovModel {
    private int numState;
    private int numObservation;
    private double[] initStateP;
    private double[][] matrixA;
    private double[][] matrixB;

    public HiddenMarkovModel(int stateNum) {
        numState = stateNum;
        numObservation = numState;
        initStateP = new double[numState];
        matrixA = new double[numState][numState];
        matrixB = new double[numState][numObservation];
    }

    public int getNumState() { return numState; }

    public double[][] getMatrixA() {
        return matrixA;
    }

    public double[][] getMatrixB() {
        return matrixB;
    }

    public void setInitStateP(int[] initStateIndex) {
        double probability = 1.0 / initStateIndex.length;
        for(int stateIndex : initStateIndex) {
            this.initStateP[stateIndex] = probability;
        }
    }

    public void setInitStateP(double[] initStateP) {
        for(int i = 0; i < initStateP.length; i++) {
            this.initStateP[i] = initStateP[i];
        }
    }

    public void setMatrixA(double[][] matrixA) {
        this.matrixA = matrixA;
    }

    public void setMatrixB(double[][] matrixB) {
        this.matrixB = matrixB;
    }

    /**
     *
     * */
    public void clear(){
        for(int i = 0; i < numState; i++) {
            initStateP[i] = 0;
            for(int j = 0; j < numState; j++) {
                matrixA[i][j] = 0;
                matrixB[i][j] = 0;
            }
        }
    }

    public void clearOnlyBMatrix() {
        for(int i = 0; i < numState; i++) {
            for(int j = 0; j < numState; j++) {
                matrixB[i][j] = 0;
            }
        }
    }

    /**
     * Calculates the probability that this model has generated the given sequence.
     * Evaluation problem.
     * Given the HMM  M = (A, B, pi) and the observation
       sequence O = {o1, o2, ..., oK}, calculate the probability that model
     * M has generated sequence O.
     * This can be computed efficiently using the either the Viterbi or the Forward algorithms.
     *
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
     *
     * @param observations A sequence of observations
     * @return The probability that the given sequence has been generated by this model
     * */
    private double forwardAlgorithm(int[] observations) {
        int T = observations.length;

        double[][] fwd = new double[T][numState];

        // 1. Initialization
        for(int i = 0; i < numState; i++) {
            fwd[0][i] = initStateP[i] * matrixB[i][observations[0]];
        }

        // 2. Induction
        for(int t = 1; t < T; t++) {
            for(int i = 0; i < numState; i++) {
                double p = matrixB[i][observations[t]];
                double sum = 0.0;

                if(p == 0) continue;
                for(int j = 0; j < numState; j++) {
                    sum += fwd[t-1][j] * matrixA[j][i];
                }
                fwd[t][i] = sum * p;
            }
        }
        double probabilitySum = 0;
        for(int i = 0; i < numState; i++) {
            probabilitySum += fwd[T-1][i];
        }

        return probabilitySum;
    }

    public int[] decode(int[] observations) {
        if(observations == null)
            throw new IllegalArgumentException("observation is null");

        if(observations.length == 0)
            return null;

        return viterbi(observations);
    }

    /**
     * Viterbi-forward Algorithm
     * reference : https://github.com/accord-net/framework/blob/development/Sources/Accord.Statistics/Models/Markov/HiddenMarkovModel%601.cs
     * */
    private int[] viterbi(int[] observations) {
        int T = observations.length;
        int states = numState;
        int maxState;
        double maxWeight;
        double weight;

        double[] logPi = initStateP;
        double[][] logA = matrixA;

        int[][] s = new int[states][T];
        double[][] lnFwd = new double[states][T];

        // Base
        for (int i = 0; i < states; i++) {
            lnFwd[i][0] = logPi[i] + Math.log(matrixB[i][observations[0]]);
        }

        // Induction
        for (int t = 1; t < T; t++)
        {
            for (int j = 0; j < states; j++)
            {
                maxState = 0;
                maxWeight = lnFwd[0][t - 1] + logA[0][j];

                for (int i = 1; i < states; i++)
                {
                    weight = lnFwd[i][t - 1] + logA[i][j];

                    if (weight > maxWeight)
                    {
                        maxState = i;
                        maxWeight = weight;
                    }
                }

                lnFwd[j][t] = maxWeight + matrixB[j][observations[t]];
                s[j][t] = maxState;
            }
        }

        // Find maximum value for time T-1
        maxState = -1;
        maxWeight = lnFwd[0][T - 1];

        for (int i = 1; i < states; i++)
        {
            if (lnFwd[i][T - 1] > maxWeight)
            {
                maxState = i;
                maxWeight = lnFwd[i][T - 1];
            }
        }

        if(maxState < 0) {
            maxState = observations[T-1];
        }

        // Trackback
        int[] path = new int[T];
        path[T - 1] = maxState;

        for (int t = T - 2; t >= 0; t--)
            path[t] = s[path[t + 1]][t + 1];


        // Returns the sequence probability as an out parameter
        //logLikelihood = maxWeight;

        // Returns the most likely (Viterbi path) for the given sequence
        return path;
    }
}
