package server.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class ProgramRequest implements  Request{

    private String nume;
    private String cnp;
    private int locatie;
    private int tipTratament;
    private int oraTratament;
}
