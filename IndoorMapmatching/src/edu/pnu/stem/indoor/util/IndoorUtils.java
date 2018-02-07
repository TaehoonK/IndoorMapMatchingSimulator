package edu.pnu.stem.indoor.util;

import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.operation.distance.DistanceOp;
import edu.pnu.stem.indoor.feature.CellSpace;
import edu.pnu.stem.indoor.feature.VisibilityGraph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Created by STEM_KTH on 2017-06-09.
 * @author Taehoon Kim, Pusan National University, STEM Lab.
 */
public class IndoorUtils {
    public static final int BUFFER_SIZE = 10;
    public static final int EPSILON = 5;
    private static final GeometryFactory gf = new GeometryFactory();

    /**
     * This function provides an indoor route that consider the indoor space geometry for a given trajectory.
     *
     * @param trajectory It consists of two points(start and end point) that want to create an indoor route
     * @param cellSpaces List of CellSpace that contains all indoor space information in the building
     * @return Indoor route
     * */
    public static LineString getIndoorRoute(LineString trajectory, ArrayList<CellSpace> cellSpaces) {
        // TODO : Make IndoorRoute with way-points (currently make with just two points)
        LineString resultIndoorPath = null;
        int startPCellIndex = -1;
        int endPCellIndex = -1;

        Point startP = trajectory.getStartPoint();
        Point endP = trajectory.getEndPoint();

        int cellIndex = 0;
        for (CellSpace cellSpace : cellSpaces) {
            Polygon cellSpaceGeom = cellSpace.getGeom();
            /*
            possible case : locate of trajectory coordinates
            1. contain in a cell
            2. on a cell boundary
            2-1. on a cell door boundary
            */
            if (cellSpaceGeom.covers(startP) && cellSpaceGeom.covers(endP)) {
                /*
                In case : trajectory is included in same cell
                but in this case, one of the trajectory boundary point possible to located at the boundary of the cell
                If it is located at the boundary of the cell,
                Need to check it is also located at the boundary of the door or not
                Because, If it isn't located at the boundary of the door,
                The two points must be distinguished as being located in another space.
                */
                boolean isStartPOnADoor = false;
                boolean isEndPOnADoor = false;
                for(LineString doorGeom : cellSpace.getDoors()) {
                    if(doorGeom.getStartPoint().equals(startP) || doorGeom.getEndPoint().equals(startP)) {
                        isStartPOnADoor = true;
                    }
                    if(doorGeom.getStartPoint().equals(endP) || doorGeom.getEndPoint().equals(endP)) {
                        isEndPOnADoor = true;
                    }
                }
                if(!cellSpaceGeom.contains(startP) && !isStartPOnADoor) {
                    endPCellIndex = cellIndex;
                }
                else if(!cellSpaceGeom.contains(endP) && !isEndPOnADoor) {
                    startPCellIndex = cellIndex;
                }
                else {
                    startPCellIndex = endPCellIndex = cellIndex;
                    resultIndoorPath = makeIndoorRouteInCell(startP, endP, cellSpace);
                    break;
                }
            }
            else {
                if(cellSpaceGeom.covers(startP) && startPCellIndex == -1) {
                    startPCellIndex = cellIndex;
                }
                else if(cellSpaceGeom.covers(endP) && endPCellIndex == -1) {
                    endPCellIndex = cellIndex;
                }
            }

            if(startPCellIndex != -1 && endPCellIndex != -1) {
                break;
            }
            cellIndex++;
        }

        if(startPCellIndex == -1 || endPCellIndex == -1) {
            // Thick 모델의 경우 벽사이로 좌표가 입력되는 경우를 처리해줘야 한다
            if(startPCellIndex == -1) {
                startPCellIndex = getCellSpaceIndexWithEpsilon(startP.getCoordinate(), cellSpaces);
                startP = getNearestPoint(startP, cellSpaces.get(startPCellIndex).getGeom());
            }
            else {
                endPCellIndex = getCellSpaceIndexWithEpsilon(endP.getCoordinate(), cellSpaces);
                endP = getNearestPoint(endP, cellSpaces.get(endPCellIndex).getGeom());
            }

            if(startPCellIndex == -1 || endPCellIndex == -1) {
                // In case : trajectory is defined outside of the cells
                // TODO : Make alert the path of point is must include in Cell Space
                System.out.println("The coordinates were bounced off the building");
            }
            else if (startPCellIndex == endPCellIndex) {
                resultIndoorPath = makeIndoorRouteInCell(startP, endP, cellSpaces.get(startPCellIndex));
            }
            else {
                resultIndoorPath = getIndoorRoute(startPCellIndex, endPCellIndex, startP, endP, cellSpaces);
                if(resultIndoorPath == null) {
                    // In case : There is no connection between start and end
                    // 이 경우, endP에 대해서 startP가 소속된 Cell에 속하도록 보정이 필요
                    // 아래 두 가지 방법으로 가능. 현재는 2번 방법으로 구현
                    // 1. startP와 endP간의 직선을 생성, Cellboundary와 교차되는 점을 찾아 해당 점으로 보정
                    // 2. endP와 Cellboundary간 가장 가까운 점을 찾아 해당 점으로 보정
                    // 이후 같은 Cell 내에서의 indoor route를 찾는다
                    endP = getNearestPoint(endP, cellSpaces.get(startPCellIndex).getGeom());
                    resultIndoorPath = makeIndoorRouteInCell(startP, endP, cellSpaces.get(startPCellIndex));
                }
            }
        }
        else if(resultIndoorPath == null) {
            resultIndoorPath = getIndoorRoute(startPCellIndex, endPCellIndex, startP, endP, cellSpaces);
        }

        return resultIndoorPath;
    }

