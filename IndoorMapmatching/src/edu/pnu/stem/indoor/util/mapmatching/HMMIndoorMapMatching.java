package edu.pnu.stem.indoor.util.mapmatching;

import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.util.GeometricShapeFactory;
import edu.pnu.stem.indoor.feature.IndoorFeatures;
import edu.pnu.stem.indoor.util.mapmatching.etc.HiddenMarkovModel;
import edu.pnu.stem.indoor.util.IndoorUtils;

import java.util.ArrayList;

/**
 *
 *
 * Created by STEM_KTH on 2017-08-01.
 * @author Taehoon Kim, Pusan National University, STEM Lab.
 * */
public class HMMIndoorMapMatching implements IndoorMapMatching {
    private double circleSize = 10;
    private double bufferLength = 10;

    private IndoorFeatures indoorFeatures;
    private DirectIndoorMapMatching dimm;
    private HiddenMarkovModel hmm;
    public Geometry[] rawCircleBufferGeometries;

    public HMMIndoorMapMatching(IndoorFeatures indoorFeatures){
        dimm = new DirectIndoorMapMatching(indoorFeatures);
        hmm = new HiddenMarkovModel(indoorFeatures.getCellSpaces().size());
        rawCircleBufferGeometries = new Geometry[hmm.getNumState()];
        for(int i = 0; i < rawCircleBufferGeometries.length; i++) {
            rawCircleBufferGeometries[i] = null;
        }
        setIndoorFeatures(indoorFeatures);
    }

    public void clear() {
        hmm.clear();
        for(int i = 0; i < rawCircleBufferGeometries.length; i++) {
            rawCircleBufferGeometries[i] = null;
        }
    }

    public void clearOnlyBMatrix() {
        hmm.clearOnlyBMatrix();
        for(int i = 0; i < rawCircleBufferGeometries.length; i++) {
            rawCircleBufferGeometries[i] = null;
        }
    }

    /**
     *
     * */
    public void makeAMatrixByTopology() {
        double[][] matrixA = hmm.getMatrixA();

        boolean[][] topologyGraph = indoorFeatures.getTopologyGraph();
        for (int i = 0; i < topologyGraph.length; i++) {
            int connectCount = 0;
            for(int j = 0; j < topologyGraph.length; j++) {
                if(topologyGraph[i][j]) {
                    matrixA[i][j] = 1;
                    if(i != j) {
                        connectCount++;
                    }
                }
            }
            if(connectCount == 0) matrixA[i][i] = 0.0;
        }

        // normalization
        matrixA = normalization(matrixA);

        hmm.setMatrixA(matrixA);
    }

    /**
     * @param staticProbability
     * */
    public void makeAMatrixByStaticP(final double staticProbability) {
        if(staticProbability > 1) return;
        double[][] matrixA = hmm.getMatrixA();

        int doorCount;
        int connectCount;
        boolean[][] topologyGraph = indoorFeatures.getTopologyGraph();
        for (int i = 0; i < topologyGraph.length; i++) {
            connectCount = 0;
            doorCount = indoorFeatures.getCellSpace(i).getDoors().size();
            for(int j = 0; j < topologyGraph.length; j++) {
                if(topologyGraph[i][j]) {
                    if(i == j) {
                        matrixA[i][j] = staticProbability;
                    }
                    else {
                        matrixA[i][j] = (1.0 - staticProbability) / doorCount;
                        connectCount++;
                    }
                }
            }
            if(connectCount == 0) matrixA[i][i] = 0.0;
        }

        hmm.setMatrixA(matrixA);
    }

