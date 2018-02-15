package edu.pnu.stem.indoor.gui;

import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import edu.pnu.stem.indoor.feature.CellSpace;
import edu.pnu.stem.indoor.feature.IndoorFeatures;
import edu.pnu.stem.indoor.util.ChangeCoord;
import edu.pnu.stem.indoor.util.IndoorUtils;
import edu.pnu.stem.indoor.util.SyntheticTrajectoryGenerator;
import edu.pnu.stem.indoor.util.TimeTableElement;
import edu.pnu.stem.indoor.util.mapmatching.DirectIndoorMapMatching;
import edu.pnu.stem.indoor.util.mapmatching.HMMIndoorMapMatching;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

/**
 *
 *
 * Created by STEM_KTH on 2017-05-17.
 * @author Taehoon Kim, Pusan National University, STEM Lab.
 */
public class CanvasPanel extends JPanel implements MouseListener, MouseMotionListener, MouseWheelListener, KeyListener {
    private static final double SNAP_THRESHOLD = 10; // SNAP_THRESHOLD value for snapping function
    private static final double POINTRADIUS = 5;    // POINTRADIUS value for draw point(circle shape)
    private static final double SCREENBUFFER = 50;  // SCREENBUFFER value
    private static final int ARR_SIZE = 8;          // Arrow size for directed line
    private static final double MAX_DISTANCE = ChangeCoord.CANVAS_MULTIPLE * 3;  // The distance that humans can move per unit time

    public static int floorId;

    EditStatus currentEditStatus = null;

    private int selectedCellIndex = -1;
    private final String resultPath = "result\\";

    private GeometryFactory gf = null;
    private IndoorFeatures indoorFeatures = null;
    private LineString trajectory = null;
    private LineString trajectory_IF = null;
    private LineString trajectory_GT = null;
    private Coordinate[] drawingCellCoords = null;
    private Coordinate trajectoryCoords = null;
    private ArrayList<LineString> relatedVisibilityEdge = null;
    private ArrayList<LineString> relatedD2DEdge = null;
    private ArrayList<Polygon> circleBufferArray = null;

    int mousePositionX;
    int mousePositionY;

