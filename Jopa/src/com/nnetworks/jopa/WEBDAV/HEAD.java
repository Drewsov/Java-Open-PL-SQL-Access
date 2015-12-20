//----------------------------------------------------------------------
// WEBDAV.HEAD.java
//
// $Id: HEAD.java,v 1.6 2005/04/19 12:19:53 Bob Exp $
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

public class HEAD extends WEBDAV
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
    if (iAccl < 1) {
      respStatus(403, HTTP_FORBIDDEN);
      return;
    }

    reqURI();
    //setCGIEnv();
    impersonate(iAccl);
    NUMBER nKey  = locatePath(this.sReqPath);
    if (nKey == null) {
      respStatus(404, HTTP_NOT_FOUND);
    }
    else {
      String sDisp = getDisp(nKey, "");
      if (checkModifiedSince(sDisp, nKey))
        return;
           if (sDisp.equals("DIR")) respDIR(this.sReqPath, nKey);
      else if (sDisp.equals("PSP")) respPSP(this.sReqPath, nKey);
      else if (sDisp.equals("DAT")) respDAT(this.sReqPath, nKey);
      else respStatus(404, HTTP_NOT_FOUND);
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

private void respDIR (String sPath, NUMBER nFolder)
throws ServletException, IOException, JOPAException
{
  respStatus(200, HTTP_OK);
  respWebDAV(3);
  respHeader("ETag", calcETag("DIR", nFolder));
  respHeader("Last-Modified", fmtGMT(getJTime(nFolder)));
  respContentType("text/html;charset="+this.effCharset);
}

//----------------------------------------------------------------------

private void respPSP (String sPath, NUMBER nKey)
throws ServletException, IOException, JOPAException
{
  NUMBER nRef = getRef(nKey);
  if (nRef == null) {
    respStatus(204, HTTP_NO_CONTENT);
    return;
  }

  CLOB clob = fetchCode(nRef);
  if (clob == null) {
    respStatus(500, "Failed to fetch code of " + sPath);
    return;
  }

  respStatus(200, HTTP_OK);
  respWebDAV(3);
  respHeader("ETag", calcETag("PSP", nKey));
  respHeader("Last-Modified", fmtGMT(getJTime(nKey)));
  respContentType("text/plain;charset="+this.effCharset);
  try { respContentLength((int)clob.length()); } catch (SQLException e) {}
}

//----------------------------------------------------------------------

private void respDAT (String sPath, NUMBER nKey)
throws ServletException, IOException, JOPAException
{
  NUMBER nRef = getRef(nKey);
  if (nRef == null) {
    respStatus(204, HTTP_NO_CONTENT);
    return;
  }

  BLOB blob = null;
  NUMBER nSize = null;
  String sMimeType = null;
  DATE dLastMod = null;
  String stmt = shell.getCode("getPageDataDAV",
    "{T}", this.props.getProperty("document_table", "NN$T_DOWNLOAD"));
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(stmt);
    cs.registerOutParameter(1, OracleTypes.BLOB);
    cs.registerOutParameter(2, OracleTypes.NUMBER);
    cs.registerOutParameter(3, OracleTypes.VARCHAR);
    cs.registerOutParameter(4, OracleTypes.DATE);
    cs.setNUMBER(5, nRef);
    cs.execute();
    blob = cs.getBLOB(1);
    if (cs.wasNull()) blob = null;
    nSize = cs.getNUMBER(2);
    if (cs.wasNull()) nSize = null;
    sMimeType = cs.getString(3);
    if (cs.wasNull()) sMimeType = null;
    dLastMod  = cs.getDATE(4);
    if (cs.wasNull()) dLastMod = null;
    cs.close();
  }
  catch(SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    respFatal(500, e);
    return;
  }
  if (blob == null || nSize == null || sMimeType == null) {
    respStatus(404, HTTP_NOT_FOUND);
    return;
  }

  respStatus(200, HTTP_OK);
  respHeader("ETag", calcETag("DAT", nKey));
  respHeader("Last-Modified", fmtGMT(dLastMod.timestampValue().getTime()));
  respContentType(sMimeType);
  try { respContentLength((int)blob.length()); } catch (SQLException e) {}
}

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.6 $";
} // class HEAD

//----------------------------------------------------------------------
