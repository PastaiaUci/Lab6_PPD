package org.example.model;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class ProgramRequest implements Request {

    private String name;
    private String cnp;
    private int location;
    private int treatmentType;
    private Hour treatmentTime;
}
