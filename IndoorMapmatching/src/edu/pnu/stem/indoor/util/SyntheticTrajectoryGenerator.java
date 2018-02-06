package edu.pnu.stem.indoor.util;

import com.vividsolutions.jts.geom.*;
import edu.pnu.stem.indoor.feature.CellSpace;
import edu.pnu.stem.indoor.feature.IndoorFeatures;

import java.util.ArrayList;
import java.util.Random;

/**
 * Created by STEM_KTH on 2018-01-10.
 * @author Taehoon Kim, Pusan National University, STEM Lab.
 */
public class SyntheticTrajectoryGenerator {
    private static final GeometryFactory gf = new GeometryFactory();
    private IndoorFeatures indoorFeatures;
    private final double MAX_DISTANCE = 4 * ChangeCoord.CANVAS_MULTIPLE;
    /**
     * 생성자, 실내공간정보를 가진 객체인 IndoorFeatures를 이용하여 초기화
     * */
    public SyntheticTrajectoryGenerator(IndoorFeatures indoorFeatures) {
        this.indoorFeatures = indoorFeatures;
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
    public LineString generate(ArrayList<TimeTableElement> timeTable) {
        Coordinate startCoordinate = null;
        Coordinate endCoordinate = null;
        ArrayList<Coordinate> coordList = new ArrayList<>();
        LineString tmpTrajectory;
        for (TimeTableElement timeTableElement : timeTable) {
            // Step1: TimeTableElement 에 지정된 정보에 따른 CellSpace 내부에 임의의 좌표 생성
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

            // Step2: 두 좌표간 Indoor route 생성
            tmpTrajectory = IndoorUtils.getIndoorRoute(gf.createLineString(
                    new Coordinate[]{startCoordinate, endCoordinate}), indoorFeatures.getCellSpaces());

            // Step3: 이동시간에 맞춰 이동 좌표 생성
            // Step3-1: Make trajectory to line segment list
            Coordinate[] coordinates = tmpTrajectory.getCoordinates();
            ArrayList<LineSegment> lineSegments = new ArrayList<>();
            for(int i = 0; i < coordinates.length - 1; i++) {
                LineSegment lineSegment = new LineSegment();
                lineSegment.setCoordinates(coordinates[i], coordinates[i + 1]);
                lineSegments.add(lineSegment);
            }

            // Step3-2: Calculate travel distance per unit time
            double travelDistance = tmpTrajectory.getLength() / timeTableElement.travelTime;
            if(travelDistance > MAX_DISTANCE) travelDistance = MAX_DISTANCE;

            // Step3-3: Find a point related with travel distance per unit time using JTS function(pointAlong(fraction))
            // TODO: Make ground truth map matching results
            double remainDistance = travelDistance;
            double ips = 0.1;
            for(int i = 0; i < lineSegments.size();i++) {
                LineSegment lineSegment = lineSegments.get(i);

                if(remainDistance > lineSegment.getLength()) {
                    remainDistance -= lineSegment.getLength();
                    continue;
                }
                else if(i == coordinates.length - 1 && Math.abs(remainDistance - lineSegment.getLength()) < ips) {
                    coordList.add(lineSegment.p1);
                    break;
                }
                else {
                    double segmentLengthFraction = remainDistance / lineSegment.getLength();
                    Coordinate tmpCoordinate = lineSegment.pointAlong(segmentLengthFraction);
                    coordList.add(tmpCoordinate);

                    lineSegments.set(i, new LineSegment(tmpCoordinate, lineSegment.p1));
                    remainDistance = travelDistance;
                    i--;
                }
            }
        }

        // TODO:
        return IndoorUtils.createLineString(coordList);
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


}