    /**
     *
     * */
    public void makeAMatrixByDistance() {
        double[][] matrixA = hmm.getMatrixA();

        boolean[][] topologyGraph = indoorFeatures.getTopologyGraph();
        int[][] hopCount = new int[topologyGraph.length][topologyGraph.length];
        for (int i = 0; i < topologyGraph.length; i++) {
            for(int j = 0; j < topologyGraph.length; j++) {
                if(topologyGraph[i][j])
                    hopCount[i][j] = 1;
                else
                    hopCount[i][j] = Integer.MAX_VALUE;
            }
        }

        for(int k = 0; k < hopCount.length; k++) {
            for(int i = 0; i < hopCount.length; i++) {
                for(int j = 0; j < hopCount.length; j++) {
                    if(hopCount[i][k] == Integer.MAX_VALUE || hopCount[k][j] == Integer.MAX_VALUE)
                        continue;
                    if(hopCount[i][j] > hopCount[i][k] + hopCount[k][j]) {
                        hopCount[i][j] = hopCount[i][k] + hopCount[k][j];
                    }
                }
            }
        }

        int totalLength;
        int connectCount;
        for(int i = 0; i < hopCount.length; i++) {
            totalLength = 0;
            connectCount = 0;
            for (int j = 0; j < hopCount.length; j++) {
                if(hopCount[i][j] != Integer.MAX_VALUE)
                    totalLength += hopCount[i][j];
            }

            for (int j = 0; j < hopCount.length; j++) {
                if(hopCount[i][j] != Integer.MAX_VALUE) {
                    //double value = (double) hopCount[i][j] / totalLength;
                    matrixA[i][j] = 1.0 / hopCount[i][j];
                    if(i != j)
                        connectCount++;
                }
            }

            if(connectCount == 0) matrixA[i][i] = 0.0;
        }

        hmm.setMatrixA(matrixA);
    }

    /**
     * @param bufferLength
     * */
    public void makeBMatrixCellBuffer(final double bufferLength) {
        this.bufferLength = bufferLength;
        double[][] matrixB = hmm.getMatrixB();

        for(int i = 0; i < hmm.getNumState(); i++) {
            Geometry aGeom = indoorFeatures.getCellSpace(i).getGeom().buffer(bufferLength);

            for(int j = 0; j < hmm.getNumState(); j++) {
                Polygon bGeom = indoorFeatures.getCellSpace(j).getGeom();

                if(i == j) matrixB[i][j] = bGeom.getArea();
                else {
                    if(aGeom.intersects(bGeom)) {
                        matrixB[i][j] = aGeom.intersection(bGeom).getArea();
                    }
                }
            }
        }
        // normalization
        matrixB = normalization(matrixB);

        hmm.setMatrixB(matrixB);
    }

    /**
     * @param radius
     * @param lastCoord
     * */
    public void makeBmatrixCircleBuffer(final Coordinate lastCoord, final double radius) {
        circleSize = radius;
        double[][] matrixB = hmm.getMatrixB();

        Polygon circleBuffer = createCircle(lastCoord, circleSize);
        int[] i_list = indoorFeatures.getCellSpaceIndex(lastCoord);
        int cellIndex = i_list[0];

        if(rawCircleBufferGeometries[cellIndex] == null) {
            rawCircleBufferGeometries[cellIndex] = circleBuffer;
        }
        else {
            rawCircleBufferGeometries[cellIndex] = rawCircleBufferGeometries[cellIndex].union(circleBuffer);
        }

        for(int j = 0; j < hmm.getNumState(); j++) {
            Polygon cellGeom = indoorFeatures.getCellSpace(j).getGeom();
            if(cellGeom.intersects(rawCircleBufferGeometries[cellIndex])) {
                matrixB[cellIndex][j] = cellGeom.intersection(rawCircleBufferGeometries[cellIndex]).getArea();
            }
        }
        // normalization
        matrixB = normalization(matrixB);

        hmm.setMatrixB(matrixB);
    }

    @Override
    public void setIndoorFeatures(IndoorFeatures indoorFeatures) {
        this.indoorFeatures = indoorFeatures;
    }

