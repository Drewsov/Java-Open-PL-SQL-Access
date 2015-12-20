//----------------------------------------------------------------------
// WEBDAV.GET.java
//
// $Id: GET.java,v 1.10 2005/04/19 12:19:53 Bob Exp $
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

public class GET extends WEBDAV
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

    //setCGIEnv();
    reqURI();

    impersonate(iAccl);
    NUMBER nKey  = locatePath(this.sReqPath);
    if (nKey == null) {
      respStatus(404, HTTP_NOT_FOUND);
    }
    else {
      String sDisp = getDisp(nKey, "");
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
  respContentType("text/html;charset="+this.effCharset);

  PrintWriter out = this.response.getWriter();
  out.println("<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">");
  out.println("<HTML><HEAD>");
  out.println(" <TITLE>Index of "+sPath+"</TITLE>");
  out.println("</HEAD><BODY>");
  out.println("<H1>Index of "+sPath+"</H1>");
  out.println("<TABLE width=\"100%\">");
  out.println("<TR>");
  out.println("  <TD width=\"5%\"><B>Disp</B><HR></TD>");
  out.println("  <TD width=\"50%\"><B>Name</B><HR></TD>");
  out.println("  <TD width=\"15%\"><B>Size</B><HR></TD>");
  out.println("  <TD width=\"30%\"><B>Last modified</B><HR></TD>");
  out.println("</TR>");

  String sPrefix = sReqBase; //this.sReqBase;
  String s = getFolderPath(nFolder, null);
  if (s != null) {
    out.println("<TR>");
    out.println("  <TD width=\"5%\"><P>DIR</P></TD>");
    out.println("  <TD width=\"50%\"><A HREF=\""+sPrefix+s+"\"><B> . . </B></A></TD>");
    out.println("  <TD width=\"15%\"><P></P></TD>");
    out.println("  <TD width=\"30%\"><P></P></TD>");
    out.println("</TR>");
  }
  try {
    OracleResultSet rs = enumSubfolders(nFolder, "\'NAME\'");
    // ( 1:ID, 2:ID_FOLDER, 3:PATH, 4:NAME, 5:ACCL,
    //   6:DISP, 7:ID_REF, 8:REF_SIZE, 9:DATA, 10:TIME_STAMP )
    if (rs != null) {
      while (rs.next()) {
        out.println("<TR>");
        out.println("  <TD width=\"5%\"><P>DIR</P></TD>");
        out.println("  <TD width=\"50%\"><A HREF=\""+sPrefix+rs.getString(3)+"\"><B>"+rs.getString(4)+"</B></A></TD>");
        out.println("  <TD width=\"15%\"><P></P></TD>");
        out.println("  <TD width=\"30%\"><P>"+rs.getDATE(10).toText("Dy, DD Mon YYYY HH24:MI:SS", "ENGLISH")+"</P></TD>");
        out.println("</TR>");
      }
      rs.close();
    }

    rs = enumItems(nFolder, "\'NAME, DISP\'");
    // ( 1:ID, 2:ID_FOLDER, 3:PATH, 4:NAME, 5:ACCL,
    //   6:DISP, 7:ID_REF, 8:REF_SIZE, 9:DATA, 10:TIME_STAMP )
    if (rs != null) {
      while (rs.next()) {
        out.println("<TR>");
        out.println("  <TD width=\"5%\"><P>"+rs.getString(6)+"</P></TD>");
        out.println("  <TD width=\"50%\"><A HREF=\""+sPrefix+rs.getString(3)+"\"><B>"+rs.getString(4)+"</B></A></TD>");
        out.println("  <TD width=\"15%\"><P>"+rs.getNUMBER(8).stringValue()+"</P></TD>");
        out.println("  <TD width=\"30%\"><P>"+rs.getDATE(10).toText("Dy, DD Mon YYYY HH24:MI:SS", "ENGLISH")+"</P></TD>");
        out.println("</TR>");
      }
      rs.close();
    }
  }
  catch (SQLException e) {}

  out.println("</TABLE>");
  out.println("</BODY></HTML>");
}

//----------------------------------------------------------------------

private void respPSP (String sPath, NUMBER nKey)
throws ServletException, IOException, JOPAException
{
  NUMBER nRef = getRef(nKey);
  if (nRef == null) 
  {
    respStatus(200, HTTP_OK);
    respWebDAV(2);
    respHeader("ETag", calcETag("PSP", nKey));
    respContentType("application/octet-stream");
    respContentLength(0);
    return;
  }

  BLOB blob = fetchCodeBLOB(nRef);
  if (blob == null) 
  {
    respStatus(500, "Failed to fetch code of " + sPath);
    return;
  }
  BLOB blobProfile = fetchProfileBLOB(nRef);

  respStatus(200, HTTP_OK);
  respWebDAV(2);
  respHeader("ETag", calcETag("PSP", nKey));
  DATE d = getTimeStamp(nKey);
  if (d != null) respHeader("Last-Modified", fmtGMT(d.timestampValue().getTime()));
  respContentType("application/octet-stream");
  respUnitBLOB(blobProfile, blob);

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
    respFatal(500, e.getMessage());
    return;
  }
  if (blob == null || nSize == null || sMimeType == null) {
    respStatus(404, HTTP_NOT_FOUND);
    return;
  }

  respStatus(200, HTTP_OK);
  respWebDAV(3);
  respHeader("ETag", calcETag("DAT", nKey));
  respHeader("Last-Modified", fmtGMT(dLastMod.timestampValue().getTime()));
  respContentType(sMimeType);
  if (getDesiredContentEncoding() == ENC_NOCOMPRESS)
  {
    try 
    { 
      respContentLength((int)blob.length()); 
    }
    catch (SQLException e) {}
  }
  respContent(blob);
}

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.10 $";
} // class GET

//----------------------------------------------------------------------
