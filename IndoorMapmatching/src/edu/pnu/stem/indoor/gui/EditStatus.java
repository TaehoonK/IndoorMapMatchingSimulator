package edu.pnu.stem.indoor.gui;

import java.io.Serializable;

/**
 * This indicates the mode of the current tool.
 *
 * Created by STEM_KTH on 2017-05-18.
 * @author Taehoon Kim, Pusan National University, STEM Lab.
 */
public enum EditStatus implements Serializable {
    NONE,
    GET_RELATED_EDGE,
    CREATE_CELLSPACE,
    CREATE_TRAJECTORY,
    CREATE_DOOR,
    CREATE_HOLE,
    SELECT_CELLSPACE
}
