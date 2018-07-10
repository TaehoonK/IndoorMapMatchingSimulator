package edu.pnu.stem.indoor.util.parser;

import com.vividsolutions.jts.geom.*;
import edu.pnu.stem.indoor.feature.CellSpace;
import edu.pnu.stem.indoor.feature.IndoorFeatures;
import edu.pnu.stem.indoor.util.ChangeCoord;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;

public class OSMtoIndoorFeatures {

    public static IndoorFeatures getIndoorFeatures(File inputFile) throws ParserConfigurationException, IOException, SAXException {
        GeometryFactory gf = new GeometryFactory();
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

        IndoorFeatures indoorFeatures = new IndoorFeatures(gf);
        for(CellSpace cellSpace : cellSpaces) {
            Polygon cellGeom = cellSpace.getGeom();
            for(LineString doorGeom : doorGeoms) {
                if(cellGeom.covers(doorGeom)) {
                    cellSpace.addDoors(doorGeom);
                }
            }
            indoorFeatures.addCellSpace(cellSpace);
        }

        return indoorFeatures;
    }

    public static IndoorFeatures getIndoorFeatures(InputStream inputStream) throws ParserConfigurationException, IOException, SAXException {
        GeometryFactory gf = new GeometryFactory();
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document document = dBuilder.parse(inputStream);
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

        IndoorFeatures indoorFeatures = new IndoorFeatures(gf);
        for(CellSpace cellSpace : cellSpaces) {
            Polygon cellGeom = cellSpace.getGeom();
            for(LineString doorGeom : doorGeoms) {
                if(cellGeom.covers(doorGeom)) {
                    cellSpace.addDoors(doorGeom);
                }
            }
            indoorFeatures.addCellSpace(cellSpace);
        }

        return indoorFeatures;
    }

    public static LineString getBuildNGoData(InputStream inputStream) throws IOException {
        GeometryFactory gf = new GeometryFactory();
        ArrayList<Coordinate> coords = new ArrayList<>();
        BufferedReader reader= new BufferedReader(new InputStreamReader(inputStream,"EUC_KR"));

        String line;
        while((line = reader.readLine()) != null) {
            if(line.split("/").length > 1) {
                Coordinate coord = new Coordinate(Double.valueOf(line.split("/")[1]),
                        Double.valueOf(line.split("/")[0]));
                coords.add(new Coordinate(ChangeCoord.changeCoordWGS84toMeter(coord)));
            }
        }

        if(coords.size() > 1) {
            Coordinate[] trajectoryData = new Coordinate[coords.size()];
            for(int i = 0; i < coords.size(); i++) {
                trajectoryData[i] = coords.get(i);
            }
            return gf.createLineString(trajectoryData);
        }
        else {
            return gf.createLineString(new Coordinate[]{});
        }
    }
}
