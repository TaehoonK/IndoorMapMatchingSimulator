package edu.pnu.stem.indoor.util.mapmatching;

import edu.pnu.stem.indoor.feature.IndoorFeatures;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;

/**
 *
 *
 * Created by STEM_KTH on 2017-07-25.
 * @author Taehoon Kim, Pusan National University, STEM Lab.
 */
public class DirectIndoorMapMatching implements IndoorMapMatching {
    private IndoorFeatures indoorFeatures;

    public DirectIndoorMapMatching(IndoorFeatures indoorFeatures) {
        setIndoorFeatures(indoorFeatures);
    }

    @Override
    public void setIndoorFeatures(IndoorFeatures indoorFeatures) {
        this.indoorFeatures = indoorFeatures;
    }

    @Override
    public String[] getMapMatchingResult(LineString trajectory) {
        String[] mapMatchingResult = new String[trajectory.getNumPoints()];
        int[] dimmResult = getDIMMResult(trajectory);
        for(int i = 0; i < dimmResult.length; i++) {
            mapMatchingResult[i] = indoorFeatures.getCellSpaceLabel(dimmResult[i]);
        }

        return mapMatchingResult;
    }

    /**
     *
     * */
    public int[] getDIMMResult(LineString trajectory) {
        int[] dimmResult = new int[trajectory.getNumPoints()];

        for(int i = 0; i < trajectory.getNumPoints(); i++){
            Coordinate coordinate = trajectory.getCoordinateN(i);
            int[] candidateCellIndexArray = indoorFeatures.getCellSpaceIndex(coordinate);
            if(candidateCellIndexArray.length == 1) {
                dimmResult[i] = candidateCellIndexArray[0];
            }
            else {
                /*
                If there is more than one DIMM result for one coordinate
                If there exist the same value as the DIMM result for the previous coordinate,
                it is set to that value, otherwise it is decided as the first value
                * */
                dimmResult[i] = -1;
                for (int candidateCellIndex : candidateCellIndexArray) {
                    if (i > 0 && dimmResult[i - 1] == candidateCellIndex) {
                        dimmResult[i] = candidateCellIndex;
                        break;
                    }
                }
                if(dimmResult[i] == -1) {
                    dimmResult[i] = candidateCellIndexArray[0];
                }
            }
        }
        return dimmResult;
    }

    int getDIMMResult(Coordinate coord) {
        return indoorFeatures.getCellSpaceIndex(coord)[0];
    }
}