    @Override
    public String[] getMapMatchingResult(LineString trajectory) {
        String[] mapMatchingResult = new String[trajectory.getNumPoints()];
        ArrayList<Integer> observationList = getRealTimeMapMatchingResult(trajectory);

        for(int i = 0; i < observationList.size(); i++) {
            int cellSpaceIndex = observationList.get(i);
            if(cellSpaceIndex == -1) {
                mapMatchingResult[i] = "Impossible";
                return null;
            }
            else{
                mapMatchingResult[i] = indoorFeatures.getCellSpace(cellSpaceIndex).getLabel();
            }
        }

        return mapMatchingResult;
    }

    /**
     * @param endPoint
     * @param observationLabelList
     * @param radius */
    public String getMapMatchingResult(Point endPoint, String[] observationLabelList, double radius) {
        String mapMatchingResult;
        int[] observations = new int[observationLabelList.length + 1];
        for(int i = 0; i < observationLabelList.length; i++) {
            observations[i] = indoorFeatures.getCellSpaceIndex(observationLabelList[i]);
        }
        int cellSpaceIndex = realTimeMapMatching(endPoint, observations, radius);

        if(cellSpaceIndex == -1) {
            mapMatchingResult = "Impossible";
        }
        else{
            mapMatchingResult = indoorFeatures.getCellSpace(cellSpaceIndex).getLabel();
        }

        return mapMatchingResult;
    }

    public String getMapMatchingResult(Point endPoint, Geometry trajectory, double radius) {
        String mapMatchingResult;
        int[] dimmResult = null, observations = null;
        if(trajectory.getNumPoints() == 0) {
            observations = new int[1];
        }
        else if(trajectory instanceof Point) {
            observations = new int[]{indoorFeatures.getCellSpaceIndex(trajectory.getCoordinate())[0],0};
        }
        else if(trajectory instanceof LineString){
            dimmResult = dimm.getDIMMResult((LineString) trajectory);
            observations = hmm.decode(dimmResult);
        }

        int cellSpaceIndex = realTimeMapMatching(endPoint, observations, radius);

        if(cellSpaceIndex == -1) {
            mapMatchingResult = "Impossible";
        }
        else{
            mapMatchingResult = indoorFeatures.getCellSpace(cellSpaceIndex).getLabel();
        }

        return mapMatchingResult;
    }

    /**
     * @param endPoint
     * @param observations
     * @param radius */
    private int realTimeMapMatching(Point endPoint, int[] observations, double radius) {
        // Generate a candidate set for the last positioning point
        int[] candidateIndexes = getCandidateIndexArray(endPoint, radius);

        double candidateRadius = radius;
        while(candidateIndexes.length == 0) {
            candidateRadius *= 2;
            candidateIndexes = getCandidateIndexArray(endPoint, candidateRadius);
        }

        // Set a initial state probability
        if(observations.length == 1) {
            hmm.setInitStateP(candidateIndexes);
        }

        // Select a cell index from candidate set by property of HMM evaluate results
        int selectedResult = -1;
        double maxProbability = 0.0;
        for (Integer candidateCellIndex : candidateIndexes) {
            observations[observations.length - 1] = candidateCellIndex;
            double temporalProbability = hmm.evaluate(observations);
            if (temporalProbability > maxProbability) {
                maxProbability = temporalProbability;
                selectedResult = candidateCellIndex;
            }
        }

        return selectedResult;
    }

    /**
     * @param trajectory
     * */
    private ArrayList<Integer> getRealTimeMapMatchingResult(LineString trajectory) {
        ArrayList<Integer> mapMatchingIndexList = new ArrayList<>();
        ArrayList<Coordinate> coordinates = new ArrayList<>();

        coordinates.add(trajectory.getCoordinateN(0));
        for(int pointIndex = 1; pointIndex < trajectory.getNumPoints(); pointIndex++) {
            coordinates.add(trajectory.getCoordinateN(pointIndex));
            LineString tempTrajectory = IndoorUtils.createLineString(coordinates);
            int observationResult = realTimeMapMatching(tempTrajectory, mapMatchingIndexList);
            mapMatchingIndexList.add(observationResult);
            if(observationResult == -1)
                break;
        }

        return mapMatchingIndexList;
    }

