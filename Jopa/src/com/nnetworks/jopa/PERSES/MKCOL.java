//----------------------------------------------------------------------
// PERSES.MKCOL.java
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

public class MKCOL extends PERSES
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
      if (this.request.getContentLength() > 0) {
        respStatus(415, "Unsupported Media Type");
      }
      else {
        operMKCOL();
      }
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

private void operMKCOL ()
{
  Probe probe = locateProbe();
  if (this.iStatus == 200) {
    if (!probe.bCollection) {
      respStatus(422, "Unprocessible entity");
      return;
    }
  }
  else if (this.iStatus == 0) {
    respStatus(405, "Method Not Allowed.");
    return;
  }
  else {
    respStatus(this.iStatus, this.sStatus);
    return;
  }

  respStatus(201, "Created.");
}

//----------------------------------------------------------------------

} // class MKCOL

//----------------------------------------------------------------------
