package com.nnetworks.jopa.OWA;

import com.nnetworks.jopa.JOPAMethod;
import java.io.IOException;
import javax.servlet.ServletException;

// AAT ** Class com.nnetworks.jopa.OWA.HEAD not found 11.2015
public class HEAD extends OWA implements JOPAMethod {
    
public void service ()
    throws ServletException, IOException
    {
        // respStatus(200, HTTP_OK);
        // respContentLength(200);
        shell.log(3, "<= com.nnetworks.jopa.OWA.HEAD =>");
    }


    public HEAD() {
        super();
    }
private static String fileRevision = "$Revision: 11.2015 $";
}