    /**
     * In case : trajectory cover several cell spaces
     * Consider only same floor's doors connection
     * TODO : Make door2door graph take into account several floors
     * */
    private static LineString getIndoorRoute(int startPCellIndex, int endPCellIndex, Point startP, Point endP, ArrayList<CellSpace> cellSpaces) {
        LineString resultIndoorPath = null;
        VisibilityGraph graph = new VisibilityGraph();

        if(startPCellIndex != endPCellIndex) {
            // Makes door2door graph
            if(VisibilityGraph.getBaseGraph() == null) {
                ArrayList<LineString> doors = new ArrayList<>();
                for (CellSpace cellSpace: cellSpaces) {
                    ArrayList<LineString> d2dGraph = cellSpace.getDoor2doorEdges();
                    if(d2dGraph.size() != 0) {
                        graph.addEdges(d2dGraph);
                    }
                    doors.addAll(cellSpace.getDoors());
                }
                // Determine whether the indoor model is thin or thick by door objects
                // If it is a thick model, creates edges for the door2door graph by finding separated two objects but actually same object (door).
                HashSet<LineString> interDoorGraph = new HashSet<>();
                for(int i = 0; i < doors.size(); i++) {
                    for(int j = i + 1; j <  doors.size(); j++) {
                        LineString doorA = doors.get(i);
                        LineString doorB = doors.get(j);
                        if(doorA.equals(doorB)) {
                            break; // In case: thin model
                        }
                        else {
                            if(doorA.buffer(BUFFER_SIZE,2).covers(doorB)) { // In case: thick model
                                interDoorGraph.add(gf.createLineString(new Coordinate[]{doorA.getStartPoint().getCoordinate(), doorB.getStartPoint().getCoordinate()}));
                                interDoorGraph.add(gf.createLineString(new Coordinate[]{doorA.getEndPoint().getCoordinate(), doorB.getEndPoint().getCoordinate()}));
                            }
                        }
                    }
                }
                doors.clear();
                doors.addAll(interDoorGraph);
                graph.addEdges(doors);

                VisibilityGraph.setBaseGraph(graph.getEdges());
            }
            else {
                // Reuse VisibilityGraph object using base graph
                graph = VisibilityGraph.getBaseGraph();
            }
            // Make point2door edges and reflects it to door2door graph
            ArrayList<LineString> start2doorGraph =  makePoint2DoorEdge(startP, cellSpaces.get(startPCellIndex));
            ArrayList<LineString> end2doorGraph =  makePoint2DoorEdge(endP, cellSpaces.get(endPCellIndex));
            graph.addEdges(start2doorGraph);
            graph.addEdges(end2doorGraph);
            // Get point2point shortest path using door2door graph
            Coordinate[] coords = new Coordinate[]{startP.getCoordinate(), endP.getCoordinate()};
            resultIndoorPath = graph.getShortestRoute(coords);
        }
        else {
            System.out.println("Impossible case: cell Index is same!");
        }

        return resultIndoorPath;
    }

