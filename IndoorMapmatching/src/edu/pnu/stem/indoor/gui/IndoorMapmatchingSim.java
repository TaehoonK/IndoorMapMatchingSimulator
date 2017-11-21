package edu.pnu.stem.indoor.gui;

import com.vividsolutions.jts.geom.*;
import edu.pnu.stem.indoor.CellSpace;
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
import java.io.*;
import java.util.ArrayList;
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

    private JPanel panelMain;
    private JButton buttonCreateCell;
    private JPanel panelCanvas;
    private JButton buttonCreatePath;
    private JButton buttonSelectCell;
    private JButton buttonPathEvaluate;
    private JButton buttonCreateDoor;
    private JButton buttonGetPreparedInfo;
    private JButton buttonCreateHole;
    private JButton buttonGetRelatedEdge;
    private JButton buttonGetOSMData;
    private JButton buttonGetCreateTrajectory;
    private JButton buttonDirectMapMatching;

    private ChangeCoord changeCoord = null;

    public IndoorMapmatchingSim() {
        panelMain.setSize(1000,1000);

        buttonGetPreparedInfo.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser("D:\\InteliJ\\github\\IndoorMapmatching");
            FileNameExtensionFilter filter = new FileNameExtensionFilter("txt (*.txt)", "txt");
            fileChooser.setFileFilter(filter);

            int returnVal = fileChooser.showOpenDialog(panelMain);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                try {
                    getIndoorInfoWithSimpleFormat(file);
                } catch (FileNotFoundException e1) {
                    e1.printStackTrace();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        });
        buttonGetOSMData.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser("D:\\InteliJ\\github\\IndoorMapmatching");
            FileNameExtensionFilter filter = new FileNameExtensionFilter("osm (*.osm)", "osm");
            fileChooser.setFileFilter(filter);

            int returnVal = fileChooser.showOpenDialog(panelMain);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                try {
                    getIndoorInfoWithOSMFormat(file);
                } catch (FileNotFoundException e1) {
                    e1.printStackTrace();
                } catch (IOException e1) {
                    e1.printStackTrace();
                } catch (ParserConfigurationException e1) {
                    e1.printStackTrace();
                } catch (SAXException e1) {
                    e1.printStackTrace();
                }
            }
        });
        buttonCreateCell.addActionListener(e -> ((CanvasPanel)panelCanvas).currentEditStatus = EditStatus.CREATE_CELLSPACE);
        buttonCreatePath.addActionListener(e -> ((CanvasPanel)panelCanvas).currentEditStatus = EditStatus.CREATE_TRAJECTORY);
        buttonSelectCell.addActionListener(e -> ((CanvasPanel)panelCanvas).currentEditStatus = EditStatus.SELECT_CELLSPACE);
        buttonPathEvaluate.addActionListener(e -> {

        });
        buttonCreateHole.addActionListener(e -> ((CanvasPanel)panelCanvas).currentEditStatus = EditStatus.CREATE_HOLE);
        buttonGetRelatedEdge.addActionListener(e -> ((CanvasPanel)panelCanvas).currentEditStatus = EditStatus.GET_RELATED_EDGE);
        buttonGetCreateTrajectory.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser("D:\\InteliJ\\github\\IndoorMapmatching");
            FileNameExtensionFilter filter = new FileNameExtensionFilter("txt (*.txt)", "txt");
            fileChooser.setFileFilter(filter);

            int returnVal = fileChooser.showOpenDialog(panelMain);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                try {
                    getBuildNGoData(file);
                } catch (FileNotFoundException e1) {
                    e1.printStackTrace();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        });
        buttonDirectMapMatching.addActionListener(e -> ((CanvasPanel)panelCanvas).evaluateDirectIndoorMapMatching());
    }

    private void getBuildNGoData(File inputFile) throws IOException {
        String line = null;
        int depth = 0;
        GeometryFactory gf = new GeometryFactory();
        ArrayList<Coordinate> coords = new ArrayList<Coordinate>();

        if(changeCoord == null) {
            // TODO : Throw exception

        }

        BufferedReader reader = new BufferedReader(new FileReader(inputFile));
        while((line = reader.readLine()) != null) {
            if(line.split("/").length > 1) {
                Coordinate coord = new Coordinate(Double.valueOf(line.split("/")[1]),
                        Double.valueOf(line.split("/")[0]));
                coords.add(new Coordinate(changeCoord.changeCordinate(coord)));
            }
        }

        Coordinate[] trajectoryData = new Coordinate[coords.size()];
        for(int i = 0; i < coords.size(); i++) {
            trajectoryData[i] = coords.get(i);
        }

        LineString loadedTrajectory = gf.createLineString(trajectoryData);
        ((CanvasPanel)panelCanvas).setTrajectory(loadedTrajectory);
    }

    private void getIndoorInfoWithSimpleFormat(File inputFile) throws IOException {
        String line = null;
        int depth = 0;
        ParseStatus parseStatus = ParseStatus.NULL;
        GeometryFactory gf = new GeometryFactory();
        CellSpace cellSpace = null;
        ArrayList<Coordinate> coords = new ArrayList<Coordinate>();
        BufferedReader reader = new BufferedReader(new FileReader(inputFile));
        while((line = reader.readLine()) != null) {
            String[] predefinedInfo = line.split("\\[");
            if(predefinedInfo.length == 1) {
                if(predefinedInfo[0].equals("Cell")){
                    depth++;
                }
                else if(predefinedInfo[0].equals("Geom")){
                    depth++;
                    parseStatus = ParseStatus.GEOM;
                }
                else if(predefinedInfo[0].equals("Door")){
                    depth++;
                    parseStatus = ParseStatus.DOOR;
                }
                else {
                    if(predefinedInfo[0].equals("]")) {
                        depth--;
                        if(depth == 0) {
                            ((CanvasPanel)panelCanvas).addCellSpace(cellSpace);
                        }
                        else {
                            switch (parseStatus) {
                                case GEOM:
                                    Coordinate[] cellCoords = new Coordinate[coords.size()];
                                    for(int i = 0; i < coords.size(); i++) {
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
                                    for(int i = 0; i < coords.size(); i++) {
                                        doorCoords[i] = new Coordinate(coords.get(i).x, coords.get(i).y);
                                    }
                                    cellSpace.addDoors(gf.createLineString(doorCoords));

                                    coords.clear();
                                    break;
                            }
                        }
                    }
                    else {
                        String[] positionInfo = predefinedInfo[0].split(",");
                        double positionX = Double.parseDouble(positionInfo[0]);
                        double positionY = Double.parseDouble(positionInfo[1]);
                        coords.add(new Coordinate(positionX,positionY));
                    }
                }
            }
        }
    }

    private void getIndoorInfoWithOSMFormat(File inputFile) throws IOException, ParserConfigurationException, SAXException {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document document = dBuilder.parse(inputFile);
        document.getDocumentElement().normalize();

        GeometryFactory gf = new GeometryFactory();
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

        changeCoord = new ChangeCoord(new Coordinate(min_position_x, min_position_y), new Coordinate(max_position_x,max_position_y));
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
                        coords.add(changeCoord.changeCordinate(osmNodeInfo.get(refID)));
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
                            Polygon polygon = null;
                            if(coords.get(0).equals(coords.get(coords.size() - 1))) {
                                // In case : "way" geometry is closed polygon
                                CoordinateSequence seq = gf.getCoordinateSequenceFactory().create(coordsArray);
                                LinearRing lr = gf.createLinearRing(seq);
                                polygon = gf.createPolygon(lr);
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

    public static void main(String[] args) {
        JFrame jFrame = new JFrame("IndoorDistanceTest");
        jFrame.setContentPane(new IndoorMapmatchingSim().panelMain);
        jFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jFrame.pack();
        jFrame.setVisible(true);
    }

    private void createUIComponents() {
        // TODO: Place custom component creation code here
        panelCanvas = new CanvasPanel();
        panelCanvas.setSize(600, 600);
    }
}
