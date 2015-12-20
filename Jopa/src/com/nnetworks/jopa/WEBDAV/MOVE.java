//----------------------------------------------------------------------
// WEBDAV.MOVE.java
//
// $Id: MOVE.java,v 1.6 2005/04/19 12:19:53 Bob Exp $
//
//----------------------------------------------------------------------

package com.nnetworks.jopa.WEBDAV;

import com.nnetworks.jopa.WEBDAV.*;
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

public class MOVE extends WEBDAV
implements JOPAMethod
{

//----------------------------------------------------------------------

public void service ()
throws ServletException, IOException
{
  try {
    establishConnection();

    // Authentication:
    int iAccl = authorize();
    if (iAccl <= 0) {
      respAuthenticate("Introduce yourself for NTS WebDAV");
      return;
    }
    if (iAccl < 5) {
      respStatus(403, HTTP_FORBIDDEN);
    }

    reqURI();
    PathInfo piDst = reqDst();
    if (piDst == null) {
      respStatus(409, HTTP_CONFLICT);
      return;
    }

    impersonate(iAccl);
    NUMBER nKey = locatePath(this.sReqPath);
    if (nKey == null) {
      respStatus(404, HTTP_NOT_FOUND);
      return;
    }

    NUMBER nDstDir = locatePath(piDst.merge(1, piDst.iLast));
    if (nDstDir == null) {
      respStatus(409, HTTP_CONFLICT);
      return;
    }

    String sDstName = piDst.getItem(-1);
    if (!uniqueName(nDstDir, sDstName, nKey)) {
      respStatus(412, HTTP_PRECONDITION_FAILED);
      return;
    }

    NUMBER nDir = getFolder(nKey);
    if (nDir != nDstDir) {
      if (!setFolder(nKey, nDstDir)) {
        respStatus(412, HTTP_PRECONDITION_FAILED);
        return;
      }
    }
    if (!this.sReqName.equals(sDstName)) {
      if (!setName(nKey, sDstName)) {
        respStatus(412, HTTP_PRECONDITION_FAILED);
        rollback();
        return;
      }
      String sDisp0 = getDisp(nKey, "NUL");
      if (sDisp0.equals("NUL")) {
        String sDisp1 = guessDisp(sDstName);
        if (!sDisp1.equals(sDisp0)) {
          setDisp(nKey, sDisp1);
          if (sDisp1.equals("PSP")) {
            //shell.log(3, "|| createUnit-1: ", sDstName);
            createUnit(nDstDir, sDstName);
          }
        }
      }
      else if (sDisp0.equals("PSP")) {
        NUMBER nRef = getRef(nKey);
        if (nRef == null) {
          //shell.log(3, "|| createUnit-2: ", sDstName);
          createUnit(nDstDir, sDstName);
        }
        else if (nRef.equals(nKey)) {
          //shell.log(3, "|| createUnit-3: ", sDstName);
          createUnit(nDstDir, sDstName);
        }
        else if (this.sReqName.toLowerCase().startsWith("untitled")) {
          //shell.log(3, "|| setUnitAttr(): ", sDstName);
          setUnitAttr(nRef, "NAME", sDstName);
          setUnitAttr(nRef, "DOMAIN",
            this.props.getProperty("username", "SYSTEM").toUpperCase());
        }
      }
    }

    commit();
    respStatus(201, HTTP_CREATED);
  }
  catch (JOPAException e) {
    respFatal(500, e);
  }
  finally {
    releaseConnection();
  }
}

//----------------------------------------------------------------------

protected NUMBER getFolder (NUMBER nKey)
{
  NUMBER nDir = null;
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(
        "begin ? := NN$NTS.getFolder(?); end;");
    cs.registerOutParameter(1, OracleTypes.NUMBER);
    cs.setNUMBER(2, nKey);
    cs.execute();
    nDir = cs.getNUMBER(1);
    if (cs.wasNull()) nDir = null;
    cs.close();
  }
  catch(SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "getFolder/SQL: ", e.getMessage());
  }
  return nDir;
}

//----------------------------------------------------------------------

protected boolean setFolder (NUMBER nKey, NUMBER nDir)
{
  boolean b = false;
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(
         "begin ? := NN$NTS.setFolder(?, ?); end;");
    cs.registerOutParameter(1, OracleTypes.NUMBER);
    cs.setNUMBER(2, nKey);
    cs.setNUMBER(3, nDir);
    cs.executeUpdate();
    NUMBER n = cs.getNUMBER(1);
    if (!cs.wasNull()) b = (n.intValue() > 0);
    cs.close();
  }
  catch(SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "setFolder/SQL: ", e.getMessage());
  }
  return b;
}

//----------------------------------------------------------------------

private boolean setName (NUMBER nKey, String sName)
{
  boolean b = false;
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(
         "begin ? := NN$NTS.setName(?, ?); end;");
    cs.registerOutParameter(1, OracleTypes.NUMBER);
    cs.setNUMBER(2, nKey);
    cs.setCHAR(3, encode(sName));
//    cs.setString(3, sName);
    cs.executeUpdate();
    NUMBER n = cs.getNUMBER(1);
    if (!cs.wasNull()) b = (n.intValue() > 0);
    cs.close();
  }
  catch(SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "setName/SQL: ", e.getMessage());
  }
  return b;
}

//----------------------------------------------------------------------

private void setUnitAttr (NUMBER nRef, String sName, String sData)
{
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(
         "begin NN$PSP_UNIT.storeAttr(?, ?, ?); end;");
    cs.setNUMBER(1, nRef);
    cs.setString(2, sName);
    cs.setCHAR(3, encode(sData));
//    cs.setString(3, sData);
    cs.execute();
    cs.close();
  }
  catch(SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "setUnitAttr/SQL: ", e.getMessage());
  }
}

//----------------------------------------------------------------------

private void createUnit
( NUMBER     nDir
, String     sName
) throws JOPAException
{
  String stmt = shell.getCode("createUnitDAV");
  StringBuffer prof = new StringBuffer(256);
  prof.append(" NAME=\"");
    prof.append(sName);
    prof.append("\"\r\n");
  prof.append(" DOMAIN=\"");
    prof.append(this.props.getProperty("username", "SYSTEM").toUpperCase());
    prof.append("\"\r\n");
  prof.append(" CODE_TYPE=\"");
    prof.append("HTML");
    prof.append("\"\r\n");
  prof.append(" PROCESSOR=\"");
    prof.append("FLAT");
    prof.append("\"\r\n");
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(stmt);
    cs.setNUMBER(1, nDir);
    cs.setCHAR(2, encode(sName));
//    cs.setString(2, sName);
    cs.setCHAR(3, encode(prof.toString()));
//    cs.setString(3, prof.toString());
    cs.execute();
    cs.close();
  }
  catch(SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "createUnit/SQL: ", e.getMessage());
  }
}

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.6 $";
} // class MOVE

//----------------------------------------------------------------------
