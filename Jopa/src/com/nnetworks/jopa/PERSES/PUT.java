//----------------------------------------------------------------------
// PERSES.PUT.java
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
import java.text.*;
import java.util.*;

//----------------------------------------------------------------------

public class PUT extends PERSES
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
      operPUT();
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

private void operPUT ()
{
  Probe probe = locateProbe();
  if (this.iStatus == 200) {
    if (probe.iLayer != LAYER_OBJECT) {
      respStatus(422, "Unprocessible entity");
      return;
    }
  }
  else if (this.iStatus == 0) {
    if (probe.iLayer != LAYER_OBJTYPE) {
      respStatus(404, "Resource not found");
      return;
    }
    this.iStatus = 200;
    probe.iLayer = LAYER_OBJECT;
    probe.bCollection = false;
    probe.sParentName = probe.sName;
    probe.sName = this.pi.getItem(this.pi.iLast);
    if (!probe.sHRef.endsWith("/")) probe.sHRef = probe.sHRef + '/';
    probe.sHRef = probe.sHRef + escape(probe.sName);
  }
  else {
    respStatus(this.iStatus, this.sStatus);
    return;
  }

  StringBuffer sb;
  int i;
  String sExt;

  switch (probe.iObjtype) {

    case OBJTYPE_PROCEDURE:
    case OBJTYPE_FUNCTION:
    case OBJTYPE_PACKAGE:
    case OBJTYPE_PACKAGE_BODY:
    case OBJTYPE_TRIGGER:
    case OBJTYPE_JAVA_SOURCE:
      sExt = stripExt(probe.sName, "").toLowerCase();
      if (sExt.equals(".sql")) {}
      else if (sExt.equals(decodeObjtypeExt(probe.iObjtype))) {}
      else {
        respStatus(422, "Incompatible object type.");
        return;
      }
      i = this.request.getContentLength();
      if (i < 1) i = BUFFER_SIZE;
      sb = new StringBuffer(i);
      i = reqContentBuffer(sb, 13);
      adjustCodeTail(sb);
      i = execDynSQL(sb);
      if (i > 0) {
        respStatus(200, "OK");
      }
      else {
        respStatus(409, "Failed to compile source");
      }
      break;

    case OBJTYPE_VIEW:
      i = this.request.getContentLength();
      if (i < 1) i = BUFFER_SIZE;
      sb = new StringBuffer(i);
      i = reqContentBuffer(sb, 13);
      adjustCodeTail(sb);
      i = execDynSQL(sb);
      if (i > 0) {
        respStatus(200, "OK");
      }
      else {
        respStatus(409, "Failed to compile view");
      }
      break;

    case OBJTYPE_ERROR:
      //respStatus(422, "Error messages are read-only.");
      // Притворяемся, что загрузили, а на самом деле...
      respStatus(200, "OK");
      break;

    default:
      respStatus(422, "Unprocessible entity");
  }
}

//----------------------------------------------------------------------

protected void adjustCodeTail (StringBuffer sb)
{
  char ch;
  int n = sb.length();
  for (int i = n-1; i > 0; i--) {
    ch = sb.charAt(i);
    if (ch <= ' ') {}
    else if (ch == '/') {
      sb.delete(i, n);
      break;
    }
    else {
      break;
    }
  }
}

//----------------------------------------------------------------------

} // class PUT

//----------------------------------------------------------------------