    /**
     * A function that returns all paths from a given point to doors in a target cell spaces.
     *
     * @param point given point
     * @param targetCellSpace target cell space with doors
     * @return all path from point to doors
     * */
    private static ArrayList<LineString> makePoint2DoorEdge(Point point, CellSpace targetCellSpace) {
        ArrayList<LineString> p2dGraph = new ArrayList<>();
        ArrayList<LineString> doors = targetCellSpace.getDoors();
        for (LineString door: doors) {
            for(int i = 0; i < door.getNumPoints(); i++) {
                Point doorP = door.getPointN(i);

                // Exception Case : Ignore If the door's point and given point are the same
                if(!point.equals(doorP)) {
                    LineString point2SDoorPath = makeIndoorRouteInCell(point, doorP, targetCellSpace);
                    p2dGraph.add(point2SDoorPath);
                    p2dGraph.add((LineString) point2SDoorPath.reverse());
                }
            }
        }
        return p2dGraph;
    }

    /**
     * A function to obtain the path between start and end points.
     * This function assumes that the start and end points are in the same cell space.
     *
     * @param startP start point
     * @param endP end point
     * @param cellSpace cell space with geometric information
     * @return Indoor route between start point and end point
     * */
    private static LineString makeIndoorRouteInCell(Point startP, Point endP, CellSpace cellSpace) {
        LineString p2pIndoorPath;
        Polygon cellSpaceGeom = cellSpace.getGeom();


        if(!cellSpaceGeom.covers(startP)) {
            startP = getNearestPoint(startP, cellSpaceGeom);
        }
        else if(!cellSpaceGeom.covers(endP)) {
            endP = getNearestPoint(endP, cellSpaceGeom);
        }


        Coordinate[] coords = new Coordinate[]{startP.getCoordinate(), endP.getCoordinate()};
        LineString lineString = gf.createLineString(coords);

        if(cellSpaceGeom.contains(lineString)) {
            // In case : The Cell contains a straight line between start and end points
            p2pIndoorPath = gf.createLineString(coords);
        }
        else {
            /*
            In case : The Cell doesn't contains a straight line between start and end points
            Make indoor path using the cell's visibility graph that added temp information(start and end points)
            */
            ArrayList<LineString> temporalGraph = cellSpace.addNodetoVGraph(startP.getCoordinate(), endP.getCoordinate());
            VisibilityGraph graph = new VisibilityGraph();
            graph.addEdges(temporalGraph);
            p2pIndoorPath = graph.getShortestRoute(coords);
        }

        return p2pIndoorPath;
    }

    private static Point getNearestPoint(Point point, Polygon polygon) {
        Coordinate[] points = DistanceOp.nearestPoints(polygon, point);
        point = gf.createPoint(points[0]);
        return point;
    }

