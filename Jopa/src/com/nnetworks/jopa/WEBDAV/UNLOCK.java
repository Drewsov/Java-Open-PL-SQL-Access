//----------------------------------------------------------------------
// WEBDAV.UNLOCK.java
//
// $Id: UNLOCK.java,v 1.5 2005/04/19 12:19:53 Bob Exp $
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

public class UNLOCK extends WEBDAV
implements JOPAMethod
{

//----------------------------------------------------------------------

public void service ()
throws ServletException, IOException
{
  String sLockToken = reqLockToken();
  if (sLockToken == null) {
    respStatus(412, HTTP_PRECONDITION_FAILED);
    return;
  }
  try {
    establishConnection();

    // Authentication:
    int iAccl = authorize();
    if (iAccl <= 0) {
      respAuthenticate("Introduce yourself for NTS WebDAV");
      return;
    }
    if (iAccl < 3) {
      respStatus(403, HTTP_FORBIDDEN);
    }

    reqURI();

    //setCGIEnv();
    impersonate(iAccl);
    NUMBER nKey  = locatePath(this.sReqPath);
    if (nKey == null) {
      respStatus(404, HTTP_NOT_FOUND);
    }
    else {
      String sDisp = getDisp(nKey, "NUL");
      NUMBER nRef = getRefEx(nKey, sDisp);
      int n = unlock(sDisp, nRef, sLockToken);
      if (n >= 0)
        respStatus(204, HTTP_NO_CONTENT);
      else
        respStatus(424, HTTP_METHOD_FAILURE);
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

private int unlock
( String sDisp
, NUMBER nRef
, String sLockToken
)
{
  int iRes = -1;
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(
         "begin ? := NN$NTS_LOCK.unlock_(?, ?, ?); end;");
    cs.registerOutParameter(1, OracleTypes.NUMBER);
    cs.setString(2, sDisp);
    cs.setNUMBER(3, nRef);
    cs.setString(4, sLockToken);
    cs.executeUpdate();
    NUMBER nRes = cs.getNUMBER(1);
    if (!cs.wasNull()) iRes = nRes.intValue();
    cs.close();
    if (iRes > 0) m_conn.commit();
  }
  catch(SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "unlock/SQL: ", e.getMessage());
  }
  return iRes;
}

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.5 $";
} // class UNLOCK

//----------------------------------------------------------------------
