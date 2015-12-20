//----------------------------------------------------------------------
// jopa.DPSPDAV.PUT.java
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
import java.text.*;
import java.util.*;

//----------------------------------------------------------------------

public class PUT extends DPSPDAV
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
    BLOB blob = readContentBlob();
    if (blob != null) {
      String sKind = guessKind(this.sReqName, "DAT");
      String sMIMEType =
        guessMIMEType
        ( this.sReqName
        , sKind.equals("PSP") ? "text/plain" : this.request.getContentType()
        );
      String sCharset = this.requestCharset;
      if (sKind.equals("PSP")) 
        sCharset = this.effCharset;
      operPUT(this.sReqPath, blob, sKind, sMIMEType, sCharset, reqIfv());
      freeTemporary(blob);
    }
    else {
      respStatus(204, "No Content");
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

private void operPUT
( String sPath
, BLOB   blobData
, String sKind
, String sMIMEType
, String sCharset
, String sIfv
)
{
  if (sIfv != null) shell.log(3, "Ifv: ", sIfv);
  int    iStatus = 500;
  String sStatus = "Internal Server Error";
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(
        "begin NN$DAV.operPUT(?, ?, ?, ?, ?, ?, ?, ?); end;");
    cs.registerOutParameter(1, OracleTypes.INTEGER);
    cs.registerOutParameter(2, OracleTypes.VARCHAR);
    cs.setCHAR(3, encode(sPath));
//    cs.setString(3, sPath);
    cs.setBLOB(4, blobData);
    cs.setCHAR(5, encode(sKind));
//    cs.setString(5, sKind);
    if (sMIMEType == null)
      cs.setNull(6, OracleTypes.VARCHAR);
    else
      cs.setString(6, sMIMEType);
    if (sCharset == null)
      cs.setNull(7, OracleTypes.VARCHAR);
    else
      cs.setString(7, sCharset);
    if (sIfv == null)
      cs.setNull(8, OracleTypes.VARCHAR);
    else
      cs.setString(8, sIfv);
    cs.execute();
    iStatus = cs.getInt(1);     if (cs.wasNull()) iStatus = 500;
    sStatus = cs.getString(2);
    cs.close();
  }
  catch (SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "operPUT/SQL: ", e.getMessage());
    iStatus = 500;
    sStatus = e.getMessage();
  }
  respStatus(iStatus, sStatus);
  if (iStatus == 200) super.commit(); else super.rollback();
}

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.6 $";
} // class PUT

//----------------------------------------------------------------------
