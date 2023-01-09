package org.example;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Config {
    private int numberOfLocations;
    private int numberOfTreatments;
    private Integer[] treatmentsCost;
    private Integer[] treatmentsDuration;
    private Integer[][] maxClientsPerTreatment;
}
