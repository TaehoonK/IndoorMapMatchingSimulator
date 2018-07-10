package edu.pnu.stem.indoor.util.mapmatching;

import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.util.GeometricShapeFactory;
import edu.pnu.stem.indoor.feature.IndoorFeatures;
import edu.pnu.stem.indoor.util.mapmatching.etc.HiddenMarkovModel;
import edu.pnu.stem.indoor.util.IndoorUtils;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

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
     * 관측 확률(B matrix) 만을 초기화 하는 함수
     * 전체 실험 시에 B matrix를 초기화하는 매개변수를 변경 시 초기화가 필요하여 생성
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
     *  실내 그래프의 연결성을 기반의 상태전이확률 행렬(A matrix) 설정 방법
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
     *  고정 확률(sigma) 기반의 상태전이확률 행렬(A matrix) 설정 방법
     *  실내 그래프의 연결성을 기반으로 하나 동일한 셀에 머물 확률(sigma)을 고정 값으로 설정하는 방법
     *
     * @param sigma
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
     *  실내 그래프의 홉 카운트 기반의 상태전이확률 행렬(A matrix) 설정 방법
     *  두 셀 사이의 최단 홉 개수이용하여 확률 값을 설정
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

        // TODO: 설정되는 확률 값이 1이하라 정규화를 하지 않음...필요할까?
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
     *  셀 기하의 버퍼 기하를 기반으로 관측확률 행렬(B matrix) 설정 방법
     *  버퍼 기하와 다른 셀(혹은 같은 셀)이 겹치는 영역을 관측확률 값으로 설정
     *
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
     *  측위 좌표 기준 버퍼를 기반으로 관측확률 행렬(B matrix) 설정 방법
     *  아래 3단계로 설정
     *  1. 측위 좌표가 속한 셀을 기준으로 버퍼 기하들을 UNION
     *  2. UNION한 기하를 이용하여 각 셀과 겹치는 영역을 관측확률 값으로 설정
     *  3. 정규화 수행
     *
     * @param radius
     * @param lastCoord
     * */
    public void makeBmatrixCircleBuffer(final Coordinate lastCoord, final double radius) {
        double[][] matrixB = hmm.getMatrixB();

        Polygon circleBuffer = createCircle(lastCoord, radius);
        int cellIndex = indoorFeatures.getCellSpaceIndex(lastCoord)[0];

        if(unionBufferGeom[cellIndex] == null) {
            unionBufferGeom[cellIndex] = circleBuffer;
        }
        else {
            unionBufferGeom[cellIndex] = unionBufferGeom[cellIndex].union(circleBuffer);
        }

        for(int j = 0; j < hmm.getNumState(); j++) {
            Polygon cellGeom = indoorFeatures.getCellSpace(j).getGeom();
            if(cellGeom.intersects(unionBufferGeom[cellIndex])) {
                matrixB[cellIndex][j] = cellGeom.intersection(unionBufferGeom[cellIndex]).getArea();
            }
        }

        // normalization
        matrixB = normalization(matrixB, cellIndex);

        hmm.setMatrixB(matrixB);
    }

    public void makeBmatrixCircleBuffer(final Coordinate lastCoord, final double radius, final int windowSize) {
        double[][] matrixB = hmm.getMatrixB();

        Polygon circleBuffer = createCircle(lastCoord, radius);
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
                for(int j = 0; j < hmm.getNumState(); j++) {
                    Polygon cellGeom = indoorFeatures.getCellSpace(j).getGeom();
                    if(unionBufferGeom[firstIndex] == null)
                        System.out.println("???");
                    if(cellGeom.intersects(unionBufferGeom[firstIndex])) {
                        matrixB[firstIndex][j] = cellGeom.intersection(unionBufferGeom[firstIndex]).getArea();
                    }
                }
                matrixB = normalization(matrixB, firstIndex);
            }
            cellIndexCount[firstIndex]--;
        }

        for(int j = 0; j < hmm.getNumState(); j++) {
            Polygon cellGeom = indoorFeatures.getCellSpace(j).getGeom();
            if(unionBufferGeom[lastIndex] == null)
                System.out.println("???");
            if(cellGeom.intersects(unionBufferGeom[lastIndex])) {
                matrixB[lastIndex][j] = cellGeom.intersection(unionBufferGeom[lastIndex]).getArea();
            }
        }
        matrixB = normalization(matrixB, lastIndex);

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

    @Override
    public void setIndoorFeatures(IndoorFeatures indoorFeatures) {
        this.indoorFeatures = indoorFeatures;
    }

    @Override
    public String[] getMapMatchingResult(LineString trajectory) {
        String[] mapMatchingResult = new String[trajectory.getNumPoints()];
        System.out.println("currently not used");
        /*
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
        */
        return mapMatchingResult;
    }

    /**
     *
     * @param endPoint
     * @param observationLabelList
     * @param radius
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
     *
     * @param endPoint
     * @param trajectory
     * @param radius
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
     *
     * @param endPoint
     * @param observations
     * @param radius
     * */
    private int realTimeMapMatching(Point endPoint, int[] observations, double radius) {
        // Generate a candidate set for the last positioning point
        int[] candidateIndexes = getCandidateIndexArray(endPoint, radius);

        double candidateRadius = radius;
        while(candidateIndexes.length == 0) {
            candidateRadius *= 2;
            candidateIndexes = getCandidateIndexArray(endPoint, candidateRadius);
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

    /**
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

    /**
     *
     * @param matrix
     * @param rowIndex
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
     *
     * @param centerP
     * @param radius
     * */
    private Polygon createCircle(Coordinate centerP, final double radius) {
        GeometricShapeFactory geometricShapeFactory = new GeometricShapeFactory();
        geometricShapeFactory.setNumPoints(16);
        geometricShapeFactory.setCentre(centerP);
        geometricShapeFactory.setSize(radius * 2);

        return geometricShapeFactory.createCircle();
    }

// SIG 용 함수 시작
    public String getLable(int cellIndex) {
        return indoorFeatures.getCellSpace(cellIndex).getLabel();
    }

    public int getMapMatchingResult(Point endPoint, Geometry trajectory, double radius, List<Integer> prevObservations) {
        int[] dimmResult = null, observations = null;

        // 초기 확률 및 이전 관측열 설정
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

        // 입력된 측위점에 대해 HIMM 수행
        int cellSpaceIndex = realTimeMapMatching(endPoint, observations, radius);

        // 결과를 못 찾는 경우 DIMM 결과 반환
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
// SIG 용 함수 끝
}
