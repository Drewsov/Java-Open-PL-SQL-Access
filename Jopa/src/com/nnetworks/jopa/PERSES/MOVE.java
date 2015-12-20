//----------------------------------------------------------------------
// PERSES.MOVE.java
//----------------------------------------------------------------------

package com.nnetworks.jopa.PERSES;

import com.nnetworks.jopa.PERSES.*;
import com.nnetworks.jopa.*;

import javax.servlet.*;
import javax.servlet.http.*;

import oracle.sql.*;
import oracle.jdbc.driver.*;

import java.sql.*;
import java.io.*;
import java.util.*;

//----------------------------------------------------------------------

public class MOVE extends PERSES
implements JOPAMethod
{

//----------------------------------------------------------------------

public void service ()
throws ServletException, IOException
{
  try {
    establishConnection();
    if (authenticate() > 0) {
      reqURI();
      PathInfo piDst = reqDst();
      if (piDst == null) {
        respStatus(409, "Conflict: Empty destination");
        return;
      }
      operMOVE(piDst);
    }
  }
  catch (JOPAException e) {
    respFatal(500, e);
  }
  finally {
    releaseConnection();
  }
}

//----------------------------------------------------------------------

private void operMOVE
( PathInfo piDst
)
{
  Probe probe = locateProbe();
  if (this.iStatus == 200) {
    if (probe.iLayer != LAYER_OBJECT) {
      respStatus(422, "Unprocessible entity");
      return;
    }
  }
  else if (this.iStatus == 0) {
    respStatus(404, this.sStatus);
    return;
  }
  else {
    respStatus(this.iStatus, this.sStatus);
    return;
  }

  respStatus(405, "Method Not Allowed");
}

//----------------------------------------------------------------------

} // class MOVE

//----------------------------------------------------------------------
