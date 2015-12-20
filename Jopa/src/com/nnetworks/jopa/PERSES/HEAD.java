//----------------------------------------------------------------------
// PERSES.HEAD.java
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

public class HEAD extends PERSES
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
      operHEAD();
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

private void operHEAD ()
{
  Probe probe = locateProbe();
  if (this.iStatus != 200) {
    respStatus(this.iStatus == 0 ? 404 : this.iStatus, this.sStatus);
    return;
  }
  if (probe.iLayer != LAYER_OBJECT) {
    respStatus(204, "No Content");
    return;
  }

  respStatus(200, "OK");
  respWebDAV(2);
  if (probe.nObjID != null) {
    respHeader("ETag", calcETag(probe));
  }
  { DATE d = probe.dUpd;
    if (d == null) d = probe.dCre;
    if (d != null) respHeader("Last-Modified", fmtGMT(d));
  }
  respContentType("text/plain; charset=\"" + this.effCharset + '\"');

  byte[] au = readObject(probe);
  if (au != null) {
    respContentLength(au.length);
    au = null;
  }
  else {
    respContentLength(0);
  }
}

//----------------------------------------------------------------------

} // class HEAD

//----------------------------------------------------------------------
