package edu.pnu.stem.indoor;

import com.vividsolutions.jts.geom.*;
import java.util.ArrayList;
import edu.pnu.stem.indoor.util.IndoorUtils;
import ucar.ma2.Array;

/**
 * IndoorFeatures is a class that represent the indoor space.
 *
 * Created by STEM_KTH on 2017-05-29.
 * @author Taehoon Kim, Pusan National University, STEM Lab.
 */
public class IndoorFeatures {
    private ArrayList<CellSpace> cellSpaces = null;
    private boolean[][] topologyGraph = null;
    private GeometryFactory gf = null;

    public IndoorFeatures () {
        this(new GeometryFactory());
    }

    public IndoorFeatures (GeometryFactory gf) {
        cellSpaces = new ArrayList<>();
        this.gf = gf;
    }

    public void addCellSpace(CellSpace cellSpace) {
        cellSpaces.add(cellSpace);
    }

    public ArrayList<CellSpace> getCellSpaces() {
        return cellSpaces;
    }

    public CellSpace getCellSpace(int selectedCellIndex) {
        return cellSpaces.get(selectedCellIndex);
    }

    /**
     * This function returns a label for the cell space for a given coordinate.
     * Returns null if there is no associated cell space.
     *
     * @param coordinate given coordinate
     * @return cell space label array
     * */
    public String[] getCellSpaceLabel(Coordinate coordinate) {
        String[] resultCellLabel = null;
        int[] closestCellIndexArray = getCellSpaceIndex(coordinate);

        if(closestCellIndexArray[0] != -1) {
            resultCellLabel = new String[closestCellIndexArray.length];
            for(int i = 0; i < closestCellIndexArray.length; i++) {
                resultCellLabel[i] = cellSpaces.get(closestCellIndexArray[i]).getLabel();
            }
        }

        return resultCellLabel;
    }

    /**
     * This function returns a label for the cell space for a given index.
     *
     * @param cellSpaceIndex given cell space index for cellSpaces
     * @return cell space label array
     * */
    public String getCellSpaceLabel(int cellSpaceIndex) {
        return cellSpaces.get(cellSpaceIndex).getLabel();
    }

    /**
     * This function returns a index for the cell space for a given coordinate.
     * This index refers to the index of the CellSpace ArrayList.
     * Returns -1 if there is no associated cell space.
     *
     * @param coordinate given coordinate
     * @return candidate cell space index array
     * */
    public int[] getCellSpaceIndex(Coordinate coordinate) {
        //TODO : Need to modify to operate on three-dimensional coordinate(or additional floor information)
        ArrayList<Integer> resultList = new ArrayList<>();
        int closestCellIndex = -1;
        Point point = gf.createPoint(coordinate);
        for (CellSpace cellSpace : cellSpaces) {
            Polygon polygon = cellSpace.getGeom();
            closestCellIndex++;
            if(polygon.covers(point)){
                resultList.add(closestCellIndex);
            }
        }

        int[] resultArray;
        if(resultList.isEmpty()) {
            resultArray = new int[]{-1};
        }
        else {
            resultArray = new int[resultList.size()];
            for(int i = 0; i < resultList.size(); i++) {
                resultArray[i] = resultList.get(i);
            }
        }

        return resultArray;
    }

    /**
     * This function return a topology graph of cell spaces.
     *
     * @return topology graph
     * */
    public boolean[][] getTopologyGraph() {
        if(topologyGraph == null) {
            generateTopologyGraph();
        }

        return topologyGraph;
    }

    /**
     * This function generate a topology graph of cell spaces.
     * */
    public void generateTopologyGraph() {
        // TODO : How to deal the connection between different floors
        int cellNumber = cellSpaces.size();
        topologyGraph = new boolean[cellNumber][cellNumber];

        for (int i = 0; i < cellSpaces.size(); i++) {
            for (int j = i; j < cellSpaces.size(); j++) {
                if(i == j) {
                    topologyGraph[i][j] = true;
                }
                else {
                    topologyGraph[i][j] = false;
                    topologyGraph[j][i] = false;
                }
            }
        }

        for (int i = 0; i < cellSpaces.size(); i++) {
            CellSpace from = cellSpaces.get(i);
            ArrayList<LineString> fromDoors = from.getDoors();
            for (int j = i; j < cellSpaces.size(); j++) {
                if(topologyGraph[i][j]) continue;

                CellSpace to = cellSpaces.get(j);
                ArrayList<LineString> toDoors = to.getDoors();
                for(LineString fromDoor : fromDoors) {
                    for(LineString toDoor : toDoors) {
                        if(fromDoor.equals(toDoor)){
                            topologyGraph[i][j] = true;
                            topologyGraph[j][i] = true;
                        }
                    }
                }
            }
        }
    }

    /**
     * This function provides an indoor route that consider the indoor space geometry for a given trajectory.
     *
     * @param trajectory It consists of two points(start and end point) that want to create an indoor route
     * @return Indoor route
     * */
    public LineString getIndoorRoute(LineString trajectory) {
        return IndoorUtils.getIndoorRoute(trajectory, cellSpaces);
    }
}
