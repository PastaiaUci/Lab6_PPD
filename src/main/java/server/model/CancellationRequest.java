package server.model;

import lombok.Getter;
import lombok.Setter;

@Setter@Getter
public class CancellationRequest implements Request {
    private String cnp;
}