    /**
     *
     * @param targetCoordinate
     * @param cellSpaces
     * @return
     * */
    public static Integer getCellSpaceIndexWithEpsilon(Coordinate targetCoordinate, ArrayList<CellSpace> cellSpaces) {
        return getCellSpaceIndexWithEpsilon(targetCoordinate, EPSILON, cellSpaces);
    }

    /**
     *
     * @param targetCoordinate
     * @param epsilon
     * @param cellSpaces
     * @return
     * */
    public static Integer getCellSpaceIndexWithEpsilon(Coordinate targetCoordinate, double epsilon, ArrayList<CellSpace> cellSpaces) {
        Point point = gf.createPoint(targetCoordinate);
        Polygon bufferedPolygon = (Polygon) point.buffer(epsilon, 2);

        int closestCellIndex = -1;
        HashMap<Integer, Double> resultWithArea = new HashMap<>();
        for (CellSpace cellSpace : cellSpaces) {
            Polygon cellGeometry = cellSpace.getGeom();
            closestCellIndex++;
            if(cellGeometry.intersects(bufferedPolygon)){
                resultWithArea.put(closestCellIndex, cellGeometry.intersection(bufferedPolygon).getArea());
            }
        }

        double maxArea = 0;
        int selectedIndex = -1;
        for(Integer cellIndex : resultWithArea.keySet()) {
            if(maxArea < resultWithArea.get(cellIndex)) {
                maxArea = resultWithArea.get(cellIndex);
                selectedIndex = cellIndex;
            }
        }

        return selectedIndex;
    }

    /**
     * This function return the trajectory of a person as much as the maximum distance in one second to a given trajectory.
     *
     * @exception Exception If given trajectory length is shorter than given max indoor distance then throw exception
     * @param trajectory Given indoor route
     * @param maxIndoorDistance The maximum distance a person can travel per second
     * @return The movement trajectory of a person as much as the maximum movable distance in one second to a given trajectory
     * */
    public static LineString getIndoorRoute(LineString trajectory, double maxIndoorDistance) throws Exception {
        if(trajectory.getLength() < maxIndoorDistance)
            throw new Exception("Trajectory length is shorter than maxIndoorDistance");

        // Make trajectory to line segment list
        Coordinate[] coordinates = trajectory.getCoordinates();
        ArrayList<LineSegment> lineSegments = new ArrayList<>();
        for(int i = 0; i < coordinates.length - 1; i++) {
            LineSegment lineSegment = new LineSegment();
            lineSegment.setCoordinates(coordinates[i], coordinates[i + 1]);
            lineSegments.add(lineSegment);
        }

        // Find a point related with maximum indoor distance using JTS function(pointAlong(fraction))
        ArrayList<Coordinate> pathCoordinates = new ArrayList<>();
        double remainDistance = maxIndoorDistance;
        for (LineSegment lineSegment : lineSegments) {
            pathCoordinates.add(lineSegment.p0);

            if(remainDistance > lineSegment.getLength()) {
                remainDistance -= lineSegment.getLength();
            }
            else {
                double segmentLengthFraction = remainDistance / lineSegment.getLength();
                pathCoordinates.add(lineSegment.pointAlong(segmentLengthFraction));
                break;
            }
        }

        return createLineString(pathCoordinates);
    }

    /**
     * Make JTS LineString using JTS Coordinate list.
     *
     * @param coordList Coordinate list
     * @return JTS LineString made by coordinate list
     * */
    public static LineString createLineString(ArrayList<Coordinate> coordList) {
        Coordinate[] resultCoordinates = new Coordinate[coordList.size()];
        for(int i = 0; i < resultCoordinates.length; i++) {
            resultCoordinates[i] = coordList.get(i);
        }

        return gf.createLineString(resultCoordinates);
    }

