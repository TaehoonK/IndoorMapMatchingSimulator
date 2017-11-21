package edu.pnu.stem.indoor.util;

import com.vividsolutions.jts.geom.Coordinate;

/**
 * Created by STEM_KTH on 2017-06-02.
 * @author Taehoon Kim, Pusan National University, STEM Lab.
 */
public class ChangeCoord {
    private final static double EARTH_RADIUS_KM = 6378.1370d;
    private final static double CANVAS_MULTIPLE = 20;
    private Coordinate min_position;
    private Coordinate max_position;

    public ChangeCoord(Coordinate min_position, Coordinate max_position)
    {
        this.min_position = min_position;
        this.max_position = max_position;
    }

    public Coordinate changeCordinate(Coordinate targetPosition)
    {
        Double changedPositionX = HaversineInM(min_position, new Coordinate(targetPosition.x, min_position.y)) * CANVAS_MULTIPLE;
        Double changedPositionY = HaversineInM(min_position, new Coordinate(min_position.x, targetPosition.y)) * CANVAS_MULTIPLE;

        return new Coordinate(changedPositionX.intValue(), changedPositionY.intValue());
    }

    private double HaversineInM(Coordinate startGPSPosition, Coordinate endGPSPosition) {
        return (1000d * HaversineInKM(startGPSPosition,endGPSPosition));
    }

    private double HaversineInKM(Coordinate startGPSPosition, Coordinate endGPSPosition) {
        double dLat = deg2rad(endGPSPosition.x - startGPSPosition.x);
        double dLon = deg2rad(endGPSPosition.y - startGPSPosition.y);
        double a = Math.sin(dLat / 2d) * Math.sin(dLat / 2d) +
                Math.cos(deg2rad(startGPSPosition.x)) * Math.cos(deg2rad(endGPSPosition.x)) *
                        Math.sin(dLon/2) * Math.sin(dLon/2);
        double c = 2d * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));

        return EARTH_RADIUS_KM * c;
    }

    // Convert degree value to radian value
    private double deg2rad(double deg) {
        return deg * Math.PI / 180d;
    }

    // Convert radian value to degree value
    private double rad2deg(double rad) {
        return rad * 180d / Math.PI;
    }
}
