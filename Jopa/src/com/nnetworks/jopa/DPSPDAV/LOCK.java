//----------------------------------------------------------------------
// DPSPDAV.LOCK.java
//----------------------------------------------------------------------

package com.nnetworks.jopa.DPSPDAV;

import com.nnetworks.jopa.DPSPDAV.*;
import com.nnetworks.jopa.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.jdom.*;
import org.jdom.input.*;
import org.jdom.output.*;

import oracle.sql.*;
import oracle.jdbc.driver.*;

import java.sql.*;
import java.io.*;
import java.util.*;

//----------------------------------------------------------------------

public class LOCK extends DPSPDAV
implements JOPAMethod
{

//----------------------------------------------------------------------

public void service ()
throws ServletException, IOException
{
  try {
    establishConnection();
    if (authenticate(3) < 0) return;
    reqURI();
    if (this.request.getContentLength() > 0) {
      reqDepth(0, Integer.MAX_VALUE);
      if (reqXMLContent()) {
        operLOCK
        ( this.sReqPath
        , reqLockOwner("SYSTEM")
        , reqTimeout()
        , (this.iDepth == 0 ? "0" : "I")
        , "E"
        , "W"
        , reqIfv()
        );
      }
      else {
        respStatus(412, "Precondition Failed");
      }
    }
    else {
      String sToken = reqLockToken();
      if (sToken == null) {
        respStatus(412, "Lock-Token header must be specified");
      }
      else {
        operRELOCK
        ( this.sReqPath
        , sToken
        , reqTimeout()
        , reqIfv()
        );
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

private void operLOCK
( String sPath
, String sOwner
, NUMBER nTimeout
, String sDepth
, String sScope
, String sType
, String sIfv
)
{
  int    iStatus = 500;
  String sStatus = "Internal Server Error";
  String sToken  = null;
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(
         "begin NN$DAV.operLOCK(?, ?, ?, ?, ?, ?, ?, ?, ?, ?); end;");
    cs.registerOutParameter(1, OracleTypes.INTEGER);
    cs.registerOutParameter(2, OracleTypes.VARCHAR);
    cs.registerOutParameter(3, OracleTypes.VARCHAR);
    cs.setCHAR(4, encode(sPath));
//    cs.setString(4, sPath);
    cs.setString(5, sOwner);
    if (nTimeout != null)
      cs.setNUMBER(6, nTimeout);
    else
      cs.setNull(6, OracleTypes.NUMBER);
    cs.setString(7, sDepth);
    cs.setString(8, sScope);
    cs.setString(9, sType);
    if (sIfv == null)
      cs.setNull(10, OracleTypes.VARCHAR);
    else
      cs.setString(10, sIfv);
    cs.execute();
    iStatus = cs.getInt(1);     if (cs.wasNull()) iStatus = 500;
    sStatus = cs.getString(2);
    if (iStatus == 200) {
      sToken = cs.getString(3);
    }
    cs.close();
  }
  catch(SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "operLOCK/SQL: ", e.getMessage());
  }
  if (iStatus == 200) {
    super.commit();
    respLocked(sToken);
  }
  else {
    super.rollback();
    respStatus(iStatus, sStatus);
  }
}

//----------------------------------------------------------------------

private void operRELOCK
( String sPath
, String sToken
, NUMBER nTimeout
, String sIfv
)
{
  int    iStatus = 500;
  String sStatus = "Internal Server Error";
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(
         "begin NN$DAV.operRELOCK(?, ?, ?, ?, ?, ?); end;");
    cs.registerOutParameter(1, OracleTypes.INTEGER);
    cs.registerOutParameter(2, OracleTypes.VARCHAR);
    cs.setCHAR(3, encode(sPath));
//    cs.setString(3, sPath);
    cs.setString(4, sToken);
    if (nTimeout != null)
      cs.setNUMBER(5, nTimeout);
    else
      cs.setNull(5, OracleTypes.NUMBER);
    if (sIfv == null)
      cs.setNull(6, OracleTypes.VARCHAR);
    else
      cs.setString(6, sIfv);
    cs.execute();
    iStatus = cs.getInt(1);     if (cs.wasNull()) iStatus = 500;
    sStatus = cs.getString(2);
    cs.close();
  }
  catch(SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "operRELOCK/SQL: ", e.getMessage());
  }
  if (iStatus == 200) {
    super.commit();
    respLocked(sToken);
  }
  else {
    super.rollback();
    respStatus(iStatus, sStatus);
  }
}

//----------------------------------------------------------------------
// TimeOut  = "Timeout" ":" 1#TimeType
// TimeType = ("Infinite" | "Second-" 1*digits | "Extend" field-value)

private NUMBER reqTimeout ()
{
  String sTimeType;
  String sTimeout = this.request.getHeader("Timeout");
  if (sTimeout != null) {
    StringTokenizer st = new StringTokenizer(sTimeout, ",");
    while (st.hasMoreTokens()) {
      sTimeType = st.nextToken().trim().toLowerCase();
      if (sTimeType.equals("infinite")) {
        return null;  //++ new NUMBER(0);  new NUMBER(86400); NUMBER(Integer.MAX_VALUE);
      }
      else if (sTimeType.startsWith("second-")) {
        String s = sTimeType.substring(7).trim();
        try { return new NUMBER(Integer.parseInt(s, 10)); }
        catch (NumberFormatException e) {}
      }
    }
  }
  return null;
}

//----------------------------------------------------------------------

private String reqLockOwner (String sDefOwner)
{
  if (this.xreqElem != null) {
    String sOwner;
    List list = this.xreqElem.getChildren();
    Iterator iter = list.iterator();
    while (iter.hasNext()) {
      Element child = (Element)(iter.next());
      if (child.getName().equals("owner")) {
        List cl = child.getChildren();
        if (!cl.isEmpty()) {
          Element own = (Element)(cl.get(0));
          if (own.getName().equals("href")) {
            sOwner = own.getTextTrim();
            if (sOwner != null) {
              if (sOwner.length() > 0) return sOwner;
            }
          }
        }
        else {
          sOwner = child.getTextTrim();
          if (sOwner != null) {
            if (sOwner.length() > 0) return sOwner;
          }
        }
      }
    }
  }
  return sDefOwner;
}

//----------------------------------------------------------------------

private void respLocked
( String sToken
)
{
  respStatus(200, "OK");
  respWebDAV(2);
  respDoc("prop");
  respLockDiscovery(this.xrespElem, borrowNS(this.xreqElem), sToken);
  respXMLContent("UTF-8");
}

//----------------------------------------------------------------------

private void respLockToken (String sLockToken)
{
  StringBuffer sb = new StringBuffer(64);
  sb.append('<');
  sb.append(LOCK_TOKEN_SCHEMA);
  sb.append(sLockToken);
  sb.append('>');
  respHeader("Lock-Token", sb.toString());
}

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.5 $";
} // class LOCK

//----------------------------------------------------------------------