    CanvasPanel() {
        gf = new GeometryFactory();
        indoorFeatures = new IndoorFeatures(gf);
        relatedVisibilityEdge = new ArrayList<>();
        relatedD2DEdge = new ArrayList<>();
        circleBufferArray = new ArrayList<>();
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

    void setTrajectory_IF(LineString indoorFilteredTrajectory) {
        trajectory_IF = indoorFilteredTrajectory;
        repaint();
    }

    void setTrajectory_GT(LineString groundTruthTrajectory) {
        trajectory_GT = groundTruthTrajectory;
        repaint();
    }

    void evaluateIndoorMapMatching(JTextPane textPaneOriginal) {
        StringBuilder result = new StringBuilder();
        if(indoorFeatures != null && trajectory != null) {
            DirectIndoorMapMatching dimm = new DirectIndoorMapMatching(indoorFeatures);
            HMMIndoorMapMatching himm = new HMMIndoorMapMatching(indoorFeatures);
            result.append("==original trajectory==\n");
            result.append("==DIMM==\n");
            printMapMatchingResults(result, dimm.getMapMatchingResult(trajectory));
            result.append("==HIMM==\n");
            himm.makeAMatrixOnlyTopology();
            himm.makeBMatrixCellBuffer(ChangeCoord.CANVAS_MULTIPLE);
            String[] mapMatchingResults =himm.getMapMatchingResult(trajectory);
            //String[] mapMatchingResults = getResultFromHIMMwithCircleBuffer(himm, trajectory, ChangeCoord.CANVAS_MULTIPLE);
            printMapMatchingResults(result, mapMatchingResults);


            // TODO: Delete this code
            // For visualize circle buffer for original trajectory
            /*
            for(Geometry geometry : himm.rawCircleBufferGeometries) {
                if(geometry instanceof Polygon)
                    circleBufferArray.add((Polygon)geometry);
                else if(geometry instanceof MultiPolygon) {
                    MultiPolygon multiPolygon = (MultiPolygon) geometry;
                    // ??
                }
            }
            */
            LineString lineWithMaxIndoorDistance = null;
            try {
                lineWithMaxIndoorDistance = IndoorUtils.applyIndoorDistanceFilter(trajectory, MAX_DISTANCE, indoorFeatures.getCellSpaces());
                setTrajectory_IF(lineWithMaxIndoorDistance);
            } catch (Exception e) {
                e.printStackTrace();
            }
            result.append("==Indoor distance filtered trajectory==\n");
            result.append("==DIMM==\n");
            System.out.print("==Indoor distance filtered trajectory==\n");
            printMapMatchingResults(result, dimm.getMapMatchingResult(trajectory_IF));
            System.out.print("==end==\n");
            result.append("==HIMM==\n");
            himm.makeAMatrixOnlyTopology();
            //himm.makeBMatrixCellBuffer(ChangeCoord.CANVAS_MULTIPLE);
            //printMapMatchingResults(result, himm.getMapMatchingResult(trajectory_IF));
            mapMatchingResults = getResultFromHIMMwithCircleBuffer(himm, trajectory_IF, ChangeCoord.CANVAS_MULTIPLE);
            printMapMatchingResults(result, mapMatchingResults);

            textPaneOriginal.setText(result.toString());

            //System.out.println(result);
            for(int i = 0; i < trajectory.getNumPoints(); i++){
                Coordinate coord = trajectory.getCoordinateN(i);
                Coordinate coord_IF = trajectory_IF.getCoordinateN(i);
                System.out.printf("%d th point\n", i);
                System.out.printf("Point Coord (%f,%f) : %s\n", coord.x, coord.y, dimm.getMapMatchingResult(trajectory)[i]);
                System.out.printf("IF Point Coord (%f,%f) : %s\n", coord_IF.x, coord_IF.y, dimm.getMapMatchingResult(trajectory_IF)[i]);
                System.out.println();
            }
        }
    }

    void getGroundTruthResult(JTextPane textPaneOriginal) {
        StringBuilder result = new StringBuilder();
        if(indoorFeatures != null && trajectory_GT != null) {
            DirectIndoorMapMatching dimm = new DirectIndoorMapMatching(indoorFeatures);
            result.append("==Ground truth==\n");
            printMapMatchingResults(result, dimm.getMapMatchingResult(trajectory_GT));
        }
        // Calculate a average error between ground truth and positioning trajectory
        if(trajectory != null && trajectory_IF != null) {
            double error_Original = 0;
            double error_IndoorFilter = 0;
            for(int i = 0; i < trajectory.getNumPoints(); i++){
                Coordinate gtCoord = trajectory_GT.getCoordinateN(i);
                Coordinate pCoord = trajectory.getCoordinateN(i);
                Coordinate ifCoord = trajectory_IF.getCoordinateN(i);

                error_Original += gtCoord.distance(pCoord);
                error_IndoorFilter += gtCoord.distance(ifCoord);
            }
            result.append("==Average Error==\n");
            result.append("Number of Points: " + trajectory.getNumPoints() + "\n");
            result.append("Original: " + error_Original/trajectory.getNumPoints() + "\n");
            result.append("Indoor Filter: " + error_IndoorFilter/trajectory.getNumPoints() + "\n");
        }
        result.append("==End==\n");

        String originalText = textPaneOriginal.getText();
        textPaneOriginal.setText(originalText + result.toString());
    }

    private void printMapMatchingResults(StringBuilder result, String[] mapMatchingResult) {
        // TODO: 사실 mapMatchingResult 배열의 크기와 trajectory의 개수가 다를수 있지만...동일하게 취급(결과 정리의 편의를 위해)
        if(mapMatchingResult != null) {
            for(int i = 0; i < trajectory.getNumPoints(); i++){
                result.append(mapMatchingResult[i] + "\n");
            }
        }
    }

    private void printMapMatchingResult(String[] mapMatchingResult) {
        if(mapMatchingResult != null) {
            for(int i = 0; i < trajectory.getNumPoints(); i++){
                Coordinate coord = trajectory.getCoordinateN(i);
                //System.out.printf("Point Coord (%f,%f) : %s\n", coord.x, coord.y, mapMatchingResult[i]);
                System.out.printf("%s\n", mapMatchingResult[i]);
            }
        }
    }

    ExperimentResult evaluateSIMM_Excel(String fileID, ArrayList<String> keyList) {
        final int BUFFER_REPETITION = 5;
        ExperimentResult experimentResult = new ExperimentResult(fileID);
        Workbook workbook = new XSSFWorkbook();
        if(indoorFeatures != null && trajectory != null) {
            DirectIndoorMapMatching dimm = new DirectIndoorMapMatching(indoorFeatures);
            HMMIndoorMapMatching himm = new HMMIndoorMapMatching(indoorFeatures);
            Sheet resultSheet = workbook.createSheet("Result");

            // Make SIMM result from original trajectory
            Row resultSheetRow = resultSheet.createRow(0);
            resultSheetRow.createCell(0).setCellValue("original trajectory");
            getEvaluateResults(BUFFER_REPETITION, dimm, himm, trajectory, resultSheet);
            System.out.println("original trajectory end");

            // Make SIMM result from indoor distance filtered trajectory
            LineString lineWithMaxIndoorDistance;
            try {
                lineWithMaxIndoorDistance = IndoorUtils.applyIndoorDistanceFilter(trajectory, MAX_DISTANCE, indoorFeatures.getCellSpaces());
                setTrajectory_IF(lineWithMaxIndoorDistance);
            } catch (Exception e) {
                e.printStackTrace();
            }
            resultSheetRow = resultSheet.createRow(resultSheet.getLastRowNum() + 1);
            resultSheetRow.createCell(0).setCellValue("Indoor distance filtered trajectory");
            getEvaluateResults(BUFFER_REPETITION, dimm, himm, trajectory_IF, resultSheet);
            System.out.println("Indoor distance filtered trajectory end");

            // Make ground truth result using DIMM and evaluate all result
            if(keyList.isEmpty())
                experimentResult = getGroundTruthResult_Excel(workbook, experimentResult, keyList);
            else
                experimentResult = getGroundTruthResult_Excel(workbook, experimentResult, null);

            // Check a directory is exist or not
            File dir = new File(resultPath);
            if(!dir.exists()){
                dir.mkdirs();
            }

            // Write all result in a excel file
            FileOutputStream outFile;
            try {
                outFile = new FileOutputStream(resultPath + "Result_" + fileID + ".xlsx");
                workbook.write(outFile);
                outFile.close();
                System.out.println("Result_" + fileID + " write complete");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return experimentResult;
    }

    private void getEvaluateResults(int BUFFER_REPETITION, DirectIndoorMapMatching dimm, HMMIndoorMapMatching himm, LineString trajectory, Sheet sheet) {
        Row row;
        row = sheet.createRow(sheet.getLastRowNum() + 1);
        row.createCell(0).setCellValue("DIMM");
        for(int i = 0; i < trajectory.getNumPoints(); i++){
            row.createCell(i+1).setCellValue(dimm.getMapMatchingResult(trajectory)[i]);
        }

        // A matrix : based on topology graph
        // B matrix : based on Cell buffer
        row = sheet.createRow(sheet.getLastRowNum() + 1);
        row.createCell(0).setCellValue("HIMM, A matrix : Topology graph / B matrix : Cell buffer");
        himm.makeAMatrixOnlyTopology();
        double radius = 0.5;
        for(int i = 0; i < BUFFER_REPETITION; i++) {
            himm.makeBMatrixCellBuffer(ChangeCoord.CANVAS_MULTIPLE * radius);
            row = sheet.createRow(sheet.getLastRowNum() + 1);
            row.createCell(0).setCellValue("Radius(m): " + radius);

            String[] mapMatchingResults = himm.getMapMatchingResult(trajectory);
            if(mapMatchingResults != null) {
                for(int j = 0; j < trajectory.getNumPoints(); j++){
                    row.createCell(j+1).setCellValue(mapMatchingResults[j]);
                }
            }
            else {
                row.createCell(1).setCellValue("Impossible");
            }
            radius += 0.5;
        }
        System.out.println("HIMM, A matrix : Topology graph / B matrix : Cell buffer end");

        // A matrix : based on static P
        // B matrix : based on Cell buffer
        row = sheet.createRow(sheet.getLastRowNum() + 1);
        row.createCell(0).setCellValue("HIMM, A matrix : Static P / B matrix : Cell buffer");

        for(double i = 0.1; i < 1; i += 0.2) {
            himm.makeAMatrixByStaticP(i);
            radius = 0.5;
            for(int j = 0; j < BUFFER_REPETITION; j++) {
                himm.makeBMatrixCellBuffer(ChangeCoord.CANVAS_MULTIPLE * radius);
                row = sheet.createRow(sheet.getLastRowNum() + 1);
                row.createCell(0).setCellValue("P: " + i + " / Radius(m): " + radius);

                String[] mapMatchingResults = himm.getMapMatchingResult(trajectory);
                if(mapMatchingResults != null) {
                    for(int k = 0; k < trajectory.getNumPoints(); k++){
                        row.createCell(k+1).setCellValue(mapMatchingResults[k]);
                    }
                }
                else {
                    row.createCell(1).setCellValue("Impossible");
                }
                radius += 0.5;
            }
        }
        System.out.println("HIMM, A matrix : Static P / B matrix : Cell buffer end");

        // A matrix : based on topology graph
        // B matrix : based on circle buffer
        row = sheet.createRow(sheet.getLastRowNum() + 1);
        row.createCell(0).setCellValue("HIMM, A matrix : Topology graph / B matrix : Circle buffer");
        radius = 0.5;
        himm.makeAMatrixOnlyTopology();
        for(int i = 0; i < BUFFER_REPETITION; i++) {
            row = sheet.createRow(sheet.getLastRowNum() + 1);
            row.createCell(0).setCellValue("Radius(m): " + radius);
            String[] mapMatchingResults = getResultFromHIMMwithCircleBuffer(himm, trajectory, ChangeCoord.CANVAS_MULTIPLE * radius);
            if(mapMatchingResults != null) {
                for(int j = 0; j < trajectory.getNumPoints(); j++){
                    row.createCell(j+1).setCellValue(mapMatchingResults[j]);
                }
            }
            else {
                row.createCell(1).setCellValue("Impossible");
            }
            radius += 0.5;
        }
        System.out.println("HIMM, A matrix : Topology graph / B matrix : Circle buffer end");

        // A matrix : based on static P
        // B matrix : based on Cell buffer
        row = sheet.createRow(sheet.getLastRowNum() + 1);
        row.createCell(0).setCellValue("HIMM, A matrix : Static P / B matrix : Circle buffer");
        for(double i = 0.1; i < 1; i += 0.2) {
            radius = 0.5;
            himm.makeAMatrixByStaticP(i);
            for(int j = 0; j < BUFFER_REPETITION; j++) {
                row = sheet.createRow(sheet.getLastRowNum() + 1);
                row.createCell(0).setCellValue("P: " + i + " / Radius(m): " + radius);
                String[] mapMatchingResults = getResultFromHIMMwithCircleBuffer(himm, trajectory, ChangeCoord.CANVAS_MULTIPLE * radius);
                if(mapMatchingResults != null) {
                    for(int k = 0; k < trajectory.getNumPoints(); k++){
                        row.createCell(k+1).setCellValue(mapMatchingResults[k]);
                    }
                }
                else {
                    row.createCell(1).setCellValue("Impossible");
                }
                radius += 0.5;
            }
        }
        System.out.println("HIMM, A matrix : Static P / B matrix : Circle buffer end");
    }

    private String[] getResultFromHIMMwithCircleBuffer(HMMIndoorMapMatching himm, LineString trajectory, double radius) {
        String[] mapMatchingResults;
        ArrayList<String> observationArrayList =  new ArrayList<>();
        for(int i = 0; i < trajectory.getNumPoints(); i++) {
            String[] observationLabelList = new String[observationArrayList.size()];
            for(int j = 0; j < observationArrayList.size(); j++) {
                observationLabelList[j] = observationArrayList.get(j);
            }
            himm.makeBmatrixCircleBuffer(trajectory.getCoordinateN(i), radius);
            String mapMatchingResult = himm.getMapMatchingResult(trajectory.getPointN(i), observationLabelList);
            if(mapMatchingResult.equals("Impossible")) {
                return null;
            }
            observationArrayList.add(mapMatchingResult);
        }
        himm.clearOnlyBMatrix();
        mapMatchingResults = new String[observationArrayList.size()];
        for(int i = 0; i < observationArrayList.size(); i++) {
            mapMatchingResults[i] = observationArrayList.get(i);
        }
        return mapMatchingResults;
    }

    private ExperimentResult getGroundTruthResult_Excel(Workbook workbook, ExperimentResult er, ArrayList<String> keyList) {
        Sheet resultSheet = workbook.getSheet("Result");
        Sheet summarySheet = workbook.createSheet("Summary");

        int numTrPoint = trajectory.getNumPoints();
        er.numTrajectoryPoint = numTrPoint;
        er.trajectoryLength[0] = trajectory.getLength() / ChangeCoord.CANVAS_MULTIPLE;
        er.trajectoryLength[1] = trajectory_IF.getLength() / ChangeCoord.CANVAS_MULTIPLE;
        er.trajectoryLength[2] = getSubLineString(trajectory_GT, numTrPoint).getLength() / ChangeCoord.CANVAS_MULTIPLE;

        String[] groungTruthResult = null;
        Row resultSheetRow = resultSheet.createRow(resultSheet.getLastRowNum() + 1);
        resultSheetRow.createCell(0).setCellValue("Ground Truth");
        if(indoorFeatures != null && trajectory_GT != null) {
            DirectIndoorMapMatching dimm = new DirectIndoorMapMatching(indoorFeatures);
            groungTruthResult = dimm.getMapMatchingResult(trajectory_GT);

            for(int i = 0; i < numTrPoint; i++){
                resultSheetRow.createCell(i+1).setCellValue(groungTruthResult[i]);
            }
        }

        String bigClass = null, middleClass = null;
        for(int i = 0; i < resultSheet.getLastRowNum(); i++) {
            resultSheetRow = resultSheet.getRow(i);
            if(resultSheetRow.getLastCellNum() != 1) {
                Row summaryRow = summarySheet.createRow(i);
                double countTrue = 0;
                for(int j = 1; j < resultSheetRow.getLastCellNum(); j++) {
                    if(resultSheetRow.getCell(j).getStringCellValue().equals(groungTruthResult[j-1])) {
                        summaryRow.createCell(j).setCellValue(true);
                        countTrue++;
                    }
                    else {
                        summaryRow.createCell(j).setCellValue(false);
                    }
                }
                summaryRow.createCell(0).setCellValue(countTrue);
                double accuracy = countTrue / (resultSheetRow.getLastCellNum() - 1) * 100;
                summaryRow.createCell(summaryRow.getLastCellNum()).setCellValue(accuracy);

                String key = bigClass + "_" + middleClass + "_" + resultSheetRow.getCell(0).getStringCellValue();
                er.accuracy.put(key, accuracy);
                er.trueCount.put(key, countTrue);
                if(keyList != null)
                    keyList.add(key);
            }
            else {
                String className = resultSheetRow.getCell(0).getStringCellValue();
                if(className.equals("original trajectory") || className.equals("Indoor distance filtered trajectory")){
                    bigClass = className;
                }
                else {
                    middleClass = className;
                }
            }
        }

        // Calculate a average error between ground truth and positioning trajectory
        if(trajectory != null && trajectory_IF != null && trajectory_GT != null) {
            double errorOriginal = 0, errorIndoorFilter = 0;
            double errorOriginalArray[] = new double[numTrPoint];
            double errorIndoorFilterArray[] = new double[numTrPoint];

            for(int i = 0; i < numTrPoint; i++){
                Coordinate gtCoord = trajectory_GT.getCoordinateN(i);
                Coordinate pCoord = trajectory.getCoordinateN(i);
                Coordinate ifCoord = trajectory_IF.getCoordinateN(i);

                errorOriginalArray[i] = gtCoord.distance(pCoord)/ChangeCoord.CANVAS_MULTIPLE;
                errorIndoorFilterArray[i] = gtCoord.distance(ifCoord)/ChangeCoord.CANVAS_MULTIPLE;
                errorOriginal += errorOriginalArray[i];
                errorIndoorFilter += errorIndoorFilterArray[i];
            }
            er.averageError[0] = errorOriginal / numTrPoint;
            er.averageError[1] = errorIndoorFilter / numTrPoint;

            double varOriginalError = 0, varIndoorFilterError = 0;
            for(int i = 0; i < trajectory.getNumPoints(); i++) {
                varOriginalError += Math.pow(er.averageError[0] - errorOriginalArray[i], 2);
                varIndoorFilterError += Math.pow(er.averageError[1] - errorIndoorFilterArray[i], 2);
            }
            er.varianceError[0] = varOriginalError / numTrPoint;
            er.varianceError[1] = varIndoorFilterError / numTrPoint;

            Row summaryRow = summarySheet.createRow(summarySheet.getLastRowNum() + 1);
            summaryRow.createCell(0).setCellValue("Metric");
            summaryRow = summarySheet.createRow(summarySheet.getLastRowNum() + 1);
            summaryRow.createCell(0).setCellValue("Number of points:");
            summaryRow.createCell(1).setCellValue(er.numTrajectoryPoint);
            summaryRow = summarySheet.createRow(summarySheet.getLastRowNum() + 1);
            summaryRow.createCell(0).setCellValue("Length of original trajectory:");
            summaryRow.createCell(1).setCellValue(er.trajectoryLength[0]);
            summaryRow = summarySheet.createRow(summarySheet.getLastRowNum() + 1);
            summaryRow.createCell(0).setCellValue("Length of indoor filtered trajectory:");
            summaryRow.createCell(1).setCellValue(er.trajectoryLength[1]);
            summaryRow = summarySheet.createRow(summarySheet.getLastRowNum() + 1);
            summaryRow.createCell(0).setCellValue("Length of ground truth trajectory:");
            summaryRow.createCell(1).setCellValue(er.trajectoryLength[2]);
            summaryRow = summarySheet.createRow(summarySheet.getLastRowNum() + 1);
            summaryRow.createCell(0).setCellValue("Average error");
            summaryRow = summarySheet.createRow(summarySheet.getLastRowNum() + 1);
            summaryRow.createCell(0).setCellValue("Original:");
            summaryRow.createCell(1).setCellValue(er.averageError[0]);
            summaryRow = summarySheet.createRow(summarySheet.getLastRowNum() + 1);
            summaryRow.createCell(0).setCellValue("Indoor filtered:");
            summaryRow.createCell(1).setCellValue(er.averageError[1]);
            summaryRow = summarySheet.createRow(summarySheet.getLastRowNum() + 1);
            summaryRow.createCell(0).setCellValue("Variance error");
            summaryRow.createCell(0).setCellValue("Original:");
            summaryRow.createCell(1).setCellValue(er.varianceError[0]);
            summaryRow = summarySheet.createRow(summarySheet.getLastRowNum() + 1);
            summaryRow.createCell(0).setCellValue("Indoor filtered:");
            summaryRow.createCell(1).setCellValue(er.varianceError[1]);
        }

        return er;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D)g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setStroke(new BasicStroke(2));
        g2.setFont(new Font("Serif", Font.PLAIN, 20));


        /*
        // draw mouse point coordinate
        // it related with mouseMoved function
        g2.setFont(new Font("Serif", Font.PLAIN, 20));
        g2.drawString((mousePositionX - SCREENBUFFER) + "," + (mousePositionY - SCREENBUFFER), 20,20);
        g2.drawString(mousePositionX + "," + mousePositionY, 20,20);
        */

        if(indoorFeatures != null) {
            int cellIndex = -1;
            for (CellSpace cellSpace : indoorFeatures.getCellSpaces()) {
                Polygon polygon = cellSpace.getGeom();
                cellIndex++;

                float drawPositionX = addScreenBuffer(polygon.getCentroid().getX()).floatValue();
                float drawPositionY = addScreenBuffer(polygon.getCentroid().getY()).floatValue();
                g2.setColor(Color.BLACK);
                g2.drawString(String.valueOf(cellIndex), drawPositionX, drawPositionY);
                if(cellSpace.getLabel() != null) {
                    g2.drawString("(" + cellSpace.getLabel() + ")", drawPositionX, drawPositionY + 20);
                }

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
                    //drawLines(g2, lineString.buffer(10,2).getCoordinates(), Color.RED, false);
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
            drawArrowLines(g2, trajectory.getCoordinates(), trajectory.getNumPoints(), Color.GREEN);
            if(trajectory_IF != null) {
                drawArrowLines(g2, trajectory_IF.getCoordinates(), trajectory.getNumPoints(), Color.BLUE);
            }
            if(trajectory_GT != null) {
                drawArrowLines(g2, trajectory_GT.getCoordinates(), trajectory.getNumPoints(), Color.RED);
            }
            System.out.println("original trajectory N : " + trajectory.getNumPoints());


            /*
            // TODO : Remove it(Temporary case)
            if(circleBufferArray.size() != 0) {
                for(Polygon polygon : circleBufferArray)
                    drawLines(g2, polygon.getExteriorRing().getCoordinates(), Color.GREEN, false);
            }

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

    private void drawArrowLines(Graphics2D g2, Coordinate[] coords, int length, Color color) {
        if (coords.length > 1) {
            for (int i = 0; i < length - 1; i++) {
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

    private Coordinate[] addCoordinate(Coordinate newCoord) {
        int coordsNum = drawingCellCoords.length;
        Coordinate[] realCellCoords = new Coordinate[coordsNum + 1];
        for (int i = 0; i < coordsNum; i++) {
            realCellCoords[i] = new Coordinate(drawingCellCoords[i].x, drawingCellCoords[i].y);
        }
        realCellCoords[coordsNum] = new Coordinate(newCoord.x, newCoord.y);
        return realCellCoords;
    }

    private LineString getSubLineString(LineString lineString, int size) {
        if(size > lineString.getNumPoints()) return  lineString;
        else {
            Coordinate[] originalCoords = lineString.getCoordinates();
            Coordinate[] newCoords = new Coordinate[size];

            for(int i = 0; i < size; i++) {
                newCoords[i] = originalCoords[i];
            }

            return gf.createLineString(newCoords);
        }
    }

    private void syntheticTrajectoryTest() {
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

    void saveImage(String fileID) {
        BufferedImage image = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        paint(g2);

        // Check a directory is exist or not
        File dir = new File(resultPath);
        if(!dir.exists()){
            dir.mkdirs();
        }

        // Save CavasPanel as image
        try {
            ImageIO.write(image, "PNG", new File(resultPath + "screenshot_" + fileID + ".png"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
