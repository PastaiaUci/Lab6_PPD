package server.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PayRequest implements Request{

    private String cnp;
    private int tratementType;
    private int location;
    private Hour hour;

}
