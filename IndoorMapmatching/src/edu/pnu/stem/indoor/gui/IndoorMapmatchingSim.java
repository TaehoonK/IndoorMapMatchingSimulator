package edu.pnu.stem.indoor.gui;

import com.vividsolutions.jts.geom.*;
import edu.pnu.stem.indoor.feature.IndoorFeatures;
import edu.pnu.stem.indoor.util.parser.ChangeCoord;
import edu.pnu.stem.indoor.util.IndoorUtils;
import edu.pnu.stem.indoor.util.parser.DataUtils;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.xml.sax.SAXException;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.regex.Pattern;

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
    private JButton buttonEvaluate;
    private JButton buttonCreateDoor;
    private JButton buttonCreateHole;
    private JButton buttonGetTR_nextOne;
    private JButton buttonGetOSMData;
    private JTextPane textPaneOriginal;
    private JScrollPane jScrollPane;
    private JButton buttonGetTR_prevOne;
    private JButton buttonGetBuildNGO;

    private final String RAW_TR_PATH = "C:\\Users\\timeo\\IdeaProjects\\github\\IndoorMapmatching\\Real_groundTruth";
    private final String TR_PATH = "C:\\Users\\timeo\\IdeaProjects\\github\\IndoorMapmatching\\Real_positioningTrajectory";

    private int trIndexView = 1;
    private LineString trajectory = null;
    private LineString trajectoryIF = null;
    private LineString trajectoryGT = null;

    private IndoorMapmatchingSim() {
        panelMain.setSize(1000,1000);
        gf = new GeometryFactory();

        buttonCreateHole.addActionListener(e -> ((CanvasPanel)panelCanvas).currentEditStatus = EditStatus.CREATE_HOLE);
        buttonCreateCell.addActionListener(e -> ((CanvasPanel)panelCanvas).currentEditStatus = EditStatus.CREATE_CELLSPACE);
        buttonCreatePath.addActionListener(e -> ((CanvasPanel)panelCanvas).currentEditStatus = EditStatus.CREATE_TRAJECTORY);
        buttonSelectCell.addActionListener(e -> ((CanvasPanel)panelCanvas).currentEditStatus = EditStatus.SELECT_CELLSPACE);
        buttonGetOSMData.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser("C:\\Users\\timeo\\IdeaProjects\\github\\IndoorMapmatching");
            FileNameExtensionFilter filter = new FileNameExtensionFilter("OSM Data (*.osm)", "osm");
            fileChooser.setFileFilter(filter);

            int returnVal = fileChooser.showOpenDialog(panelMain);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                try {
                    IndoorFeatures indoorFeatures = DataUtils.getIndoorFeaturesFromOSMXML(file);
                    ((CanvasPanel)panelCanvas).setIndoorFeatures(indoorFeatures);
                } catch (IOException | ParserConfigurationException | SAXException e1) {
                    e1.printStackTrace();
                }
            }
        });
        buttonEvaluate.addActionListener(e -> {
            long startTime = System.currentTimeMillis();
            File dir = new File(TR_PATH);
            File[] fileList = dir.listFiles();
            assert fileList != null;

            int SAMPLE_NUMBER = 1000;
            if(SAMPLE_NUMBER > fileList.length) SAMPLE_NUMBER = fileList.length;
            ExperimentResult[] experimentResults = new ExperimentResult[SAMPLE_NUMBER];
            ArrayList<String> keyList = new ArrayList<>();
            for(int i = 0 ; i < SAMPLE_NUMBER ; i++){
                String fileID = "";
                File file = fileList[i];
                if(file.isFile()){
                    System.out.println("\tFile Name = " + file.getName());
                    String[] fileName = file.getName().split("_");
                    fileID = fileName[fileName.length - 1].split(Pattern.quote("."))[0];

                    try {
                        LineString trajectory = DataUtils.getBuildNGoData(file);

                        if(trajectory.isEmpty()) {
                            System.out.println("Pass");
                            experimentResults[i] = null;
                            continue;
                        }
                        ((CanvasPanel)panelCanvas).setTrajectory(trajectory);
                    } catch (IOException | ParseException e1) {
                        e1.printStackTrace();
                        long endTime = System.currentTimeMillis();
                        System.out.println("Running Time :" + (endTime - startTime)/1000.0 + "sec");
                    }
                }

                String groundTruthFileName = "gt_Trajectory_";
                String[] stringGT = null;
                File groundTruth = new File(RAW_TR_PATH +"\\"+ groundTruthFileName + fileID + ".txt");
                try {
                    stringGT = DataUtils.getBuildNGoGroundTruth(groundTruth);
                } catch (IOException | ParseException e1) {
                    e1.printStackTrace();
                    long endTime = System.currentTimeMillis();
                    System.out.println("Running Time :" + (endTime - startTime)/1000.0 + "sec");
                }
                experimentResults[i] = ((CanvasPanel)panelCanvas).evaluateSIMM_Excel(fileID, keyList, stringGT);
                experimentResults[i] = ((CanvasPanel)panelCanvas).evaluateSIMM_forSIG(fileID, keyList, stringGT);
                ((CanvasPanel)panelCanvas).saveImage(fileID);
            }

            // 최종 결과 출력
            Workbook workbook = new XSSFWorkbook();
            Sheet summarySheet = workbook.createSheet("Summary");
            Row row = summarySheet.createRow(0);
            if(experimentResults[0] != null) {
                row.createCell(row.getLastCellNum()  + 1).setCellValue("ID");
                for(String key : keyList) {
                    row.createCell(row.getLastCellNum()).setCellValue("Accuracy: \n" + key);
                }
                for(String key : keyList) {
                    row.createCell(row.getLastCellNum()).setCellValue("TRUE count: \n" + key);
                }
                row.createCell(row.getLastCellNum()).setCellValue("Point count");
                row.createCell(row.getLastCellNum()).setCellValue("Trajectory Length\n Original");
                row.createCell(row.getLastCellNum()).setCellValue("Trajectory Length\n Indoor Filtered");
            }
            for(ExperimentResult er : experimentResults) {
                if(er != null) {
                    row = summarySheet.createRow(summarySheet.getLastRowNum() + 1);
                    row.createCell(row.getLastCellNum()  + 1).setCellValue(er.id);
                    for(String key : keyList) {
                        if(er.accuracy.containsKey(key)) {
                            row.createCell(row.getLastCellNum()).setCellValue(er.accuracy.get(key));
                        }
                        else {
                            row.createCell(row.getLastCellNum()).setCellValue("");
                        }
                    }
                    for(String key : keyList) {
                        if(er.trueCount.containsKey(key)) {
                            row.createCell(row.getLastCellNum()).setCellValue(er.trueCount.get(key));
                        }
                        else {
                            row.createCell(row.getLastCellNum()).setCellValue("");
                        }
                    }
                    row.createCell(row.getLastCellNum()).setCellValue(er.numTrajectoryPoint);
                    row.createCell(row.getLastCellNum()).setCellValue(er.trajectoryLength[0]);
                    row.createCell(row.getLastCellNum()).setCellValue(er.trajectoryLength[1]);
                    row.createCell(row.getLastCellNum()).setCellValue(er.trajectoryLength[2]);
                }
            }

            // Write all result in a excel file
            FileOutputStream outFile;
            try {
                outFile = new FileOutputStream("Result_Summary.xlsx");
                workbook.write(outFile);
                outFile.close();
                System.out.println("Experiments is End!!");
            } catch (Exception e1) {
                e1.printStackTrace();
                long endTime = System.currentTimeMillis();
                System.out.println("Running Time :" + (endTime - startTime)/1000.0 + "sec");
            }
            long endTime = System.currentTimeMillis();
            System.out.println("Running Time :" + (endTime - startTime)/1000.0 + "sec");
        });
        buttonGetBuildNGO.addActionListener(e -> {
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

                    LineString trajectory = DataUtils.getBuildNGoData(file);
                    if(trajectory.isEmpty()) return;
                    ((CanvasPanel)panelCanvas).setTrajectory(trajectory);

                    LineString lineWithMaxIndoorDistance = IndoorUtils.applyIndoorDistanceFilter(trajectory, ChangeCoord.CANVAS_MULTIPLE * 3, ((CanvasPanel)panelCanvas).getIndoorFeatures().getCellSpaces());
                    ((CanvasPanel)panelCanvas).setTrajectory_IF(lineWithMaxIndoorDistance);
                } catch (IOException | ParseException e1) {
                    e1.printStackTrace();
                }
            }

            String[] gt = null;
            String groundTruthFileName = "gt_Trajectory_";
            File groundTruth = new File(RAW_TR_PATH +"\\"+ groundTruthFileName + fileID);
            try {
                gt = DataUtils.getBuildNGoGroundTruth(groundTruth);
            } catch (IOException | ParseException e1) {
                e1.printStackTrace();
            }

            ((CanvasPanel)panelCanvas).doIndoorMapMatching(textPaneOriginal);
            ((CanvasPanel)panelCanvas).getGroundTruthResult(textPaneOriginal, gt);
        });
        buttonGetTR_nextOne.addActionListener(e -> {
            if(trajectory != null && trajectoryIF != null && trajectoryGT != null) {
                if(trajectory.getNumPoints() > trIndexView && trIndexView > 0) {
                    trIndexView++;

                    ((CanvasPanel)panelCanvas).setTrajectory((LineString) CanvasPanel.getSubLineString(trajectory, trIndexView));
                    ((CanvasPanel)panelCanvas).setTrajectory_IF((LineString) CanvasPanel.getSubLineString(trajectoryIF, trIndexView));
                }
                else {
                    trajectory = trajectoryIF = trajectoryGT = null;
                    trIndexView = 1;
                }
            }
            else {
                trajectory = ((CanvasPanel)panelCanvas).getTrajectory();
                trajectoryIF = ((CanvasPanel)panelCanvas).getTrajectory_IF();
            }
        });
        buttonGetTR_prevOne.addActionListener(e -> {
            if(trajectory != null && trajectoryIF != null && trajectoryGT != null) {
                if(trajectory.getNumPoints() > trIndexView && trIndexView > 2) {
                    trIndexView--;

                    ((CanvasPanel)panelCanvas).setTrajectory((LineString) CanvasPanel.getSubLineString(trajectory, trIndexView));
                    ((CanvasPanel)panelCanvas).setTrajectory_IF((LineString) CanvasPanel.getSubLineString(trajectoryIF, trIndexView));
                }
                else {
                    trIndexView = 3;
                }
            }
            else {
                trajectory = ((CanvasPanel)panelCanvas).getTrajectory();
                trajectoryIF = ((CanvasPanel)panelCanvas).getTrajectory_IF();
            }
        });
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
