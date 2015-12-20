//----------------------------------------------------------------------
// WEBDAV.COPY.java
//
// $Id: COPY.java,v 1.4 2005/04/19 12:19:53 Bob Exp $
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

public class COPY extends WEBDAV
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
    if (iAccl < 4) {
      respStatus(403, HTTP_FORBIDDEN);
    }

    reqURI();
    reqDepth(0, Integer.MAX_VALUE);
    PathInfo piDst = reqDst();
    if (piDst == null) {
      respStatus(409, HTTP_CONFLICT);
      return;
    }

    impersonate(iAccl);
    NUMBER nSrcKey = locatePath(this.sReqPath);
    if (nSrcKey == null) {
      respStatus(404, HTTP_NOT_FOUND);
      return;
    }

    NUMBER nDstDir = locatePath(piDst.merge(1, piDst.iLast));
    if (nDstDir == null) {
      respStatus(409, HTTP_CONFLICT);
      return;
    }

    String sDstName = piDst.getItem(piDst.iLast);
    if (!uniqueName(nDstDir, sDstName, nSrcKey)) {
      respStatus(412, HTTP_PRECONDITION_FAILED);
      return;
    }

    int iRes = copyNode(nSrcKey, nDstDir, sDstName);
    if (iRes < 0) {
      rollback();
      respStatus(412, HTTP_PRECONDITION_FAILED);
    }
    else if (iRes == 0) {
      rollback();
      respStatus(412, HTTP_PRECONDITION_FAILED);
    }
    else {
      commit();
      respStatus(204, HTTP_NO_CONTENT);
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

private int copyNode
( NUMBER nSrcKey
, NUMBER nDstDir
, String sDstName
) throws JOPAException
{
  int iRes = 0;
  String stmt = shell.getCode("copyNodeDAV");
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(stmt);
    cs.registerOutParameter(1, OracleTypes.NUMBER);
    cs.setNUMBER(2, nSrcKey);
    cs.setNUMBER(3, nDstDir);
    cs.setString(4, sDstName);
    cs.executeUpdate();
    NUMBER n = cs.getNUMBER(1);
    iRes = (!cs.wasNull() ? n.intValue() : 0);
    cs.close();
  }
  catch(SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "setName/SQL: ", e.getMessage());
  }
  return iRes;
}

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.4 $";
} // class COPY

//----------------------------------------------------------------------
