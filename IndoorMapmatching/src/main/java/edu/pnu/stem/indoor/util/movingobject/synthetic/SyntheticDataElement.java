package edu.pnu.stem.indoor.util.movingobject.synthetic;

import org.locationtech.jts.geom.LineString;

public class SyntheticDataElement {
    private LineString raw_trajectory;
    private LineString noise_trajectory;
    private int[] ground_truth;

    SyntheticDataElement(LineString raw_trajectory, LineString noise_trajectory, int[] ground_truth){
        this.raw_trajectory = raw_trajectory;
        this.noise_trajectory = noise_trajectory;
        this.ground_truth = ground_truth;
    }

    public LineString getRaw_trajectory() {
        return raw_trajectory;
    }

    public LineString getNoise_trajectory() {
        return noise_trajectory;
    }

    public int[] getGround_truth() {
        return ground_truth;
    }
}
