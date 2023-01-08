package server;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import server.model.Hour;
import server.model.ProgramRequest;
import server.model.ProgramResponse;
import server.model.Request;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MedicalServiceImpl {

    private List<Interval> intervals = new ArrayList<>;

    public synchronized ProgramResponse processProgramRequest(ProgramRequest request){
        intervals.add(new Interval());
        var matchingIntervals = intervals.stream()
                .filter(interval -> interval.getLocation() == request.getLocatie() && interval.getTratemntType() == request.getTipTratament())
                .sorted(Comparator.comparingInt(i -> i.minutes))
                .toList();

    }


    @AllArgsConstructor
    @Getter
    @Setter
    private class Interval{
        private String cnp;
        private int location;
        private int tratemntType;
        private int minutes;
    }
}
