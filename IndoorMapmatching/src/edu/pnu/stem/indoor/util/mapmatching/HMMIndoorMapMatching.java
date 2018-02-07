package edu.pnu.stem.indoor.util.mapmatching;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.util.GeometricShapeFactory;
import edu.pnu.stem.indoor.feature.IndoorFeatures;
import edu.pnu.stem.indoor.util.mapmatching.etc.HiddenMarkovModel;
import edu.pnu.stem.indoor.util.IndoorUtils;

import java.util.ArrayList;

/**
 * Created by STEM_KTH on 2017-08-01.
 * @author Taehoon Kim, Pusan National University, STEM Lab.
 * */
public class HMMIndoorMapMatching implements IndoorMapMatching {
    private final double CIRCLE_SIZE = 10;
    private final double BUFFER_LENGTH = 10;
    private IndoorFeatures indoorFeatures;
    private DirectIndoorMapMatching dimm;
    private HiddenMarkovModel hmm;

    public HMMIndoorMapMatching(IndoorFeatures indoorFeatures){
        dimm = new DirectIndoorMapMatching(indoorFeatures);
        hmm = new HiddenMarkovModel(indoorFeatures.getCellSpaces().size());

        setIndoorFeatures(indoorFeatures);
        // Set variable of Hidden Markov Model
        makeAMatrixOnlyTopology();
        makeBMatrixOnlyCellBuffer(BUFFER_LENGTH);
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
    public void makeBMatrixOnlyCellBuffer(final double bufferLength) {
        double[][] matrixB = hmm.getRawMatrixB();

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

    @Override
    public void setIndoorFeatures(IndoorFeatures indoorFeatures) {
        this.indoorFeatures = indoorFeatures;
    }

    @Override
    public String[] getMapMatchingResult(LineString trajectory) {
        String[] mapMatchingResult = new String[trajectory.getNumPoints()];
        ArrayList<Integer> observationList = getRealTimeMapMatchingResult(trajectory);

        int[] observations = new int[observationList.size()];
        for(int i = 0; i < observationList.size(); i++) {
            observations[i] = observationList.get(i);
        }

        for(int i = 0; i < observations.length; i++) {
            int cellSpaceIndex = observations[i];
            if(cellSpaceIndex == -1) {
                mapMatchingResult[i] = "Impossible";
            }
            else{
                mapMatchingResult[i] = indoorFeatures.getCellSpace(cellSpaceIndex).getLabel();
            }
        }

        return mapMatchingResult;
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
            if(indoorFeatures.getCellSpace(i).getGeom().intersects(tempTrajectory.getEndPoint().buffer(CIRCLE_SIZE)))
                candidateSet.add(i);
        }

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

    private Geometry createCircle(Coordinate centerP, final double radius) {
        GeometricShapeFactory geometricShapeFactory = new GeometricShapeFactory();
        geometricShapeFactory.setNumPoints(16);
        geometricShapeFactory.setCentre(centerP);
        geometricShapeFactory.setSize(radius * 2);

        return geometricShapeFactory.createCircle();
    }
}
