package edu.pnu.stem.indoor.util.mapmatching;

import com.vividsolutions.jts.geom.LineString;
import edu.pnu.stem.indoor.IndoorFeatures;

/**
 * Created by STEM_KTH on 2017-07-25.
 * @author Taehoon Kim, Pusan National University, STEM Lab.
 * */
public interface IndoorMapMatching {

    /**
     * This function sets IndoorFeatures within a class to do map matching.
     * Usually used in constructors of class.
     *
     * @param indoorFeatures Space information for map matching
     * */
    void setIndoorFeatures(IndoorFeatures indoorFeatures);

    /**
     * This function returns the map-matched result for a given trajectory.
     * The result uses the label of the cell space.
     *
     * @param trajectory given trajectory
     * @return map-matched result
     * */
    String[] getMapMatchingResult(LineString trajectory);
}
