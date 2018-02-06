package edu.pnu.stem.indoor.feature;

import com.vividsolutions.jts.geom.*;
import org.geotools.graph.build.line.BasicLineGraphGenerator;
import org.geotools.graph.path.DijkstraShortestPathFinder;
import org.geotools.graph.path.Path;
import org.geotools.graph.structure.Edge;
import org.geotools.graph.structure.Graph;
import org.geotools.graph.structure.Node;
import org.geotools.graph.traverse.standard.DijkstraIterator;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Created by STEM_KTH on 2017-05-23.
 */
public class VisibilityGraph {
    private static VisibilityGraph baseGraph = null;
    private Graph graph = null;
    private BasicLineGraphGenerator graphGenerator = null;
    private GeometryFactory gf = null;

    public VisibilityGraph () {
        this.graphGenerator = new BasicLineGraphGenerator();
        this.gf = new GeometryFactory();
    }

    public static VisibilityGraph getBaseGraph() {
        VisibilityGraph cloneGraph = null;
        if(baseGraph != null) {
            cloneGraph = new VisibilityGraph();
            cloneGraph.addEdges(baseGraph.getEdges());
        }

        return cloneGraph;
    }

    public static void setBaseGraph(ArrayList<LineString> edges) {
        baseGraph = new VisibilityGraph();
        baseGraph.addEdges(edges);
    }

    /**
     *
     * @param edges
     * */
    public void addEdges(ArrayList<LineString> edges) {
        for (LineString ls : edges) {
            Coordinate[] coords = ls.getCoordinates();
            for(int i = 0; i < coords.length - 1; i++) {
                LineSegment lineSegment = new LineSegment();
                lineSegment.setCoordinates(coords[i], coords[i + 1]);
                graphGenerator.add(lineSegment);
            }
        }
        this.graph = graphGenerator.getGraph();

        //System.out.println("Node Count : " + graph.getNodes().size());
        //System.out.println("Edge Count : " + graph.getEdges().size());
    }

    /**
     *
     * @param coords
     * @return
     * */
    public LineString getShortestRoute(Coordinate[] coords) {
        LineString resultPath = null;

        if(coords.length == 2) {
            Node source = getNode(coords[0]);
            Node target = getNode(coords[1]);

            DijkstraShortestPathFinder pf = new DijkstraShortestPathFinder(
                    graph, source, costFunction());
            pf.calculate();
            Path path = pf.getPath(target);

            ArrayList<Coordinate> pathElement = new ArrayList<>();
            for (Object object : path) {
                if (object instanceof Node) {
                    Node node = (Node) object;
                    Coordinate coord = (Coordinate) node.getObject();
                    pathElement.add(coord);
                }
            }

            Coordinate[] resultPathCoords = new Coordinate[pathElement.size()];
            for(int i = 0; i < pathElement.size(); i++) {
                resultPathCoords[i] = pathElement.get(i);
            }
            resultPath = gf.createLineString(resultPathCoords);
        }

        return (LineString) resultPath.reverse();
    }

    /**
     *
     * @return
     * */
    private DijkstraIterator.EdgeWeighter costFunction() {
        return (e -> {
            Coordinate a = (Coordinate) e.getNodeA().getObject();
            Coordinate b = (Coordinate) e.getNodeB().getObject();
            double dx = a.x - b.x;
            double dy = a.y - b.y;

            return Math.sqrt( dx * dx + dy * dy );
        });
    }

    /**
     *
     * @param coord
     * @return
     * */
    private Node getNode(Coordinate coord) {
        Node targetNode = this.graphGenerator.getNode(coord);

        /*
        // TODO : In case : Can't find matched node
        if(targetNode == null) {
            Map nodeMap = this.graphGenerator.getNodeMap();
            Iterator keys = nodeMap.keySet().iterator();
            while(keys.hasNext()) {
                keys.next();

            }
        }
        */
        return targetNode;
    }

    /**
     *
     *
     * @return
     * */
    public ArrayList<LineString> getEdges() {
        ArrayList<LineString> graphEdges = new ArrayList<>();

        for(Object e : this.graph.getEdges()) {
            if(e instanceof  Edge) {
                Edge edge = (Edge) e;
                Coordinate[] coords = new Coordinate[] {(Coordinate) edge.getNodeA().getObject(), (Coordinate) edge.getNodeB().getObject()};
                graphEdges.add(gf.createLineString(coords));
            }
        }

        return graphEdges;
    }
}