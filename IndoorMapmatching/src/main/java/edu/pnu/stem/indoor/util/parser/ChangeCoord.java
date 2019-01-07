package edu.pnu.stem.indoor.util.parser;


import edu.pnu.stem.indoor.gui.IndoorMapmatchingSim;
import org.locationtech.jts.geom.Coordinate;

import java.awt.*;

/**
 * Created by STEM_KTH on 2017-06-02.
 * @author Taehoon Kim, Pusan National University, STEM Lab.
 */
public class ChangeCoord {
    public final static double CANVAS_MULTIPLE = 20.0;
    private final static double EARTH_RADIUS_KM = 6378.1370d;
    private static Coordinate min_position = new Coordinate(IndoorMapmatchingSim.CANVAS_LEFT_UPPER_X, IndoorMapmatchingSim.CANVAS_LEFT_UPPER_Y);
    private static Coordinate max_position = new Coordinate(IndoorMapmatchingSim.CANVAS_RIGHT_LOWER_X, IndoorMapmatchingSim.CANVAS_RIGHT_LOWER_Y);

    public static void setInitialInfo(Coordinate minPosition, Coordinate maxPosition)
    {
        min_position = minPosition;
        max_position = maxPosition;
    }

    public static Dimension getArea() {
        Dimension area;
        if(min_position.equals2D(new Coordinate(IndoorMapmatchingSim.CANVAS_LEFT_UPPER_X, IndoorMapmatchingSim.CANVAS_LEFT_UPPER_Y))
                && max_position.equals2D(new Coordinate(IndoorMapmatchingSim.CANVAS_RIGHT_LOWER_X, IndoorMapmatchingSim.CANVAS_RIGHT_LOWER_Y))) {
            area = new Dimension(IndoorMapmatchingSim.CANVAS_RIGHT_LOWER_X, IndoorMapmatchingSim.CANVAS_RIGHT_LOWER_Y);
        }
        else {
            area = new Dimension((int) (HaversineInM(new Coordinate(max_position.x, 0), new Coordinate(min_position.x, 0)) * CANVAS_MULTIPLE + 100),
                    (int) (HaversineInM(new Coordinate(0, max_position.y), new Coordinate(0, min_position.y)) * CANVAS_MULTIPLE + 10));
        }
        return area;
    }

    public static Coordinate changeCoordWithRatio(Coordinate targetPosition) {
        Double changedPositionX = (targetPosition.x - min_position.x) * CANVAS_MULTIPLE;
        Double changedPositionY = (targetPosition.y - min_position.y) * CANVAS_MULTIPLE;

        return new Coordinate(changedPositionX.intValue(), changedPositionY.intValue(), targetPosition.z);
    }

    public static Coordinate changeCoordWGS84toMeter(Coordinate targetPosition)
    {
        Double changedPositionX = HaversineInM(min_position, new Coordinate(targetPosition.x, min_position.y)) * CANVAS_MULTIPLE;
        Double changedPositionY = HaversineInM(min_position, new Coordinate(min_position.x, targetPosition.y)) * CANVAS_MULTIPLE;

        return new Coordinate(changedPositionX.intValue(), changedPositionY.intValue(), targetPosition.z);
    }

    private static double HaversineInM(Coordinate startGPSPosition, Coordinate endGPSPosition) {
        return (1000d * HaversineInKM(startGPSPosition,endGPSPosition));
    }

    private static double HaversineInKM(Coordinate startGPSPosition, Coordinate endGPSPosition) {
        double dLat = deg2rad(endGPSPosition.x - startGPSPosition.x);
        double dLon = deg2rad(endGPSPosition.y - startGPSPosition.y);
        double a = Math.sin(dLat / 2d) * Math.sin(dLat / 2d) +
                Math.cos(deg2rad(startGPSPosition.x)) * Math.cos(deg2rad(endGPSPosition.x)) *
                        Math.sin(dLon/2) * Math.sin(dLon/2);
        double c = 2d * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));

        return EARTH_RADIUS_KM * c;
    }

    // Convert degree value to radian value
    private static double deg2rad(double deg) {
        return deg * Math.PI / 180d;
    }

    // Convert radian value to degree value
    private static double rad2deg(double rad) {
        return rad * 180d / Math.PI;
    }
}
