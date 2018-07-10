package edu.pnu.stem.indoor.feature;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Polygon;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * CellSpace is a base class for representing the indoor space.
 * The class CellSpace contains properties for space attribute and purely geometric representation of space.
 *
 * Created by STEM_KTH on 2017-05-21.
 * @author Taehoon Kim, Pusan National University, STEM Lab.
 */
public class CellSpace {
    private String label;   // Name to represent space
    private Polygon geom;   // Geometry expressing space
    private ArrayList<LineString> doors;    // An array that stores the geometry expressing door. The door is assumed to be represented by a LineString
    //private ArrayList<LineString> visibilityEdges;  // A collection of edges of the graph that represent visibility from point to point in geometry that represents space
    //private ArrayList<LineString> door2doorEdges;   // An array that stores the path between doors
    //private GeometryFactory gf;

    private CellSpace() {
        doors = new ArrayList<>();
        //visibilityEdges = new ArrayList<>();
        //door2doorEdges = new ArrayList<>();
    }

    public CellSpace(Polygon geom) {
        this();
        setGeom(geom);
    }

    public ArrayList<LineString> getDoor2doorEdges() {
        return null;//door2doorEdges;
    }

    public ArrayList<LineString> getVisibilityEdges() {
        return null;//visibilityEdges;
    }

    public ArrayList<LineString> getDoors() {
        return doors;
    }

    public Polygon getGeom() {
        return geom;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public void setGeom(Polygon geom) {
        this.geom = geom;
        setVisibilityGraphEdges();
        // TODO : Make d2d distance when add holes
    }

    /**
     * Add a new door.
     * If there are more than two doors, create and store the path between doors.
     *
     * @param newDoor new door to add
     *
     * TODO : How to reuse visibility graph? need optimization
     * */
    public void addDoors(LineString newDoor) {
        GeometryFactory gf = new GeometryFactory();
        doors.add(newDoor);
/*
        if(visibilityEdges.isEmpty()) {
            setVisibilityGraphEdges();
        }
        for(Coordinate coord : newDoor.getCoordinates()) {
            addNodetoVGraph(coord, visibilityEdges);
        }
        if(doors.size() > 1) {
            VisibilityGraph graph = new VisibilityGraph();
            graph.addEdges(visibilityEdges);

            for (LineString existdDoor : doors) {
                if(existdDoor.equals(newDoor)) continue;
                for(Coordinate fromCoord : existdDoor.getCoordinates()) {
                    for(Coordinate toCoord : newDoor.getCoordinates()) {
                        Coordinate[] coords = new Coordinate[]{fromCoord, toCoord};
                        LineString directPath = gf.createLineString(coords);

                        if(geom.covers(directPath)) {
                            door2doorEdges.add(directPath);
                            door2doorEdges.add((LineString) directPath.reverse());
                        }
                        else {
                            LineString d2dPath = graph.getShortestRoute(coords);
                            if(geom.covers(d2dPath)) {
                                door2doorEdges.add(d2dPath);
                                door2doorEdges.add((LineString) d2dPath.reverse());
                            }
                            else
                                System.out.println("d2d path isn't covered by geom");
                        }
                    }
                }
            }
        }
        */
    }

    /**
     * Make visibilityEdges.
     * visibilityEdges is a collection of edges of the graph that represent visibility from point to point in geometry that represents space.
     * */
    private void setVisibilityGraphEdges() {
        /*
        visibilityEdges.clear();
        Coordinate[] coords = geom.getCoordinates();
        for (Coordinate from : coords) {
            visibilityEdges = addNodetoVGraph(from, visibilityEdges);
        }
        */
    }

    /**
     * Add a node to VisibilityGraph.
     * Determine if the straight line from "from" to "to" coordinates is included in the geometry of the space.
     * If included, add it to visibilityEdgeList.
     *
     * @param from start coordinate of straight line
     * @param visibilityEdgeList List of edges of visible graph
     * @return added visibilityEdgeList
     * */
    private ArrayList<LineString> addNodetoVGraph(Coordinate from, ArrayList<LineString> visibilityEdgeList){
        GeometryFactory gf = new GeometryFactory();
        Coordinate[] coords = geom.getCoordinates();
        for (Coordinate to : coords) {
            if (from.equals(to)) continue;

            Coordinate[] edgeCoords = new Coordinate[] {from, to};
            LineString edge = gf.createLineString(edgeCoords);

            // Determine that cell geometry covers it(visibility line)
            if (geom.covers(edge)) {
                if(!visibilityEdgeList.contains(edge))
                    visibilityEdgeList.add(edge);
            }
        }

        return visibilityEdgeList;
    }

    /**
     * Temporarily adding nodes to visible graph.
     * Add edges if the straight line from "startP(or endP)" to coordinates of space geometry is included in the geometry of the space.
     * This function is mainly used when creating a visible graph to find the indoor path from startP to endP in cellSpace.
     *
     * @param startP start point of indoor path
     * @param endP end point of indoor path
     * @return Edge list of visible graphs reflecting temporarily added nodes
     * */
    public ArrayList<LineString> addNodetoVGraph(Coordinate startP, Coordinate endP){
        /*
        ArrayList<LineString> temporalResult = (ArrayList<LineString>) visibilityEdges.clone();
        temporalResult = addNodetoVGraph(startP, temporalResult);
        temporalResult = addNodetoVGraph(endP, temporalResult);

        return temporalResult;
        */
        return null;
    }
}
