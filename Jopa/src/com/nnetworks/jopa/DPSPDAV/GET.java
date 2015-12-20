//----------------------------------------------------------------------
// jopa.DPSPDAV.GET.java
//----------------------------------------------------------------------

package com.nnetworks.jopa.DPSPDAV;

import com.nnetworks.jopa.DPSPDAV.*;
import com.nnetworks.jopa.*;

import javax.servlet.*;
import javax.servlet.http.*;

import oracle.sql.*;
import oracle.jdbc.driver.*;

import java.sql.*;
import java.io.*;
import java.util.*;

//----------------------------------------------------------------------

public class GET extends DPSPDAV
implements JOPAMethod
{

//----------------------------------------------------------------------

public void service ()
throws ServletException, IOException
{
  try {
    establishConnection();
    if (authenticate(1) < 0) return;
    reqURI();
    operGET(this.sReqPath);
  }
  catch (JOPAException e) {
    respFatal(500, e);
  }
  finally {
    releaseConnection();
  }
}

//----------------------------------------------------------------------

private void operGET
( String sPath
)
{
  int    iStatus = 500;
  String sStatus = "Internal Server Error";
  String sETag = null;
  BLOB   blobData = null;
  String sMIMEType = null;
  String sCharset = null;
  DATE   dt = null;
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(
        "begin NN$DAV.operGET(?, ?, ?, ?, ?, ?, ?, ?); end;");
    cs.registerOutParameter(1, OracleTypes.INTEGER);
    cs.registerOutParameter(2, OracleTypes.VARCHAR);
    cs.registerOutParameter(3, OracleTypes.VARCHAR);
    cs.registerOutParameter(4, OracleTypes.BLOB);
    cs.registerOutParameter(5, OracleTypes.VARCHAR);
    cs.registerOutParameter(6, OracleTypes.VARCHAR);
    cs.registerOutParameter(7, OracleTypes.DATE);
    cs.setCHAR(8, encode(sPath));
//    cs.setString(8, sPath);
    cs.execute();
    iStatus = cs.getInt(1);     if (cs.wasNull()) iStatus = 500;
    sStatus = cs.getString(2);
    if (iStatus == 200) {
      sETag = cs.getString(3);      if (cs.wasNull()) sETag = null;
      blobData = cs.getBLOB(4);     if (cs.wasNull()) blobData = null;
      sMIMEType = cs.getString(5);  if (cs.wasNull()) sMIMEType = null;
      sCharset = cs.getString(6);   if (cs.wasNull()) sCharset = null;
      dt = cs.getDATE(7);           if (cs.wasNull()) dt = null;
    }
    cs.close();
  }
  catch (SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "operGET/SQL: ", e.getMessage());
    iStatus = 500;
    sStatus = e.getMessage();
  }
  //
  respStatus(iStatus, sStatus);
  if (iStatus == 200) {
    respWebDAV(3);
    if (sETag != null) {
      respHeader("ETag", sETag);
    }
    if (sMIMEType != null) {
      if (sCharset != null) {
        if (sMIMEType.startsWith("text/")) {
          sMIMEType = sMIMEType + "; charset=\"" + sCharset + '\"';
        }
      }
      respContentType(sMIMEType);
    }
    if (blobData != null) {
      try { respContentLength((int)blobData.length()); }
      catch (SQLException e) {}
      respContent(blobData);
    }
    else {
      respContentLength(0);
    }
  }
}

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.4 $";
} // class GET

//----------------------------------------------------------------------