    /**
     *
     * @param tempTrajectory
     * @param mapMatchingIndexList
     * */
    private int realTimeMapMatching(LineString tempTrajectory, ArrayList<Integer> mapMatchingIndexList) {
        int selectedResult = -1;

        double candidataeBufferRadius = tempTrajectory.getPointN(tempTrajectory.getNumPoints() - 2).distance(tempTrajectory.getEndPoint());
        if(candidataeBufferRadius < bufferLength) {
            candidataeBufferRadius = bufferLength;
        }

        // Set a initial state probability
        if(mapMatchingIndexList.isEmpty()) {
            Point point = tempTrajectory.getStartPoint();
            int[] candidateInitStateIndexes = getCandidateIndexArray(point, candidataeBufferRadius);
            hmm.setInitStateP(candidateInitStateIndexes);

            int[] observations = new int[1];
            double maxProbability = 0.0;
            for (Integer candidateCellIndex : candidateInitStateIndexes) {
                observations[0] = candidateCellIndex;
                double temporalProbability = hmm.evaluate(observations);
                if (temporalProbability > maxProbability) {
                    maxProbability = temporalProbability;
                    selectedResult = candidateCellIndex;
                }
            }
            mapMatchingIndexList.add(selectedResult);
        }

        // Make a observation array
        int[] observations = new int[mapMatchingIndexList.size() + 1];
        for(int i = 0; i < mapMatchingIndexList.size(); i++) {
            observations[i] = mapMatchingIndexList.get(i);
        }

        // Generate a candidate set for the last positioning point
        Point point = tempTrajectory.getEndPoint();
        int[] candidateIndexes = getCandidateIndexArray(point, candidataeBufferRadius);

        // Select a cell index from candidate set by property of HMM evaluate results
        double maxProbability = 0.0;
        for (Integer candidateCellIndex : candidateIndexes) {
            observations[observations.length - 1] = candidateCellIndex;
            double temporalProbability = hmm.evaluate(observations);
            if (temporalProbability > maxProbability) {
                maxProbability = temporalProbability;
                selectedResult = candidateCellIndex;
            }
        }

        return selectedResult;
    }

    private int[] getCandidateIndexArray(Point point, double candidateBufferRadius) {
        Polygon circleBuffer = (Polygon) point.buffer(candidateBufferRadius);
        ArrayList<Integer> candidateSet = new ArrayList<>();
        for(int i = 0; i < indoorFeatures.getCellSpaces().size(); i++) {
            if(indoorFeatures.getCellSpace(i).getGeom().intersects(circleBuffer))
                candidateSet.add(i);
        }
        int[] candidateInitStateIndexes = new int[candidateSet.size()];
        for (int i = 0; i < candidateInitStateIndexes.length; i++) {
            candidateInitStateIndexes[i] = candidateSet.get(i);
        }
        return candidateInitStateIndexes;
    }

    /**
     *
     * @param matrix
     * */
    private double[][] normalization(double[][] matrix) {
        double rowSum;
        for (int i = 0; i < matrix.length; i++) {
            rowSum = 0.0;
            for(int j = 0; j < matrix.length; j++) {
                rowSum += matrix[i][j];
            }
            if(rowSum != 0) {
                for(int j = 0; j < matrix.length; j++) {
                    matrix[i][j] = matrix[i][j] / rowSum;
                }
            }
        }

        return matrix;
    }

    private Polygon createCircle(Coordinate centerP, final double radius) {
        GeometricShapeFactory geometricShapeFactory = new GeometricShapeFactory();
        geometricShapeFactory.setNumPoints(16);
        geometricShapeFactory.setCentre(centerP);
        geometricShapeFactory.setSize(radius * 2);

        return geometricShapeFactory.createCircle();
    }
}
