//----------------------------------------------------------------------
// WEBDAV.DELETE.java
//
// $Id: DELETE.java,v 1.4 2005/04/19 12:19:53 Bob Exp $
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

public class DELETE extends WEBDAV
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
      return;
    }

    reqURI();
    reqDepth(0, 0);

    impersonate(iAccl);
    NUMBER nKey = locatePath(this.sReqPath);
    if (nKey == null) {
      respStatus(404, HTTP_NOT_FOUND);
    }
    else {
      if (deleteNode(nKey) > 0) {
        respStatus(204, HTTP_NO_CONTENT);
      }
      else {
        respStatus(423, HTTP_LOCKED);
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

private int deleteNode (NUMBER nKey)
throws JOPAException
{
  NUMBER nRes = null;
  String stmt = shell.getCode("deleteNodeDAV");
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(stmt);
    cs.setNUMBER(1, nKey);
    cs.registerOutParameter(2, OracleTypes.NUMBER);
    cs.executeUpdate();
    nRes = cs.getNUMBER(2);
    if (cs.wasNull()) nRes = null;
    cs.close();
    if (nRes != null) return nRes.intValue();
  }
  catch(SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "deleteNode/SQL: ", e.getMessage());
  }
  return -1;
}

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.4 $";
} // class DELETE

//----------------------------------------------------------------------
