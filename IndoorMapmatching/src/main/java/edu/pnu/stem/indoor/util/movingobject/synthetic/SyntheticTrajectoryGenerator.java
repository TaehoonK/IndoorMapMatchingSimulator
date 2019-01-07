package edu.pnu.stem.indoor.util.movingobject.synthetic;

import edu.pnu.stem.indoor.feature.CellSpace;
import edu.pnu.stem.indoor.feature.IndoorFeatures;
import edu.pnu.stem.indoor.util.IndoorUtils;
import edu.pnu.stem.indoor.util.mapmatching.DirectIndoorMapMatching;
import edu.pnu.stem.indoor.util.parser.ChangeCoord;
import org.locationtech.jts.geom.*;

import java.util.ArrayList;
import java.util.Random;

/**
 * Created by STEM_KTH on 2019-01-04.
 * @author Taehoon Kim, Pusan National University, STEM Lab.
 */
public class SyntheticTrajectoryGenerator {
    private final double MAX_DISTANCE = 4 * ChangeCoord.CANVAS_MULTIPLE;
    private final int MAX_ERROR = (int) (5 * ChangeCoord.CANVAS_MULTIPLE);
    private final int RANDOM_COUNT = 1000;
    private final int CHOOSE_NUM = 5;

    private GeometryFactory gf;
    private IndoorFeatures indoorFeatures;
    private ArrayList<Coordinate> randomPointList;
    private Random r;

    /**
        생성자, 실내공간정보를 가진 객체인 IndoorFeatures를 이용하여 초기화
     * */
    public SyntheticTrajectoryGenerator(IndoorFeatures indoorFeatures) {
        this.indoorFeatures = indoorFeatures;
        gf = new GeometryFactory();
        randomPointList = new ArrayList<>();

        r = new Random();
        for (int i = 0; i < RANDOM_COUNT; i++) {
            double tempError = r.nextInt(MAX_ERROR);
            randomPointList.add(
                    new Coordinate(
                            tempError * Math.cos(Math.toRadians((double) r.nextInt(360))),
                            tempError * Math.sin(Math.toRadians((double) r.nextInt(360)))));
        }
    }

    /*
        Timetable의 구성요소 -> generate()
        시작 cell / 중간 cell / 이동시간
        ...
        중간 cell / 종료 cell / 이동시간

        1. 중간 cell(혹은 종료 cell)에서 임의의 좌표를 생성 -> getRandomPointCellSpace()
        1-1. 처음의 경우 시작 cell에서 임의의 좌표를 생성
        2. 두 좌표 간에 Indoor route 생성 -> getIndoorRoute()
        3. 생성된 Indoor route를 이동시간에 맞춰 이동(측위)좌표를 생성
        3-1. 생성된 거리에 비해 이동시간이 너무 짧은경우(즉, 사람이 이동할 수 없는 속도인 경우)
           설정된 최고 속도로 이동하는 좌표로 생성
        4. 생성된 이동궤적을 파일에 저장(ground truth)
        5. 각 이동 궤적에 에러 추가
        5-1. 에러의 종류에 맞춰 에러 추가 (방사형 / 누적형)
        6. 에러가 추가된 궤적을 파일에 저장(synthetic data)
    */