    /**
     * A function that apply a filter using the maximum distance that a person can move per second for indoor route.
     * This function assumes that the sampling time of each point of a given trajectory is one second.
     * Therefore, If the length of each line segments exceeds the maximum distance, a new end point is generated.
     * It is also the start point of the next line segment.
     *
     * @param trajectory Given Indoor route
     * @param maxIndoorDistance The maximum distance a person can travel per second
     * @param cellSpaces List of CellSpace that contains all indoor space information in the building
     * @param sameCount This is a variable that determines whether the number of points constituting the newly created path
     *                  should be the same as the number op points constituting the input path. Default value is false.
     * @return Indoor route with maximum indoor distance filter
     * */
    public static LineString applyIndoorDistanceFilter(LineString trajectory, double maxIndoorDistance, ArrayList<CellSpace> cellSpaces, boolean sameCount) {
        Coordinate correctedCoordinate = null;
        ArrayList<Coordinate> pathCoordinates = new ArrayList<>();

        pathCoordinates.add(trajectory.getCoordinates()[0]);
        for(int i = 0; i < trajectory.getNumPoints() - 1; i++) {
            Coordinate startP;
            Coordinate endP = trajectory.getCoordinateN(i + 1);
            Coordinate[] trajectoryCoordinates;
            LineString trajectorySegment;

            if(correctedCoordinate == null) {
                startP = trajectory.getCoordinateN(i);
                trajectoryCoordinates = new Coordinate[] {startP, endP};
                trajectorySegment = gf.createLineString(trajectoryCoordinates);
            }
            else {
                startP = correctedCoordinate;
                trajectoryCoordinates = new Coordinate[] {startP, endP};
                trajectorySegment = gf.createLineString(trajectoryCoordinates);
                trajectorySegment = getIndoorRoute(trajectorySegment, cellSpaces);
                correctedCoordinate = null;
            }

            if(trajectorySegment.getLength() > maxIndoorDistance) {
                try {
                    LineString correctedPath = getIndoorRoute(trajectorySegment, maxIndoorDistance);
                    correctedCoordinate = correctedPath.getEndPoint().getCoordinate();
                    if(sameCount) {
                        pathCoordinates.add(correctedCoordinate);
                    }
                    else {
                        for(int j = 1; j < correctedPath.getCoordinates().length; j++){
                            Coordinate coord = correctedPath.getCoordinateN(j);
                            pathCoordinates.add(coord);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            else{
                pathCoordinates.add(endP);
            }
        }

        return createLineString(pathCoordinates);
    }

    /**
     * A function that apply a filter using the maximum distance that a person can move per second for indoor route.
     * This function assumes that the sampling time of each point of a given trajectory is one second.
     * Therefore, If the length of each line segments exceeds the maximum distance, a new end point is generated.
     * It is also the start point of the next line segment.
     *
     * @param trajectory Given Indoor route
     * @param maxIndoorDistance The maximum distance a person can travel per second
     * @param cellSpaces List of CellSpace that contains all indoor space information in the building
     * @return Indoor route with maximum indoor distance filter
     * */
    public static LineString applyIndoorDistanceFilter(LineString trajectory, double maxIndoorDistance, ArrayList<CellSpace> cellSpaces) {
       return applyIndoorDistanceFilter(trajectory, maxIndoorDistance, cellSpaces, true);
    }

    /**
     * 이 함수는 입력된 궤적에 노이즈를 더해주는 함수이다.
     *  @param trajectory 입력 궤적
     *  @param noiseRange 노이즈 범위
     * */
    public static LineString generatePathAddedNoise(LineString trajectory, final double noiseRange) {
        LineString resultPath = null;

        // Make trajectory to line segment list
        Coordinate[] coordinates = trajectory.getCoordinates();
        ArrayList<LineSegment> lineSegments = new ArrayList<>();
        for(int i = 0; i < coordinates.length - 1; i++) {
            LineSegment lineSegment = new LineSegment();
            lineSegment.setCoordinates(coordinates[i], coordinates[i + 1]);
            lineSegments.add(lineSegment);
        }

        return resultPath;
    }
}
