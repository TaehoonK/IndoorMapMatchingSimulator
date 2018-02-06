package edu.pnu.stem.indoor.gui;

import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.operation.buffer.BufferOp;
import edu.pnu.stem.indoor.feature.CellSpace;
import edu.pnu.stem.indoor.feature.IndoorFeatures;
import edu.pnu.stem.indoor.util.IndoorUtils;
import edu.pnu.stem.indoor.util.SyntheticTrajectoryGenerator;
import edu.pnu.stem.indoor.util.TimeTableElement;
import edu.pnu.stem.indoor.util.mapmatching.DirectIndoorMapMatching;
import edu.pnu.stem.indoor.util.mapmatching.HMMIndoorMapMatching;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.Random;

/**
 * Created by STEM_KTH on 2017-05-17.
 * @author Taehoon Kim, Pusan National University, STEM Lab.
 */
public class CanvasPanel extends JPanel implements MouseListener, MouseMotionListener, MouseWheelListener, KeyListener {
    private static final double SNAP_THRESHOLD = 10; //SNAP_THRESHOLD value for snapping function
    private static final double POINTRADIUS = 5; //POINTRADIUS value for draw point(circle shape)
    private static final double SCREENBUFFER = 50; //SCREENBUFFER value
    private static final int ARR_SIZE = 8;

    EditStatus currentEditStatus = null;

    private int selectedCellIndex = -1;

    private GeometryFactory gf = null;
    private IndoorFeatures indoorFeatures = null;
    private LineString trajectory = null;
    private Coordinate[] drawingCellCoords = null;
    private Coordinate trajectoryCoords = null;
    private ArrayList<LineString> relatedVisibilityEdge = null;
    private ArrayList<LineString> relatedD2DEdge = null;

    int mousePositionX;
    int mousePositionY;

    CanvasPanel() {
        gf = new GeometryFactory();
        indoorFeatures = new IndoorFeatures(gf);
        relatedVisibilityEdge = new ArrayList<>();
        relatedD2DEdge = new ArrayList<>();
        this.addMouseListener(this);
        this.addMouseMotionListener(this);
        this.addMouseWheelListener(this);
        this.addKeyListener(this);
    }

    void addCellSpace(CellSpace cellSpace) {
        indoorFeatures.addCellSpace(cellSpace);
        repaint();
    }

    public IndoorFeatures getIndoorFeatures() {
        return indoorFeatures;
    }


    void setTrajectory(LineString loadedTrajectory) {
        this.trajectory = loadedTrajectory;
        repaint();
    }

