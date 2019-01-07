package edu.pnu.stem.indoor.util.mapmatching;

import edu.pnu.stem.indoor.feature.IndoorFeatures;
import edu.pnu.stem.indoor.util.mapmatching.etc.HiddenMarkovModel;
import edu.pnu.stem.indoor.util.IndoorUtils;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.util.GeometricShapeFactory;

import java.util.ArrayList;
import java.util.List;

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
    private Geometry[] unionBufferGeom;
    private int[] cellIndexCount;
    private ArrayList<Geometry> tempRawCircleBufferGeom;
    private ArrayList<Integer> tempCellIndex;

    public HMMIndoorMapMatching(IndoorFeatures indoorFeatures){
        dimm = new DirectIndoorMapMatching(indoorFeatures);
        hmm = new HiddenMarkovModel(indoorFeatures.getCellSpaces().size());
        unionBufferGeom = new Geometry[hmm.getNumState()];
        for(int i = 0; i < unionBufferGeom.length; i++) {
            unionBufferGeom[i] = null;
        }
        cellIndexCount = new int[hmm.getNumState()];
        tempRawCircleBufferGeom = new ArrayList<>();
        tempCellIndex = new ArrayList<>();
        setIndoorFeatures(indoorFeatures);
    }

    public void setCircleSize(double radius) {
        this.circleSize = radius;
    }


    public void clear() {
        hmm.clear();
        for(int i = 0; i < unionBufferGeom.length; i++) {
            unionBufferGeom[i] = null;
        }
        cellIndexCount = new int[hmm.getNumState()];
        tempRawCircleBufferGeom.clear();
        tempCellIndex.clear();
    }

    /**
     * A function that initializes only the observation probability (B matrix)
     * In the case of repeated experiment, it is necessary to initialize when changing parameters to set B matrix
     * */
    public void clearOnlyBMatrix() {
        hmm.clearOnlyBMatrix();
        for(int i = 0; i < unionBufferGeom.length; i++) {
            unionBufferGeom[i] = null;
        }
        cellIndexCount = new int[hmm.getNumState()];
        tempRawCircleBufferGeom.clear();
        tempCellIndex.clear();
    }

    /**
     *  How to set initial state probability
     *  Creates a circular buffer based on the input position
     *  Set the area that intersects the geometry of the cell as the initial probability value
     *
     *  @param startP the input position
     * */
    public void setInitalProbability(Point startP) {
        double candidateRadius = circleSize;
        double[] initStateP = new double[hmm.getNumState()];

        boolean isSet = false;
        while (!isSet) {
            Polygon circleBuffer = (Polygon) startP.buffer(candidateRadius);
            for(int i = 0; i < indoorFeatures.getCellSpaces().size(); i++) {
                if(indoorFeatures.getCellSpace(i).getGeom().intersects(circleBuffer)) {
                    initStateP[i] = indoorFeatures.getCellSpace(i).getGeom().intersection(circleBuffer).getArea();
                    isSet = true;
                }
            }
            candidateRadius *= 2;
        }
        hmm.setInitStateP(normalization(initStateP));
    }

    /**
     *  How to set the state transition probability matrix (A matrix) based on connectivity of indoor graph
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
     *  How to set a state transition probability matrix (A matrix) based on fixed probability (sigma)
     *  How to set the probability of staying in the same cell (sigma) as a fixed value based on the connectivity of the indoor graph
     *
     * @param sigma Fixed probability
     * */
    public void makeAMatrixByStaticP(final double sigma) {
        if(sigma > 1) return;
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
                        matrixA[i][j] = sigma;
                    }
                    else {
                        matrixA[i][j] = (1.0 - sigma) / doorCount;
                        connectCount++;
                    }
                }
            }
            if(connectCount == 0) matrixA[i][i] = 0.0;
        }

        hmm.setMatrixA(matrixA);
    }

    /**
     *  How to set the state transition probability matrix (A matrix) based on hop count of indoor graph
     *  Set the probability value using the shortest hop count between two cells
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

        // TODO: The probability value to be set is 1 or less. So, the normalization is not performed....Is it necessary?
        int connectCount;
        for(int i = 0; i < hopCount.length; i++) {
            connectCount = 0;
            for (int j = 0; j < hopCount.length; j++) {
                if(hopCount[i][j] != Integer.MAX_VALUE) {
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
     *  How to Set Observation Probability Matrix (B matrix) Based on Cell Geometry Buffer Geometry
     *  The area where the buffer geometry overlaps with another cell (or the same cell) is set as the observation probability value
     *
     * @param bufferLength The length of the buffer to apply
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
     *  How to Set Observation Probability Matrix (B matrix) Based on Positioning Reference Buffer
     *  Set to Step 3 below:
     *  1. Merging buffer geometries based on the cell to which the positioning coordinates belong
     *  2. Using the merged geometry, the region overlapping each cell is set as the observation probability value
     *  3. Perform normalization
     *
     * @param lastCoord The most recently entered location coordinates
     * @param radius The radius of the original buffer to apply
     * */
    public void makeBMatrixCircleBuffer(final Coordinate lastCoord, final double radius) {
        double[][] matrixB = hmm.getMatrixB();

        Polygon circleBuffer = createCircle(lastCoord, radius);
        // TODO: Implicitly select a cell index. Need to change to choose by probabilistic method
        int cellIndex = indoorFeatures.getCellSpaceIndex(lastCoord)[0];

        if(unionBufferGeom[cellIndex] == null) {
            unionBufferGeom[cellIndex] = circleBuffer;
        }
        else {
            unionBufferGeom[cellIndex] = unionBufferGeom[cellIndex].union(circleBuffer);
        }

        matrixB = getUpdatedMatrixB(matrixB, cellIndex);

        hmm.setMatrixB(matrixB);
    }

    /**
     * How to Set Observation Probability Matrix (B matrix) Based on Positioning Reference Buffer
     * Overloading function with sliding window added
     * The difference from the existing function is whether or not the sliding window is applied
     *
     * @param lastCoord The most recently entered location coordinates
     * @param radius The radius of the original buffer to apply
     * @param windowSize The size of sliding window
     * */
    public void makeBMatrixCircleBuffer(final Coordinate lastCoord, final double radius, final int windowSize) {
        double[][] matrixB = hmm.getMatrixB();

        Polygon circleBuffer = createCircle(lastCoord, radius);
        // TODO: Implicitly select a cell index. Need to change to choose by probabilistic method
        int lastIndex = indoorFeatures.getCellSpaceIndex(lastCoord)[0];

        if(tempRawCircleBufferGeom.size() < windowSize) {
            if(unionBufferGeom[lastIndex] == null) {
                unionBufferGeom[lastIndex] = circleBuffer;
            }
            else {
                unionBufferGeom[lastIndex] = unionBufferGeom[lastIndex].union(circleBuffer);
            }
        }
        else {
            // 현재 셀 인덱스가 리스트에서 처음 나타난 경우 -> 그대로 추가 (null인 경우와 동일)
            // 현재 셀 인덱스와 동일한 인덱스가 리스트 내에 하나만 있는경우(단, 첫번째 객체는 제외) -> 이전 기하와 Union하여 활용
            // 이외의 경우 -> 현재 셀 인덱스에 해당되는 객체만 업데이트(단, 첫번째 객체는 제외)
            // 리스트의 첫번째 객체(인덱스)가 리스트 내에 하나만 있는 경우 -> 인덱스에 해당하는 확률값들 삭제
            // 이외의 경우 -> 첫번째 인덱스에 해당되는 객체만 업데이트
            int firstIndex = tempCellIndex.get(0);
            tempCellIndex.remove(0);
            tempRawCircleBufferGeom.remove(0);

            if(unionBufferGeom[lastIndex] == null) {
                unionBufferGeom[lastIndex] = circleBuffer;
            }
            else if(cellIndexCount[lastIndex] == 1) {
                if(lastIndex != firstIndex) {
                    unionBufferGeom[lastIndex] = unionBufferGeom[lastIndex].union(circleBuffer);
                }
                else {
                    unionBufferGeom[lastIndex] = circleBuffer;
                }
            }
            else {
                unionBufferGeom[lastIndex] = getUpdatedGeometry(lastIndex);
            }
            if(cellIndexCount[firstIndex] == 1) {
                if(lastIndex != firstIndex) {
                    unionBufferGeom[firstIndex] = null;
                    for(int j = 0; j < hmm.getNumState(); j++) {
                        matrixB[firstIndex][j] = 0;
                    }
                }
            }
            else {
                unionBufferGeom[firstIndex] = getUpdatedGeometry(firstIndex);
                matrixB = getUpdatedMatrixB(matrixB, firstIndex);
            }
            cellIndexCount[firstIndex]--;
        }

        matrixB = getUpdatedMatrixB(matrixB, lastIndex);

        tempRawCircleBufferGeom.add(circleBuffer);
        tempCellIndex.add(lastIndex);
        cellIndexCount[lastIndex]++;
        hmm.setMatrixB(matrixB);
    }

    public Geometry getUpdatedGeometry(int cellIndex) {
        Geometry updatedGeom = null;
        boolean isFirst = true;
        for(int i = 0; i < tempCellIndex.size(); i++) {
            if(tempCellIndex.get(i).equals(cellIndex)) {
                if(isFirst) {
                    updatedGeom = tempRawCircleBufferGeom.get(i);
                    isFirst = false;
                }
                else {
                    updatedGeom = updatedGeom.union(tempRawCircleBufferGeom.get(i));
                }
            }
        }
        return updatedGeom;
    }

    public double[][] getUpdatedMatrixB(double[][] matrixB, int cell_index) {
        for(int j = 0; j < hmm.getNumState(); j++) {
            Polygon cellGeom = indoorFeatures.getCellSpace(j).getGeom();
            if(cellGeom.intersects(unionBufferGeom[cell_index])) {
                matrixB[cell_index][j] = cellGeom.intersection(unionBufferGeom[cell_index]).getArea();
            }
        }
        matrixB = normalization(matrixB, cell_index);
        return matrixB;
    }

    @Override
    public void setIndoorFeatures(IndoorFeatures indoorFeatures) {
        this.indoorFeatures = indoorFeatures;
    }

    @Override
    public String[] getMapMatchingResult(LineString trajectory) {
        String[] mapMatchingResult = new String[trajectory.getNumPoints()];
        System.out.println("currently not used");
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
     * Create symbolic indoor map matching results
     * Map matching is performed using the map matching result generated so far with respect to the input position
     *
     * @param endPoint The input position
     * @param observationLabelList The map matching result (cell name) generated so far
     * @param radius The radius of the original buffer to apply
     * */
    public String getMapMatchingResult(Point endPoint, ArrayList<String> observationLabelList, double radius) {
        String mapMatchingResult;
        int[] observations = new int[observationLabelList.size() + 1];
        for(int i = 0; i < observationLabelList.size(); i++) {
            observations[i] = indoorFeatures.getCellSpaceIndex(observationLabelList.get(i));
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
     * Create symbolic indoor map matching results
     * Map matching is performed using the current trajectory with respect to the input position
     *
     * @param endPoint The input position
     * @param trajectory The current trajectory
     * @param radius The radius of the original buffer to apply
     * */
    public String getMapMatchingResult(Point endPoint, Geometry trajectory, double radius) {
        String mapMatchingResult;
        int[] dimmResult = null, observations = null;

        if(trajectory.getNumPoints() == 0) {
            setInitalProbability(endPoint);
            observations = new int[1];
        }
        else if(trajectory instanceof Point) {
            setInitalProbability((Point) trajectory);
            observations = new int[]{indoorFeatures.getCellSpaceIndex(trajectory.getCoordinate())[0],0};
        }
        else if(trajectory instanceof LineString){
            Point startP = ((LineString) trajectory).getStartPoint();
            setInitalProbability(startP);

            dimmResult = dimm.getDIMMResult((LineString) trajectory);
            observations = hmm.decode(dimmResult);
        }

        int cellSpaceIndex = realTimeMapMatching(endPoint, observations, radius);
        //int cellSpaceIndex = observations[observations.length - 1];

        if(cellSpaceIndex == -1) {
            mapMatchingResult = "Impossible";
        }
        else{
            mapMatchingResult = indoorFeatures.getCellSpace(cellSpaceIndex).getLabel();
        }

        return mapMatchingResult;
    }

    /**
     * A function of performing map matching on the input positioning coordinates,
     * after all the matrices of the hidden Markov model are set.
     *
     * @param endPoint The input position
     * @param observations The map matching result (cell index) generated so far
     * @param radius The radius of the original buffer to apply
     * */
    private int realTimeMapMatching(Point endPoint, int[] observations, double radius) {
        // Generate a candidate set for the last positioning point
        int[] candidateIndexes = getCandidateIndexArray(endPoint, radius);
        double candidateRadius = radius;
        while(candidateIndexes.length == 0) {
            // If the candidate set is not generated by the size of the currently set radius,
            // the radius is doubled and the iteration is repeated until the candidate set is generated.
            candidateRadius *= 2;
            candidateIndexes = getCandidateIndexArray(endPoint, candidateRadius);
        }

        // Select a cell index from candidate set by property of HMM evaluate (using Forward algorithm) results
        int selectedResult = -1;
        double maxProbability = 0.0;
        for (Integer candidateCellIndex : candidateIndexes) {
            observations[observations.length - 1] = candidateCellIndex;
            double temporalProbability = hmm.evaluate(observations);
            if (temporalProbability > maxProbability) { // Grid way with forward algorithm
                maxProbability = temporalProbability;
                selectedResult = candidateCellIndex;
            }
        }

        return selectedResult;
    }

    /**
     * A function of performing map matching on the input positioning coordinates,
     * after all the matrices of the hidden Markov model are set.
     * However, the radius is dynamically changed to a larger value,
     * compared to the distance between the previous positioning coordinate and the input positioning coordinate.
     *
     * @param tempTrajectory The current trajectory
     * @param mapMatchingIndexList The map matching result (cell index) generated so far
     * */
    private int realTimeMapMatching(LineString tempTrajectory, ArrayList<Integer> mapMatchingIndexList) {
        // Compares the distance between positioning coordinates and the size of the radius,
        // and then, sets the larger value as the radius
        double candidateRadius = tempTrajectory.getPointN(tempTrajectory.getNumPoints() - 2).distance(tempTrajectory.getEndPoint());
        if(candidateRadius < bufferLength) {
            candidateRadius = bufferLength;
        }

        // Generate a candidate set for the positioning point
        // If positioning point is first one, get map matching result using realTimeMapMatching function
        int selectedResult = -1;
        if(mapMatchingIndexList.isEmpty()) {
            Point point = tempTrajectory.getStartPoint();
            selectedResult = realTimeMapMatching(point, new int[1], candidateRadius);
            mapMatchingIndexList.add(selectedResult);
        }
        // Else, Make a observation array
        int[] observations = new int[mapMatchingIndexList.size() + 1];
        for(int i = 0; i < mapMatchingIndexList.size(); i++) {
            observations[i] = mapMatchingIndexList.get(i);
        }

        // Generate a candidate set for the last positioning point
        Point point = tempTrajectory.getEndPoint();
        int[] candidateIndexes = getCandidateIndexArray(point, candidateRadius);

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

    /**
     *
     *
     * @param point
     * @param candidateBufferRadius
     * */
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
     * Perform two-dimensional matrix normalization
     *
     * @param matrix Matrix to be normalized
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

    /**
     * Normalize only one row of a two-dimensional matrix
     *
     * @param matrix Matrix to be normalized
     * @param rowIndex Row index to be normalized
     * */
    private double[][] normalization(double[][] matrix, int rowIndex) {
        double rowSum = 0.0;
        for(int j = 0; j < matrix.length; j++) {
            rowSum += matrix[rowIndex][j];
        }
        if(rowSum != 0) {
            for(int j = 0; j < matrix.length; j++) {
                matrix[rowIndex][j] = matrix[rowIndex][j] / rowSum;
            }
        }

        return matrix;
    }

    /**
     * Perform one-dimensional matrix normalization
     *
     * @param matrix Matrix to be normalized
     * */
    private double[] normalization(double[] matrix) {
        double rowSum = 0.0;
        for(int j = 0; j < matrix.length; j++) {
            rowSum += matrix[j];
        }
        if(rowSum != 0) {
            for(int j = 0; j < matrix.length; j++) {
                matrix[j] = matrix[j] / rowSum;
            }
        }

        return matrix;
    }

    /**
     * A function that creates a circular polygon geometry
     *
     * @param centerP Center point of the circle to be created
     * @param radius Radius of circle to create
     * */
    private Polygon createCircle(Coordinate centerP, final double radius) {
        GeometricShapeFactory geometricShapeFactory = new GeometricShapeFactory();
        geometricShapeFactory.setNumPoints(16);
        geometricShapeFactory.setCentre(centerP);
        geometricShapeFactory.setSize(radius * 2);

        return geometricShapeFactory.createCircle();
    }

// Start function for SIG
    /**
     *
     * */
    public String getLable(int cellIndex) {
        return indoorFeatures.getCellSpace(cellIndex).getLabel();
    }

    /**
     *
     * */
    public int getMapMatchingResult(Point endPoint, Geometry trajectory, double radius, List<Integer> prevObservations) {
        int[] dimmResult = null, observations = null;

        // Initial probability and previous observation list settings
        if(trajectory.getNumPoints() == 0) {
            setInitalProbability(endPoint);
            observations = new int[1];
        }
        else if(trajectory instanceof Point) {
            setInitalProbability((Point) trajectory);
            observations = new int[]{indoorFeatures.getCellSpaceIndex(trajectory.getCoordinate())[0],0};
        }
        else if(trajectory instanceof LineString){
            Point startP = ((LineString) trajectory).getStartPoint();
            setInitalProbability(startP);
            observations = new int[prevObservations.size() + 1];
            for (int i = 0; i < prevObservations.size(); i++) {
                observations[i] = prevObservations.get(i);
            }
        }

        // Perform HIMM on the input positioning point
        int cellSpaceIndex = realTimeMapMatching(endPoint, observations, radius);

        // Return DIMM results if you can not find the result
        if(cellSpaceIndex == -1) {
            if(trajectory instanceof LineString){
                dimmResult = dimm.getDIMMResult((LineString) trajectory);
                if(dimmResult != null) {
                    cellSpaceIndex = dimmResult[dimmResult.length - 1];
                }
            } else if(trajectory instanceof Point){
                cellSpaceIndex = dimm.getDIMMResult((trajectory).getCoordinate());
            }
        }

        return cellSpaceIndex;
    }
// End function for SIG
}
