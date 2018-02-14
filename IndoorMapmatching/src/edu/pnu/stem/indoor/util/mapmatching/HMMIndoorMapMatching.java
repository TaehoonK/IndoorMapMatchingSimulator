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
        rawCircleBufferGeometries = new Geometry[hmm.getNumOfState()];
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
    public void makeAMatrixOnlyTopology() {
        double[][] matrixA = hmm.getMatrixA();

        boolean[][] topologyGraph = indoorFeatures.getTopologyGraph();
        for (int i = 0; i < topologyGraph.length; i++) {
            for(int j = 0; j < topologyGraph.length; j++) {
                if(topologyGraph[i][j]) {
                    matrixA[i][j] = 1;
                }
            }
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

        int doorCount = 0;
        boolean[][] topologyGraph = indoorFeatures.getTopologyGraph();
        for (int i = 0; i < topologyGraph.length; i++) {
            doorCount = indoorFeatures.getCellSpace(i).getDoors().size();
            for(int j = 0; j < topologyGraph.length; j++) {
                if(topologyGraph[i][j]) {
                    if(i == j) matrixA[i][j] = staticProbability;
                    else matrixA[i][j] = (1 - staticProbability) / doorCount;
                }
            }
        }

        hmm.setMatrixA(matrixA);
    }

    /**
     * @param bufferLength
     * */
    public void makeBMatrixCellBuffer(final double bufferLength) {
        this.bufferLength = bufferLength;
        double[][] matrixB = hmm.getMatrixB();

        for(int i = 0; i < hmm.getNumOfState(); i++) {
            Geometry aGeom = indoorFeatures.getCellSpace(i).getGeom().buffer(bufferLength);

            for(int j = 0; j < hmm.getNumOfState(); j++) {
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
     * TODO: Window Size에 대한 개념 추가 필요
     * @param radius
     * @param lastCoord
     * */
    public void makeBmatrixCircleBuffer(final Coordinate lastCoord, final double radius) {
        circleSize = radius;
        double[][] matrixB = hmm.getMatrixB();

        Polygon circleBuffer = createCircle(lastCoord, circleSize);
        int[] i_list = indoorFeatures.getCellSpaceIndex(lastCoord);
        int i = i_list[0];

        if(rawCircleBufferGeometries[i] == null) {
            rawCircleBufferGeometries[i] = circleBuffer;
        }
        else {
            rawCircleBufferGeometries[i] = rawCircleBufferGeometries[i].union(circleBuffer);
        }

        for(int j = 0; j < hmm.getNumOfState(); j++) {
            Polygon cellGeom = indoorFeatures.getCellSpace(j).getGeom();
            if(cellGeom.intersects(rawCircleBufferGeometries[i])) {
                matrixB[i][j] = cellGeom.intersection(rawCircleBufferGeometries[i]).getArea();
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
     * */
    public String getMapMatchingResult(Point endPoint, String[] observationLabelList) {
        String mapMatchingResult;
        int[] observations = new int[observationLabelList.length + 1];
        for(int i = 0; i < observationLabelList.length; i++) {
            observations[i] = indoorFeatures.getCellSpaceIndex(observationLabelList[i]);
        }
        int cellSpaceIndex = realTimeMapMatching(endPoint, observations);

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
     * */
    private int realTimeMapMatching(Point endPoint, int[] observations) {
        int selectedResult = -1;

        if(observations.length == 1) {
            selectedResult = indoorFeatures.getCellSpaceIndex(endPoint.getCoordinate())[0];
            observations[0] = selectedResult;
            hmm.setInitStateP(selectedResult);
        }

        // Generate a candidate set for the last positioning point
        ArrayList<Integer> candidateSet = new ArrayList<>();
        for(int i = 0; i < indoorFeatures.getCellSpaces().size(); i++) {
            if(indoorFeatures.getCellSpace(i).getGeom().intersects(endPoint.buffer(bufferLength)))
                candidateSet.add(i);
        }

        // Select a cell index from candidate set by property of HMM evaluate results
        double maxProbability = 0;
        for (Integer candidateCell_Index : candidateSet) {
            observations[observations.length - 1] = candidateCell_Index;
            double temporalProbability = hmm.evaluate(observations);
            if (temporalProbability > maxProbability) {
                maxProbability = temporalProbability;
                selectedResult = candidateCell_Index;
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

        if(mapMatchingIndexList.isEmpty()) {
            selectedResult = dimm.getDIMMResult(tempTrajectory)[0];
            hmm.setInitStateP(selectedResult);
            mapMatchingIndexList.add(selectedResult);
        }

        // Make observation array
        int[] observations = new int[mapMatchingIndexList.size() + 1];
        for(int i = 0; i < mapMatchingIndexList.size(); i++) {
            observations[i] = mapMatchingIndexList.get(i);
        }

        // Generate a candidate set for the last positioning point
        ArrayList<Integer> candidateSet = new ArrayList<>();
        for(int i = 0; i < indoorFeatures.getCellSpaces().size(); i++) {
            if(indoorFeatures.getCellSpace(i).getGeom().intersects(tempTrajectory.getEndPoint().buffer(bufferLength)))
                candidateSet.add(i);
        }

        // Select a cell index from candidate set by property of HMM evaluate results
        double maxProbability = 0;
        for (Integer candidateCell_Index : candidateSet) {
            observations[observations.length - 1] = candidateCell_Index;
            double temporalProbability = hmm.evaluate(observations);
            if (temporalProbability > maxProbability) {
                maxProbability = temporalProbability;
                selectedResult = candidateCell_Index;
            }
        }

        return selectedResult;
    }

    /**
     *
     * @param matrix
     * */
    private double[][] normalization(double[][] matrix) {
        double rowSum = 0;
        for (int i = 0; i < matrix.length; i++) {
            rowSum = 0;
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
