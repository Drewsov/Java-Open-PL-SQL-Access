//----------------------------------------------------------------------
// jopa.DPSPDAV.MKCOL.java
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

public class MKCOL extends DPSPDAV
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
    if (this.request.getContentLength() > 0) {
      respStatus(415, "Unsupported Media Type");
    }
    else {
      operMKCOL(this.sReqPath, reqIfv());
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

private void operMKCOL
( String sPath
, String sIfv
)
{
  int    iStatus = 500;
  String sStatus = "Internal Server Error";
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(
        "begin NN$DAV.operMKCOL(?, ?, ?, ?); end;");
    cs.registerOutParameter(1, OracleTypes.INTEGER);
    cs.registerOutParameter(2, OracleTypes.VARCHAR);
    cs.setCHAR(3, encode(sPath));
//    cs.setString(3, sPath);
    if (sIfv == null)
      cs.setNull(4, OracleTypes.VARCHAR);
    else
      cs.setString(4, sIfv);
    cs.execute();
    iStatus = cs.getInt(1);     if (cs.wasNull()) iStatus = 500;
    sStatus = cs.getString(2);
    cs.close();
  }
  catch (SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "operMKCOL/SQL: ", e.getMessage());
    iStatus = 500;
    sStatus = e.getMessage();
  }
  respStatus(iStatus, sStatus);
  if (iStatus == 201) super.commit(); else super.rollback();
}

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.4 $";
} // class MKCOL

//----------------------------------------------------------------------
