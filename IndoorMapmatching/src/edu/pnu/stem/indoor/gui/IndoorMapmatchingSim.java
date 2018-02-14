package edu.pnu.stem.indoor.gui;

import cn.edu.zju.db.datagen.database.DB_Connection;
import cn.edu.zju.db.datagen.database.DB_WrapperLoad;
import cn.edu.zju.db.datagen.database.spatialobject.AccessPoint;
import cn.edu.zju.db.datagen.database.spatialobject.Floor;
import cn.edu.zju.db.datagen.database.spatialobject.Partition;
import com.vividsolutions.jts.geom.*;
import diva.util.java2d.Polygon2D;
import edu.pnu.stem.indoor.feature.CellSpace;
import edu.pnu.stem.indoor.util.ChangeCoord;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.io.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

/**
 * Created by STEM_KTH on 2017-05-17.
 * @author Taehoon Kim, Pusan National University, STEM Lab.
 */
public class IndoorMapmatchingSim {
    public final static double CANVAS_RIGHT_LOWER_X = 1500;
    public final static double CANVAS_RIGHT_LOWER_Y = 1500;
    public final static double CANVAS_LEFT_UPPER_X = 0;
    public final static double CANVAS_LEFT_UPPER_Y = 0;
    private GeometryFactory gf;
    private JPanel panelMain;
    private JButton buttonCreateCell;
    private JPanel panelCanvas;
    private JButton buttonCreatePath;
    private JButton buttonSelectCell;
    private JButton buttonPathEvaluate;
    private JButton buttonCreateDoor;
    private JButton buttonGetPreparedInfo;
    private JButton buttonCreateHole;
    private JButton buttonGetGroundTruth;
    private JButton buttonGetOSMData;
    private JButton buttonGetCreateTrajectory;
    private JButton buttonGetIFCData;
    private JTextPane textPaneOriginal;
    private JScrollPane jScrollPane;

    private final String RAW_TR_PATH = "C:\\Users\\timeo\\IdeaProjects\\github\\vita-master\\export4\\raw trajectory\\2018_02_14_09_41_15";
    private final String TR_PATH = "C:\\Users\\timeo\\IdeaProjects\\github\\vita-master\\export4\\indoor positioning data\\2018_02_14_09_56_14";

    private IndoorMapmatchingSim() {
        panelMain.setSize(1000,1000);
        gf = new GeometryFactory();

        buttonGetPreparedInfo.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser("C:\\Users\\timeo\\IdeaProjects\\github\\IndoorMapmatching");
            FileNameExtensionFilter filter = new FileNameExtensionFilter("txt (*.txt)", "txt");
            fileChooser.setFileFilter(filter);

            int returnVal = fileChooser.showOpenDialog(panelMain);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                try {
                    getIndoorInfoWithSimpleFormat(file);
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        });
        buttonGetOSMData.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser("C:\\Users\\timeo\\IdeaProjects\\github\\IndoorMapmatching");
            FileNameExtensionFilter filter = new FileNameExtensionFilter("OSM Data (*.osm)", "osm");
            fileChooser.setFileFilter(filter);

