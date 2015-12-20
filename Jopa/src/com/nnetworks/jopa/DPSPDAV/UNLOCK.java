//----------------------------------------------------------------------
// DPSPDAV.UNLOCK.java
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

public class UNLOCK extends DPSPDAV
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
    if (authenticate(3) < 0) return;
    reqURI();
    operUNLOCK(this.sReqPath, sToken, reqIfv());
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
( String sPath
, String sToken
, String sIfv
)
{
  int    iStatus = 500;
  String sStatus = "Internal Server Error";
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(
         "begin NN$DAV.operUNLOCK(?, ?, ?, ?, ?); end;");
    cs.registerOutParameter(1, OracleTypes.INTEGER);
    cs.registerOutParameter(2, OracleTypes.VARCHAR);
    cs.setCHAR(3, encode(sPath));
//    cs.setString(3, sPath);
    cs.setString(4, sToken);
    if (sIfv == null)
      cs.setNull(5, OracleTypes.VARCHAR);
    else
      cs.setString(5, sIfv);
    cs.execute();
    iStatus = cs.getInt(1);     if (cs.wasNull()) iStatus = 500;
    sStatus = cs.getString(2);
    cs.close();
  }
  catch(SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "operUNLOCK/SQL: ", e.getMessage());
  }
  respStatus(iStatus, sStatus);
  if (iStatus == 204) super.commit(); else super.rollback();
}

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.4 $";
} // class UNLOCK

//----------------------------------------------------------------------