    /**
     * 이 함수는 입력되는 Timetable에 맞춰 합성궤적을 생성하는 함수이다.
     * @param timeTable 합성궤적 생성의 기반이 되는 정보를 저장한 객체
     * */
    public SyntheticDataElement generate(ArrayList<TimeTableElement> timeTable) {
        Coordinate startCoordinate = null;
        Coordinate endCoordinate = null;
        ArrayList<Coordinate> coordList = new ArrayList<>();

        for (TimeTableElement timeTableElement : timeTable) {
            // Step 1: TimeTableElement 에 지정된 정보에 따른 CellSpace 내부에 임의의 좌표 생성
            if(startCoordinate == null) {
                startCoordinate = getRandomPointInCellSpace(
                        indoorFeatures.getCellSpace(timeTableElement.startCellIndex));
                coordList.add(startCoordinate);
            }
            else {
                startCoordinate = endCoordinate;
            }
            endCoordinate = getRandomPointInCellSpace(
                    indoorFeatures.getCellSpace(timeTableElement.endCellIndex));

            // Step 2: 두 좌표간 Indoor route 생성
            LineString tmpTrajectory = IndoorUtils.getIndoorRoute(gf.createLineString(
                    new Coordinate[]{startCoordinate, endCoordinate}), indoorFeatures.getCellSpaces());

            // Step 3: 이동시간에 맞춰 이동 좌표 생성
            // Step 3-1: Make a trajectory to lineSegment list
            Coordinate[] coordinates = tmpTrajectory.getCoordinates();
            ArrayList<LineSegment> lineSegments = new ArrayList<>();
            for(int i = 0; i < coordinates.length - 1; i++) {
                LineSegment lineSegment = new LineSegment();
                lineSegment.setCoordinates(coordinates[i], coordinates[i + 1]);
                lineSegments.add(lineSegment);
            }

            // Step 3-2: Calculate a travel distance per unit time
            double travelDistance = tmpTrajectory.getLength() / timeTableElement.travelTime;
            while(travelDistance > MAX_DISTANCE) {
                timeTableElement.travelTime += 1;
                travelDistance = tmpTrajectory.getLength() / timeTableElement.travelTime;
            }

            // Step 3-3: Find a point related with travel distance per unit time using JTS function(pointAlong(fraction))
            // TODO: Make ground truth map matching results
            double remainDistance = travelDistance;
            double ips = 0.1;
            for(int i = 0; i < lineSegments.size();i++) {
                LineSegment lineSegment = lineSegments.get(i);

                if(remainDistance > lineSegment.getLength()) {
                    // Case 1: 남은 이동거리가 현재 직선 경로을 넘어서는 경우
                    remainDistance -= lineSegment.getLength();
                    coordList.add(lineSegment.p1);
                    continue;
                }
                else if(i == coordinates.length - 1 && Math.abs(remainDistance - lineSegment.getLength()) < ips) {
                    // Case 2: 마지막 직선 경로에서 남은 거리와 남은 경로의 길이의 차이가 입실론(ips) 미만인 경우
                    coordList.add(lineSegment.p1);
                    break;
                }
                else {
                    // Case 3: 남은 이동거리가 현재 직선 경로 내인 경우
                    double segmentLengthFraction = remainDistance / lineSegment.getLength();
                    Coordinate tmpCoordinate = lineSegment.pointAlong(segmentLengthFraction);
                    coordList.add(tmpCoordinate);

                    lineSegments.set(i, new LineSegment(tmpCoordinate, lineSegment.p1));
                    remainDistance = travelDistance;
                    i--;
                }
            }
        }
        // Step 4: Make a ground truth result
        LineString raw_trajectory = IndoorUtils.createLineString(coordList);
        DirectIndoorMapMatching dimm = new DirectIndoorMapMatching(indoorFeatures);
        int[] ground_truth = dimm.getDIMMResult(raw_trajectory);

        // Step 5: Add noise to raw trajectory
        for (int i = 0; i < coordList.size(); i++) {
            Coordinate coordinate = coordList.get(i);

            double gaussianErrorX = 0;
            double gaussianErrorY = 0;
            for (int j = 0; j < CHOOSE_NUM; j++) {
                Coordinate randomCoord = randomPointList.get(r.nextInt(RANDOM_COUNT));
                gaussianErrorX += randomCoord.getX();
                gaussianErrorY += randomCoord.getY();
            }
            coordList.set(i, new Coordinate(coordinate.getX() + gaussianErrorX/5, coordinate.getY() + gaussianErrorY/5));
        }
        LineString noise_trajectory = IndoorUtils.createLineString(coordList);
        // Step 6: Save each results to files (raw_trajectory, noise_trajectory, ground_truth)

        return new SyntheticDataElement(raw_trajectory, noise_trajectory, ground_truth);
    }

    /**
     * 이 함수는 입력받은 CellSpace 안에 임의의 좌표를 생성한다.
     * @param cellSpace 임의의 좌표를 생성할 CellSpace
     * */
    private Coordinate getRandomPointInCellSpace(CellSpace cellSpace) {
        Point resultPoint;
        Random random = new Random();
        Envelope envelope = cellSpace.getGeom().getEnvelopeInternal();
        do {
            double x = envelope.getMinX() + (envelope.getMaxX() - envelope.getMinX()) * random.nextDouble();
            double y = envelope.getMinY() + (envelope.getMaxY() - envelope.getMinY()) * random.nextDouble();
            resultPoint = gf.createPoint(new Coordinate(x,y));
        } while (!cellSpace.getGeom().covers(resultPoint));

        return resultPoint.getCoordinate();
    }

    private double getMeterDistance(LineString trajectory) {
        double distance = 0;
        Coordinate[] coordinates = trajectory.getCoordinates();
        for(int i = 0; i < coordinates.length - 1; i++) {
            distance += ChangeCoord.HaversineInM(coordinates[i], coordinates[i+1]);
        }
        return distance;
    }
}
