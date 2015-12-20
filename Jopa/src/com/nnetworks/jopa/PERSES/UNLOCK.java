//----------------------------------------------------------------------
// PERSES.UNLOCK.java
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

public class UNLOCK extends PERSES
implements JOPAMethod
{

//----------------------------------------------------------------------

public void service ()
throws ServletException, IOException
{
  String sToken = reqLockToken();
  if (sToken == null) {
    respStatus(412, "Lock-Token header must be specified");
    return;
  }
  try {
    establishConnection();
    if (authenticate() > 0) {
      reqURI();
      operUNLOCK(sToken);
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

private void operUNLOCK
( String sToken
)
{
  Probe probe = locateProbe();
  if (this.iStatus == 200) {
    if (probe.nObjID == null) {
      respStatus(422, "Unprocessible entity");
      return;
    }
  }
  else {
    respStatus(this.iStatus, this.sStatus);
    return;
  }
  openLocks();
  this.locks.removeLock(probe.sSchema, sToken);
  cleanLocks();
  respStatus(204, "No Content");
}

//----------------------------------------------------------------------

} // class UNLOCK

//----------------------------------------------------------------------
