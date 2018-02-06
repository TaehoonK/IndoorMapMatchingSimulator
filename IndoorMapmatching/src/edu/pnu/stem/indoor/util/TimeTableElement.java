package edu.pnu.stem.indoor.util;

public class TimeTableElement {
    int startCellIndex;
    int endCellIndex;
    int travelTime;

    public TimeTableElement(int startCellIndex, int endCellIndex, int travelTime) {
        this.startCellIndex = startCellIndex;
        this.endCellIndex = endCellIndex;
        this.travelTime = travelTime;
    }
}