    void evaluateDirectIndoorMapMatching() {
        if(indoorFeatures != null && trajectory != null) {
            DirectIndoorMapMatching dimm = new DirectIndoorMapMatching(indoorFeatures);
            HMMIndoorMapMatching himm = new HMMIndoorMapMatching(indoorFeatures);
            System.out.println("=============original trajectory=============");
            System.out.println("=============DIMM=============");
            printMapMatchingResult(dimm.getMapMatchingResult(trajectory));
            System.out.println("=============HIMM=============");
            printMapMatchingResult(himm.getMapMatchingResult(trajectory));

            double MAX_DISTANCE = 40;
            if(trajectory.getLength() > MAX_DISTANCE) {
                LineString lineWithMaxIndoorDistance = null;
                try {
                    lineWithMaxIndoorDistance = IndoorUtils.applyIndoorDistanceFilter(trajectory, MAX_DISTANCE, indoorFeatures.getCellSpaces());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                System.out.println("=============Indoor maximum distance filtered trajectory=============");
                System.out.println("=============DIMM=============");
                printMapMatchingResult(dimm.getMapMatchingResult(lineWithMaxIndoorDistance));
                System.out.println("=============HIMM=============");
                printMapMatchingResult(himm.getMapMatchingResult(lineWithMaxIndoorDistance));
            }
        }
    }

    private void printMapMatchingResult(String[] mapMatchingResult) {
        for(int i = 0; i < trajectory.getNumPoints(); i++){
            Coordinate coord = trajectory.getCoordinateN(i);
            System.out.printf("Point Coord (%f,%f) : %s\n", coord.x, coord.y, mapMatchingResult[i]);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D)g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setStroke(new BasicStroke(2));

        /*
        g2.setFont(new Font("Serif", Font.PLAIN, 20));
        g2.drawString((mousePositionX - SCREENBUFFER) + "," + (mousePositionY - SCREENBUFFER), 20,20);
        g2.drawString(mousePositionX + "," + mousePositionY, 20,20);
        */

        if(indoorFeatures != null) {
            int cellIndex = -1;
            for (CellSpace cellSpace : indoorFeatures.getCellSpaces()) {
                Polygon polygon = cellSpace.getGeom();
                cellIndex++;
                if(currentEditStatus == EditStatus.SELECT_CELLSPACE && cellIndex == selectedCellIndex) {
                    drawLines(g2, polygon.getExteriorRing().getCoordinates(), Color.RED, true);
                    for(int i = 0; i < polygon.getNumInteriorRing(); i++) {
                        drawLines(g2, polygon.getInteriorRingN(i).getCoordinates(), Color.RED, true);
                    }
                }
                else {
                    drawLines(g2, polygon.getExteriorRing().getCoordinates(), Color.BLACK, true);
                    for(int i = 0; i < polygon.getNumInteriorRing(); i++) {
                        drawLines(g2, polygon.getInteriorRingN(i).getCoordinates(), Color.GRAY, true);
                    }
                }

                ArrayList<LineString> doors = cellSpace.getDoors();
                for (LineString lineString: doors) {
                    drawLines(g2, lineString.getCoordinates(), Color.YELLOW, false);
                    drawLines(g2, lineString.buffer(10,2).getCoordinates(), Color.RED, false);
                }

                /*
                // draw door2door graph
                if(doors.size() > 1) {
                    ArrayList<LineString> d2dgraphs = cellSpace.getDoor2doorEdges();
                    for (LineString lineString: d2dgraphs) {
                        drawLines(g2, lineString.getCoordinates(), Color.RED, false);
                    }
                }
                */
                /*
                // draw visibility graph
                ArrayList<LineString> vgraph = cellSpace.getVisibilityEdges();
                for (LineString lineString: vgraph) {
                    drawLines(g2, lineString.getCoordinates(), Color.MAGENTA, false);
                }
                */
                if(currentEditStatus == EditStatus.GET_RELATED_EDGE) {
                    for (LineString lineString: relatedVisibilityEdge) {
                        drawLines(g2, lineString.getCoordinates(), Color.GREEN, false);
                    }
                    for (LineString lineString: relatedD2DEdge) {
                        drawLines(g2, lineString.getCoordinates(), Color.CYAN, false);
                    }
                }
            }
        }
        if(trajectory != null) {
            drawArrowLines(g2, trajectory.getCoordinates(), Color.GREEN);
            System.out.println("original trajectory N : " + trajectory.getNumPoints());
            // TODO : Remove it(Temporary case)
            /*
            double MAX_DISTANCE = 40;
            if(trajectory.getLength() > MAX_DISTANCE) {
                LineString lineWithMaxIndoorDistance = null;
                try {
                    lineWithMaxIndoorDistance = IndoorUtils.applyIndoorDistanceFilter(trajectory, MAX_DISTANCE, indoorFeatures.getCellSpaces());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                drawArrowLines(g2, lineWithMaxIndoorDistance.getCoordinates(), Color.RED);
                System.out.println("corrected trajectory N : " + lineWithMaxIndoorDistance.getNumPoints());
            }
            */
        }
        if(drawingCellCoords != null) {
            if(drawingCellCoords.length == 1) {
                drawPoint(g2, drawingCellCoords[0], Color.BLUE);
            }
            else {
                drawLines(g2, drawingCellCoords, Color.BLUE, true);
            }
        }
        if(trajectoryCoords != null) {
            drawPoint(g2, trajectoryCoords, Color.GREEN);
        }
    }

    private Double addScreenBuffer(double coord) {
        return coord + SCREENBUFFER;
    }

    private void drawPoint(Graphics2D g2, Coordinate coord, Color color) {
        g2.setColor(color);
        g2.draw(new Ellipse2D.Double(addScreenBuffer(coord.x - POINTRADIUS), addScreenBuffer(coord.y - POINTRADIUS), POINTRADIUS * 2, POINTRADIUS * 2));

    }

    private void drawLines(Graphics2D g2, Coordinate[] coords, Color color, Boolean isDrawPoint) {
        g2.setColor(color);
        if(coords.length > 1) {
            for(int i = 0; i < coords.length - 1; i++){
                g2.draw(new Line2D.Double(addScreenBuffer(coords[i].x), addScreenBuffer(coords[i].y), addScreenBuffer(coords[i+1].x), addScreenBuffer(coords[i+1].y)));
                if(isDrawPoint) {
                    g2.draw(new Ellipse2D.Double(addScreenBuffer(coords[i].x - POINTRADIUS), addScreenBuffer(coords[i].y - POINTRADIUS), POINTRADIUS * 2, POINTRADIUS * 2));
                }

            }
            if(isDrawPoint) {
                g2.draw(new Ellipse2D.Double(addScreenBuffer(coords[coords.length - 1].x - POINTRADIUS), addScreenBuffer(coords[coords.length - 1].y - POINTRADIUS), POINTRADIUS * 2, POINTRADIUS * 2));
            }
        }
    }

    private void drawArrowLines(Graphics2D g2, Coordinate[] coords, Color color) {
        if (coords.length > 1) {
            for (int i = 0; i < coords.length - 1; i++) {
                drawArrowLine(g2, coords[i], coords[i+1], color);
            }
        }
    }

    private void drawArrowLine(Graphics2D g2, Coordinate startCoord, Coordinate endCoord, Color color) {
        g2.setColor(color);

        double x1 = addScreenBuffer(startCoord.x);
        double y1 = addScreenBuffer(startCoord.y);
        double x2 = addScreenBuffer(endCoord.x);
        double y2 = addScreenBuffer(endCoord.y);

        int dx = (int)(x2 - x1), dy = (int)(y2 - y1);
        double D = Math.sqrt(dx*dx + dy*dy);
        double xm = D - ARR_SIZE, xn = xm, ym = ARR_SIZE, yn = -ARR_SIZE, x;
        double sin = dy/D, cos = dx/D;

        x = xm*cos - ym*sin + x1;
        ym = xm*sin + ym*cos + y1;
        xm = x;

        x = xn*cos - yn*sin + x1;
        yn = xn*sin + yn*cos + y1;
        xn = x;

        int[] xpoints = {(int)x2, (int) xm, (int) xn};
        int[] ypoints = {(int)y2, (int) ym, (int) yn};

        g2.fillPolygon(xpoints, ypoints, 3);

        g2.draw(new Line2D.Double(x1, y1, x2, y2));
    }

    @Override
    public void keyTyped(KeyEvent e) {

    }

    @Override
    public void keyPressed(KeyEvent e) {

    }

    @Override
    public void keyReleased(KeyEvent e) {

    }

    @Override
    public void mouseClicked(MouseEvent e) {

    }

    @Override
    public void mousePressed(MouseEvent e) {
        this.requestFocus();
        int previousMouseX = e.getX() - (int)SCREENBUFFER;
        int previousMouseY = e.getY() - (int)SCREENBUFFER;

        Coordinate coord = new Coordinate(previousMouseX, previousMouseY);
        if (e.getButton() == 1) {
            if(currentEditStatus == null) {
                return;
            }
            else if(currentEditStatus == EditStatus.CREATE_CELLSPACE || currentEditStatus == EditStatus.CREATE_HOLE) {
                if (drawingCellCoords == null) {
                    drawingCellCoords = new Coordinate[1];
                    drawingCellCoords[0] = coord;
                }
                else {
                    if (drawingCellCoords[0].distance(coord) < SNAP_THRESHOLD) {
                        // if new_point is covered by first_point and SNAP_THRESHOLD buffer
                        // make polygon for cell space geometry
                        Coordinate newCoord = drawingCellCoords[0];
                        Coordinate[] realCellCoords = addCoordinate(newCoord);
                        CoordinateSequence seq = gf.getCoordinateSequenceFactory().create(realCellCoords);
                        LinearRing lr = gf.createLinearRing(seq);

                        if(currentEditStatus == EditStatus.CREATE_CELLSPACE) {
                            indoorFeatures.addCellSpace(new CellSpace(gf.createPolygon(lr)));
                        }
                        else if(currentEditStatus == EditStatus.CREATE_HOLE) {
                            selectedCellIndex = indoorFeatures.getCellSpaceIndex(new Coordinate(previousMouseX, previousMouseY))[0];
                            CellSpace cellSpace = indoorFeatures.getCellSpace(selectedCellIndex);
                            Polygon polygon = cellSpace.getGeom();
                            LinearRing exteriorRIng = gf.createLinearRing(polygon.getExteriorRing().getCoordinateSequence());
                            LinearRing[] interiorRings = new LinearRing[polygon.getNumInteriorRing() + 1];
                            for(int i = 0; i < polygon.getNumInteriorRing(); i++) {
                                interiorRings[i] = gf.createLinearRing(polygon.getInteriorRingN(i).getCoordinateSequence());
                            }
                            interiorRings[polygon.getNumInteriorRing()] = lr;
                            Polygon newGeom = gf.createPolygon(exteriorRIng, interiorRings);
                            cellSpace.setGeom(newGeom);
                        }

                        drawingCellCoords = null;
                    }
                    else {
                        Coordinate newCoord = new Coordinate(previousMouseX, previousMouseY);
                        drawingCellCoords = addCoordinate(newCoord);
                    }
                }
            }
            else if(currentEditStatus == EditStatus.CREATE_TRAJECTORY) {
                if(trajectoryCoords == null) {
                    trajectoryCoords = coord;
                    trajectory = null;
                }
                else {
                    trajectory = gf.createLineString(new Coordinate[]{trajectoryCoords, coord});
                    trajectory = indoorFeatures.getIndoorRoute(trajectory);
                    trajectoryCoords = null;
                }
            }
            else if(currentEditStatus == EditStatus.CREATE_DOOR) {
                selectedCellIndex = indoorFeatures.getCellSpaceIndex(new Coordinate(previousMouseX, previousMouseY))[0];
                Point clickedPosition = gf.createPoint(new Coordinate(previousMouseX,previousMouseY));
                Polygon polygon = indoorFeatures.getCellSpace(selectedCellIndex).getGeom();
                Coordinate[] lineStringArray = polygon.getExteriorRing().getCoordinates();

                double closestDistance = 10;
                for(int i = 0; i < lineStringArray.length - 1; i++) {
                    LineString tempLineString = gf.createLineString(new Coordinate[]{new Coordinate(lineStringArray[i].x, lineStringArray[i].y), new Coordinate(lineStringArray[i+1].x, lineStringArray[i+1].y)});
                    double tempDistance = tempLineString.distance(clickedPosition);

                    if(tempDistance < closestDistance) {
                        // TODO : Find closest point that lie on polygon
                    }
                }

            }
            else if(currentEditStatus == EditStatus.SELECT_CELLSPACE) {
                selectedCellIndex = indoorFeatures.getCellSpaceIndex(new Coordinate(previousMouseX, previousMouseY))[0];
            }
            else if(currentEditStatus == EditStatus.GET_RELATED_EDGE) {
                relatedVisibilityEdge.clear();
                relatedD2DEdge.clear();
                // find closest point
                Point targetPoint = null;
                for (CellSpace cellSpace : indoorFeatures.getCellSpaces()) {
                    if(targetPoint != null) break;

                    Polygon polygon = cellSpace.getGeom();
                    for(Coordinate geomCoord : polygon.getCoordinates()) {
                        if (geomCoord.distance(coord) < SNAP_THRESHOLD) {
                            targetPoint = gf.createPoint(geomCoord);
                            break;
                        }
                    }
                }
                // find visibility Edge and Door2Door Edge related with that point
                if(targetPoint != null) {
                    for (CellSpace cellSpace : indoorFeatures.getCellSpaces()) {
                        for(LineString lineString : cellSpace.getVisibilityEdges()){
                            if(lineString.getStartPoint().equals(targetPoint)) {
                                relatedVisibilityEdge.add(lineString);
                            }
                        }
                        for(LineString lineString : cellSpace.getDoor2doorEdges()){
                            if(lineString.getStartPoint().equals(targetPoint)) {
                                relatedD2DEdge.add(lineString);
                            }
                        }
                    }
                }
            }
        }
        repaint();
    }

    private Coordinate[] addCoordinate(Coordinate newCoord) {
        int coordsNum = drawingCellCoords.length;
        Coordinate[] realCellCoords = new Coordinate[coordsNum + 1];
        for (int i = 0; i < coordsNum; i++) {
            realCellCoords[i] = new Coordinate(drawingCellCoords[i].x, drawingCellCoords[i].y);
        }
        realCellCoords[coordsNum] = new Coordinate(newCoord.x, newCoord.y);
        return realCellCoords;
    }

    @Override
    public void mouseReleased(MouseEvent e) {

    }

    @Override
    public void mouseEntered(MouseEvent e) {

    }

    @Override
    public void mouseExited(MouseEvent e) {

    }

    @Override
    public void mouseDragged(MouseEvent e) {

    }

    @Override
    public void mouseMoved(MouseEvent e) {
/*
        mousePositionX = e.getX();
        mousePositionY = e.getY();
        repaint();
*/
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {

    }

    void syntheticTrajectoryTest() {
        ArrayList<TimeTableElement> timeTableElements = new ArrayList<>();
        Random random = new Random();
        int timeTableElementsNum = 10;
        int travelTimeUpperBound = 30;
        int travelTimeLowerBound = 5;
        int cellSpacesCount = indoorFeatures.getCellSpaces().size();

        for (int i = 0; i < timeTableElementsNum; i++) {
            int startCellIndex = random.nextInt(cellSpacesCount);
            int endCellIndex = random.nextInt(cellSpacesCount);
            int travelTime = random.nextInt(travelTimeUpperBound- travelTimeLowerBound) + travelTimeLowerBound;
            timeTableElements.add(new TimeTableElement(startCellIndex, endCellIndex, travelTime));
        }

        SyntheticTrajectoryGenerator generator = new SyntheticTrajectoryGenerator(indoorFeatures);
        trajectory = generator.generate(timeTableElements);
        repaint();
    }
}