            int returnVal = fileChooser.showOpenDialog(panelMain);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                try {
                    getIndoorInfoWithOSMFormat(file);
                } catch (IOException | ParserConfigurationException | SAXException e1) {
                    e1.printStackTrace();
                }
            }
        });
        buttonGetIFCData.addActionListener(e -> {
            getIndoorInfoWithIFCFormat();
            /*
            JFileChooser fileChooser = new JFileChooser("C:\\Users\\timeo\\IdeaProjects\\github\\IndoorMapmatching");
            FileNameExtensionFilter filter = new FileNameExtensionFilter("IFC Data (*.ifc)", "ifc");
            fileChooser.setFileFilter(filter);

            int returnVal = fileChooser.showOpenDialog(panelMain);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                getIndoorInfoWithIFCFormat(file);
            }
            */
        });
        buttonCreateCell.addActionListener(e -> ((CanvasPanel)panelCanvas).currentEditStatus = EditStatus.CREATE_CELLSPACE);
        buttonCreatePath.addActionListener(e -> ((CanvasPanel)panelCanvas).currentEditStatus = EditStatus.CREATE_TRAJECTORY);
        buttonSelectCell.addActionListener(e -> ((CanvasPanel)panelCanvas).currentEditStatus = EditStatus.SELECT_CELLSPACE);
        buttonPathEvaluate.addActionListener(e -> ((CanvasPanel)panelCanvas).syntheticTrajectoryTest());
        buttonCreateHole.addActionListener(e -> ((CanvasPanel)panelCanvas).currentEditStatus = EditStatus.CREATE_HOLE);
        buttonGetGroundTruth.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser(RAW_TR_PATH);
            FileNameExtensionFilter filter = new FileNameExtensionFilter("txt (*.txt)", "txt");
            fileChooser.setFileFilter(filter);

            int returnVal = fileChooser.showOpenDialog(panelMain);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                try {
                    System.out.println("File Name: " + file.getName());
                    ((CanvasPanel)panelCanvas).setTrajectory(getVITAData(file));
                } catch (IOException | ParseException e1) {
                    e1.printStackTrace();
                }
            }
            ((CanvasPanel)panelCanvas).getGroundTruthResult(textPaneOriginal);
        });
        buttonGetCreateTrajectory.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser(TR_PATH);
            FileNameExtensionFilter filter = new FileNameExtensionFilter("txt (*.txt)", "txt");
            fileChooser.setFileFilter(filter);

            String fileID = "";
            int returnVal = fileChooser.showOpenDialog(panelMain);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                try {
                    System.out.println("File Name: " + file.getName());
                    String[] fileName = file.getName().split("_");
                    fileID = fileName[fileName.length - 1];
                    //getBuildNGoData(file);
                    ((CanvasPanel)panelCanvas).setTrajectory(getVITAData(file));
                } catch (IOException | ParseException e1) {
                    e1.printStackTrace();
                }
            }

            String groundTruthFileName = "Dest_Traj_";
            File groundTruth = new File(RAW_TR_PATH +"\\"+ groundTruthFileName + fileID);
            try {
                ((CanvasPanel)panelCanvas).setTrajectory_GT(getVITAData(groundTruth));
            } catch (IOException | ParseException e1) {
                e1.printStackTrace();
            }
            //((CanvasPanel)panelCanvas).evaluateIndoorMapMatching(textPaneOriginal);
            //((CanvasPanel)panelCanvas).getGroundTruthResult(textPaneOriginal);
            ((CanvasPanel)panelCanvas).evaluateSIMM_Excel(fileID);
        });
    }

    private void getBuildNGoData(File inputFile) throws IOException {
        String line;
        ArrayList<Coordinate> coords = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new FileReader(inputFile));

        while((line = reader.readLine()) != null) {
            if(line.split("/").length > 1) {
                Coordinate coord = new Coordinate(Double.valueOf(line.split("/")[1]),
                        Double.valueOf(line.split("/")[0]));
                coords.add(new Coordinate(ChangeCoord.changeCoordWGS84toMeter(coord)));
            }
        }

        Coordinate[] trajectoryData = new Coordinate[coords.size()];
        for(int i = 0; i < coords.size(); i++) {
            trajectoryData[i] = coords.get(i);
        }
        LineString loadedTrajectory = gf.createLineString(trajectoryData);

        ((CanvasPanel)panelCanvas).setTrajectory(loadedTrajectory);
    }

    private LineString getVITAData(File inputFile) throws IOException, ParseException {
        String line;
        ArrayList<Coordinate> coords = new ArrayList<>();
        ArrayList<Coordinate> coordsInASec = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new FileReader(inputFile));
        Date prevDate = null;
        Boolean isRawData = false;
        line = reader.readLine(); // first line is header part

        while((line = reader.readLine()) != null) {
            if(line.split("\t").length > 1) {
                String[] parsedResult = line.split("\t");
                // parsedResult is composed as follows
                // floorId | partitionId | location_x | location_y | timeStamp

                if(((CanvasPanel)panelCanvas).floorId != Integer.valueOf(parsedResult[0])) {
                    break;
                }

                Coordinate coord = new Coordinate(Double.valueOf(parsedResult[2]),
                        Double.valueOf(parsedResult[3]));
                coordsInASec.add(coord);
                if(!isRawData) {
                    coords.add(new Coordinate(ChangeCoord.changeCoordWithRatio(coord)));
                }

                SimpleDateFormat dt = new SimpleDateFormat("yyyy/mm/dd hh:mm:ss");
                Date curDate = dt.parse(parsedResult[4]);

                if(prevDate == null) {
                    prevDate = curDate;
                }
                else if(!isRawData) {
                    if(prevDate.equals(curDate)) {
                        isRawData = true;
                        if(coords.size() == 3) {
                            Coordinate first = coords.get(0);
                            coords.clear();
                            coords.add(first);
                        }
                        else {
                            coords.clear();
                        }
                    }
                }
                else {
                    if(!prevDate.equals(curDate)) {
                        // create an average coordinate while in a second
                        Coordinate nextCoord = coordsInASec.get(coordsInASec.size() - 1);
                        coordsInASec.remove(coordsInASec.size() - 1);

                        Coordinate coordinate = getAverageCoordinate(coordsInASec);
                        coords.add(new Coordinate(ChangeCoord.changeCoordWithRatio(coordinate)));
                        coordsInASec.clear();
                        coordsInASec.add(nextCoord);
                    }
                }

                prevDate = curDate;
            }
        }

        if(!coordsInASec.isEmpty() && isRawData) {
            Coordinate coordinate = getAverageCoordinate(coordsInASec);
            coords.add(new Coordinate(ChangeCoord.changeCoordWithRatio(coordinate)));
            coordsInASec.clear();
        }

        Coordinate[] trajectoryData = new Coordinate[coords.size()];
        for(int i = 0; i < coords.size(); i++) {
            trajectoryData[i] = coords.get(i);
        }
        LineString loadedTrajectory = gf.createLineString(trajectoryData);
        return  loadedTrajectory;
    }

    private Coordinate getAverageCoordinate(ArrayList<Coordinate> coordsInASec) {
        double avrCoordX = 0;
        double avrCoordY = 0;
        for(Coordinate c : coordsInASec) {
            avrCoordX += c.x;
            avrCoordY += c.y;
        }
        avrCoordX /= coordsInASec.size();
        avrCoordY /= coordsInASec.size();

        return new Coordinate(avrCoordX,avrCoordY);
    }

    private void getIndoorInfoWithSimpleFormat(File inputFile) throws IOException {
        String line;
        int depth = 0;
        ParseStatus parseStatus = ParseStatus.NULL;
        CellSpace cellSpace = null;
        ArrayList<Coordinate> coords = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new FileReader(inputFile));
        while((line = reader.readLine()) != null) {
            String[] predefinedInfo = line.split("\\[");
            if(predefinedInfo.length == 1) {
                switch (predefinedInfo[0]) {
                    case "Cell":
                        depth++;
                        break;
                    case "Geom":
                        depth++;
                        parseStatus = ParseStatus.GEOM;
                        break;
                    case "Door":
                        depth++;
                        parseStatus = ParseStatus.DOOR;
                        break;
                    default:
                        if (predefinedInfo[0].equals("]")) {
                            depth--;
                            if (depth == 0) {
                                ((CanvasPanel) panelCanvas).addCellSpace(cellSpace);
                            } else {
                                switch (parseStatus) {
                                    case GEOM:
                                        Coordinate[] cellCoords = new Coordinate[coords.size()];
                                        for (int i = 0; i < coords.size(); i++) {
                                            cellCoords[i] = new Coordinate(coords.get(i).x, coords.get(i).y);
                                        }
                                        CoordinateSequence seq = gf.getCoordinateSequenceFactory().create(cellCoords);
                                        LinearRing lr = gf.createLinearRing(seq);
                                        Polygon polygon = gf.createPolygon(lr);
                                        cellSpace = new CellSpace(polygon);

                                        coords.clear();
                                        break;
                                    case DOOR:
                                        Coordinate[] doorCoords = new Coordinate[coords.size()];
                                        for (int i = 0; i < coords.size(); i++) {
                                            doorCoords[i] = new Coordinate(coords.get(i).x, coords.get(i).y);
                                        }
                                        if (cellSpace != null) {
                                            cellSpace.addDoors(gf.createLineString(doorCoords));
                                        }

                                        coords.clear();
                                        break;
                                }
                            }
                        } else {
                            String[] positionInfo = predefinedInfo[0].split(",");
                            double positionX = Double.parseDouble(positionInfo[0]);
                            double positionY = Double.parseDouble(positionInfo[1]);
                            coords.add(new Coordinate(positionX, positionY));
                        }
                        break;
                }
            }
        }
    }

    private void getIndoorInfoWithOSMFormat(File inputFile) throws IOException, ParserConfigurationException, SAXException {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document document = dBuilder.parse(inputFile);
        document.getDocumentElement().normalize();

        HashMap<Integer, Coordinate> osmNodeInfo = new HashMap<>();

        double max_position_x = 0;
        double max_position_y = 0;
        double min_position_x = 10000;
        double min_position_y = 10000;

        // Save node information in osm xml file (It is point geometry related with Cell geometries)
        NodeList nodeList = document.getElementsByTagName("node");
        for(int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if(node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                int id = Integer.valueOf(element.getAttribute("id"));
                double x = Double.valueOf(element.getAttribute("lat"));
                double y = Double.valueOf(element.getAttribute("lon"));
                osmNodeInfo.put(id, new Coordinate(x,y));

                if (x > max_position_x) max_position_x = x;
                if (y > max_position_y) max_position_y = y;
                if (x < min_position_x) min_position_x = x;
                if (y < min_position_y) min_position_y = y;
            }
        }

        ChangeCoord.setInitialInfo(new Coordinate(min_position_x, min_position_y), new Coordinate(max_position_x,max_position_y));
        ArrayList<CellSpace> cellSpaces = new ArrayList<>();
        ArrayList<LineString> doorGeoms = new ArrayList<>();
        ArrayList<Coordinate> coords = new ArrayList<>();

        // Make cell spaces (relate with geometry, door info, cell label)
        NodeList wayList = document.getElementsByTagName("way");
        for(int i = 0; i < wayList.getLength(); i++) {
            Node node = wayList.item(i);
            if(node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                NodeList refList = element.getElementsByTagName("nd");
                NodeList tagList = element.getElementsByTagName("tag");

                for(int j = 0; j < refList.getLength(); j++) {
                    Node refNode = refList.item(j);
                    if(refNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element refElement = (Element) refNode;
                        int refID = Integer.valueOf(refElement.getAttribute("ref"));
                        coords.add(ChangeCoord.changeCoordWGS84toMeter(osmNodeInfo.get(refID)));
                    }
                }
                Coordinate[] coordsArray = new Coordinate[coords.size()];
                for(int j = 0; j < coords.size(); j++) {
                    coordsArray[j] = coords.get(j);
                }

                CellSpace cellSpace = null;
                String cellLabel = null;
                for(int j = 0; j < tagList.getLength(); j++) {
                    Node tagNode = tagList.item(j);
                    if (tagNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element tagElement = (Element) tagNode;
                        if(tagElement.getAttribute("k").equals("door")
                                && tagElement.getAttribute("v").equals("yes")) {
                            LineString lineString = gf.createLineString(coordsArray);
                            doorGeoms.add(lineString);
                            break;
                        }
                        else if((tagElement.getAttribute("k").equals("room") || tagElement.getAttribute("k").equals("corridor"))
                                && tagElement.getAttribute("v").equals("yes")) {
                            if(coords.get(0).equals(coords.get(coords.size() - 1))) {
                                // In case : "way" geometry is closed polygon
                                CoordinateSequence seq = gf.getCoordinateSequenceFactory().create(coordsArray);
                                LinearRing lr = gf.createLinearRing(seq);
                                Polygon polygon = gf.createPolygon(lr);
                                cellSpace = new CellSpace(polygon);
                                cellSpaces.add(cellSpace);
                            }
                        }
                        if(tagElement.getAttribute("k").equals("label")) {
                            cellLabel = tagElement.getAttribute("v");
                        }
                    }
                }

                if(cellSpace != null && cellLabel != null){
                    cellSpace.setLabel(cellLabel);
                }

                coords.clear();
            }
        }

        for(CellSpace cellSpace : cellSpaces) {
            Polygon cellGeom = cellSpace.getGeom();
            for(LineString doorGeom : doorGeoms) {
                if(cellGeom.covers(doorGeom)) {
                    cellSpace.addDoors(doorGeom);
                }
            }
            ((CanvasPanel)panelCanvas).addCellSpace(cellSpace);
        }
    }

    private void getIndoorInfoWithIFCFormat() {
        ArrayList<CellSpace> cellSpaces = new ArrayList<>();

        Connection connection = DB_Connection.connectToDatabase("conf/moovework.properties");
        try {
            DB_WrapperLoad.loadALL(connection, 3);
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        ArrayList<Floor> floors = DB_WrapperLoad.floorT;
        Floor floor = floors.get(0);

        double max_position_x = 0;
        double max_position_y = 0;
        double min_position_x = 10000;
        double min_position_y = 10000;
        for(Partition partition : floor.getPartsAfterDecomposed()) {
            if(partition.getPolygon2D().getVertexCount() == 0) // In Case: OutSide
                continue;
            Rectangle2D mbr = partition.getPolygon2D().getBounds2D();
            if (mbr.getMaxX() > max_position_x) max_position_x = mbr.getMaxX();
            if (mbr.getMaxY() > max_position_y) max_position_y = mbr.getMaxY();
            if (mbr.getMinX() < min_position_x) min_position_x = mbr.getMinX();
            if (mbr.getMinY() < min_position_y) min_position_y = mbr.getMinY();
        }
        ChangeCoord.setInitialInfo(new Coordinate(min_position_x, min_position_y), new Coordinate(max_position_x,max_position_y));

        for(Partition partition : floor.getPartitions()) {
            Polygon2D.Double cellGeom = partition.getPolygon2D();
            if(cellGeom.getVertexCount() == 0) // In Case: OutSide
                continue;
            Coordinate[] coordsArray = new Coordinate[cellGeom.getVertexCount() + 1];
            for(int i = 0; i < coordsArray.length - 1; i++) {
                coordsArray[i] = ChangeCoord.changeCoordWithRatio(new Coordinate(cellGeom.getX(i), cellGeom.getY(i)));
            }
            coordsArray[coordsArray.length - 1] = coordsArray[0];

            CoordinateSequence seq = gf.getCoordinateSequenceFactory().create(coordsArray);
            LinearRing lr = gf.createLinearRing(seq);
            Polygon polygon = gf.createPolygon(lr);
            CellSpace cellSpace = new CellSpace(polygon);
            cellSpaces.add(cellSpace);
            if(partition.getName() != null)
                cellSpace.setLabel(partition.getName());
            else
                cellSpace.setLabel(partition.getGlobalID());

            for(AccessPoint ap : partition.getAPs()) {
                Line2D.Double doorGeom = ap.getLine2D();
                coordsArray = new Coordinate[2];
                coordsArray[0] = ChangeCoord.changeCoordWithRatio(new Coordinate(doorGeom.getX1(), doorGeom.getY1()));
                coordsArray[1] = ChangeCoord.changeCoordWithRatio(new Coordinate(doorGeom.getX2(), doorGeom.getY2()));
                LineString doorGeomJTS = gf.createLineString(coordsArray);
                if(cellSpace.getGeom().covers(doorGeomJTS)) {
                    cellSpace.addDoors(doorGeomJTS);
                } else {
                    double bufferSize = cellSpace.getGeom().distance(doorGeomJTS);

                    Geometry buffer = doorGeomJTS.buffer(bufferSize + 1);
                    LineString newDoorGeomJTS = (LineString) buffer.intersection(cellSpace.getGeom().getExteriorRing());//cellSpace.getGeom().intersection(buffer);
                    if(cellSpace.getGeom().covers(newDoorGeomJTS)) {
                        cellSpace.addDoors(newDoorGeomJTS);
                    } else
                        System.out.println("WTF??");
                }

            }
            ((CanvasPanel)panelCanvas).addCellSpace(cellSpace);
            ((CanvasPanel)panelCanvas).floorId = floor.getItemID();
        }
    }

    public static void main(String[] args) {
        JFrame jFrame = new JFrame("Symbolic Indoor Map Matching Simulator");
        jFrame.setContentPane(new IndoorMapmatchingSim().panelMain);
        jFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        jFrame.pack();
        jFrame.setVisible(true);
    }

    private void createUIComponents() {
        // TODO: Place custom component creation code here
        panelCanvas = new CanvasPanel();
        jScrollPane = new JScrollPane(panelCanvas);
    }
}
