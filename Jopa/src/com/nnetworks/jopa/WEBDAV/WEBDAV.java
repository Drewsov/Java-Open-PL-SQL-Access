//----------------------------------------------------------------------
// JOPA.WEBDAV.java
//
// $Id: WEBDAV.java,v 1.18 2005/04/19 12:19:53 Bob Exp $
//
//----------------------------------------------------------------------

package com.nnetworks.jopa.WEBDAV;

import com.nnetworks.jopa.WEBDAV.*;
import com.nnetworks.jopa.*;

import oracle.sql.*;
import oracle.jdbc.driver.*;

import org.jdom.*;
import org.jdom.input.*;
import org.jdom.output.*;

import javax.servlet.*;
import javax.servlet.http.*;

import java.sql.*;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.zip.*;

//----------------------------------------------------------------------

class WEBDAV extends Processor
{

//----------------------------------------------------------------------

public static final String LOCK_TOKEN_SCHEMA = "opaquelocktoken:";

//----------------------------------------------------------------------

protected String    sReqBase;
protected String    sReqPath;
protected String    sReqName;
protected int       iTop;
protected int       iDepth;

protected Document  xreqDoc;
protected Element   xreqElem;
protected List      xreqList;

protected Namespace xrespNS;
protected Namespace xrespNSb;
protected Document  xrespDoc;
protected Element   xrespElem;
protected int       iPref;

//----------------------------------------------------------------------

protected void afterInit ()
{
  super.afterInit();
  this.iTop   = 0;
  this.iDepth = 0;
  this.iPref  = 0;
}

//----------------------------------------------------------------------

protected void initSession ()
{
  super.initSession();

  for (int i = 1; i < this.pi.iCount; i++)
    this.pi.asPathInfo[i] = unescape(this.pi.asPathInfo[i], this.effCharset);

}

//----------------------------------------------------------------------
// Request support:
//----------------------------------------------------------------------

protected void reqURI ()
{
  StringBuffer sb = new StringBuffer(256);
  sb.append("http://");
  sb.append(this.request.getHeader("Host"));
//   int i = this.request.getRequestURI().indexOf(this.request.getServletPath());
   int i = this.request.getRequestURI().indexOf(Processor.getServletPath);
  if (i > 0)
    sb.append(this.request.getRequestURI().substring(0,i+getServletPath.length()));
  else
    sb.append(getServletPath);
  sb.append('/');
  sb.append(this.pi.getItem(0));
  this.sReqBase = sb.toString();
  this.sReqPath = this.pi.merge(1, -1);
  this.sReqName = this.pi.getItem(this.pi.iLast);
    if (sReqName.equalsIgnoreCase(".")){
        sReqName = this.pi.getItem(this.pi.iLast-1);
        sReqPath = "";
    shell.log(3,"Clear Request Name: "+sReqName);
    }
  shell.log(3,"Request Base: "+sReqBase);
  shell.log(3,"Request Path: "+sReqPath);
  shell.log(3,"Request Name: "+sReqName);
  
}

//----------------------------------------------------------------------

protected PathInfo reqDst ()
{
  String sDst = this.request.getHeader("Destination");
  if (sDst == null) return null;
  PathInfo dpi = new PathInfo(sDst, this.request.getServletPath());
  for (int i = 1; i < dpi.iCount; i++)
    dpi.asPathInfo[i] = unescape(dpi.asPathInfo[i], this.effCharset);

  return dpi;
}

//----------------------------------------------------------------------

protected void reqDepth (int iTopDef, int iDepthDef)
{
  String sDepth = this.request.getHeader("Depth");
  if (sDepth == null)
    { this.iTop = iTopDef;  this.iDepth = iDepthDef; }
  else if (sDepth.equals("0"))
    { this.iTop = 0;  this.iDepth = 0; }
  else if (sDepth.equals("1"))
    { this.iTop = 0;  this.iDepth = 1; }
  else if (sDepth.equalsIgnoreCase("1,noroot"))
    { this.iTop = 1;  this.iDepth = 1; }
  else if (sDepth.equalsIgnoreCase("infinity"))
    { this.iTop = 0;  this.iDepth = Integer.MAX_VALUE; }
  else
    { this.iTop = iTopDef;  this.iDepth = iDepthDef; }
}

//----------------------------------------------------------------------

protected String reqLockToken ()
{
  String sLockToken = this.request.getHeader("Lock-Token");
  if (sLockToken == null) return null;
  int n = sLockToken.length();
  if (n <= 0) return null;
  int i = n-1;
  if (sLockToken.startsWith("<") &&  sLockToken.endsWith(">"))
    sLockToken = sLockToken.substring(1, i);
  i = sLockToken.indexOf(':');
  if (i >= 0)
    sLockToken = sLockToken.substring(i+1);
  return sLockToken;
}

//----------------------------------------------------------------------

protected NTSNode reqNode (String sPath)
throws ServletException, IOException, JOPAException
{
  NUMBER nKey = locatePath(sPath);
  if (nKey != null) {
    NTSNode rNode = new NTSNode();
    if (rNode.materialize(this.m_conn, nKey)) return rNode;
    rNode = null;
  }
  return null;
}

//----------------------------------------------------------------------

protected boolean reqXMLContent ()
throws IOException
{
  SAXBuilder builder = new SAXBuilder();
  builder.setValidation(false);
  builder.setIgnoringElementContentWhitespace(true);
  try {
    this.xreqDoc = builder.build(this.request.getInputStream());
    this.xreqElem = this.xreqDoc.getRootElement();
    /* */  spyXMLContent("=> (XML):", this.xreqDoc, "ISO-8859-5");
  }
  catch (JDOMException e) {
    shell.log(0, "reqXMLContent/XML: ", e.getMessage());
    return false;
  }
  return true;
}

//----------------------------------------------------------------------
// Guess:
//----------------------------------------------------------------------

protected String guessDisp (String sName)
{
  int i = sName.lastIndexOf('.');
  if (i < 0) return "PSP";
  String sExt = sName.substring(i).toLowerCase();
  if (sExt.equals(".dpsp")) return "PSP";
  if (sExt.equals(".psp"))  return "PSP";
  /*
  if (sExt.equals(".html")) return "PSP";
  if (sExt.equals(".htm"))  return "PSP";
  if (sExt.equals(".wml"))  return "PSP";
  if (sExt.equals(".css"))  return "PSP";
  if (sExt.equals(".js"))   return "PSP";
  */
  return "DAT";
}

//----------------------------------------------------------------------

protected String guessDataType (String sName)
{
  int i = sName.lastIndexOf('.');
  if (i >= 0) {
    String sExt = sName.substring(i).toLowerCase();
    if (sExt.equals(".gif"))  return "image/gif";
    if (sExt.equals(".jpg"))  return "image/pjpeg";
    if (sExt.equals(".bmp"))  return "image/bmp";
    if (sExt.equals(".ico"))  return "image/x-icon";
    if (sExt.equals(".zip"))  return "application/x-zip-compressed";
    if (sExt.equals(".jar"))  return "application/x-zip-compressed";
    if (sExt.equals(".rar"))  return "application/x-rar-compressed";
    if (sExt.equals(".html")) return "text/html";
    if (sExt.equals(".htm"))  return "text/html";
    if (sExt.equals(".wml"))  return "text/vnd.wap.wml";
    if (sExt.equals(".css"))  return "text/css";
    if (sExt.equals(".js"))   return "text/javascript";
    if (sExt.equals(".txt"))  return "text/plain";
    if (sExt.equals(".data")) return "text/plain";
  }
  return "application/octet-stream";
}

//----------------------------------------------------------------------

protected String guessCodeType (String sName)
{
  int i = sName.lastIndexOf('.');
  if (i < 0) return "PSP";
  String sExt = sName.substring(i).toLowerCase();
  if (sExt.equals(".dpsp")) return "PSP";
  if (sExt.equals(".psp"))  return "PSP";
  if (sExt.equals(".css"))  return "CSS";
  if (sExt.equals(".js"))   return "JAVASCRIPT";
  if (sExt.equals(".wml"))  return "WML";
  return "HTML";
}

//----------------------------------------------------------------------

protected String guessCodeProc (String sName)
{
  int i = sName.lastIndexOf('.');
  if (i < 0) return "DPSP";
  String sExt = sName.substring(i).toLowerCase();
  if (sExt.equals(".psp"))  return "PPSP";
  if (sExt.equals(".css"))  return "FLAT";
  if (sExt.equals(".js"))   return "FLAT";
  if (sExt.equals(".wml"))  return "PPSP";
  if (sExt.equals(".htm"))  return "FLAT";
  if (sExt.equals(".html")) return "FLAT";
  return "DPSP";
}

//----------------------------------------------------------------------
// Responses:
//----------------------------------------------------------------------

protected void respWebDAV (int i)
{
  if (i > 0) respHeader("Server", shell.getVersion());
  if (i > 1) respHeader("MS-Author-Via", "DAV");
  if (i > 2) respHeader("Allow",
    "GET, HEAD, OPTIONS, DELETE, PUT, COPY, MOVE, MKCOL, PROPFIND, LOCK, UNLOCK");
  if (i > 3) respHeader("DAV", "1,2");
}

//----------------------------------------------------------------------

protected void respXMLContent (String enc)
{
  respContentType("text/xml; charset=\"" + enc + '\"');
  try {
    OutputStream out;
    switch (getDesiredContentEncoding())
    {
      case ENC_GZIP:
         respHeader("Content-Encoding","gzip");
         out = new GZIPOutputStream(this.response.getOutputStream());
         shell.log(3, "Applying GZIP content encoding to the output...");
         break;
      case ENC_DEFLATE:
         respHeader("Content-Encoding","deflate");
         out = new DeflaterOutputStream(this.response.getOutputStream());
         shell.log(3, "Applying Deflate content encoding to the output...");
         break;
      default:
         out = this.response.getOutputStream();
    }
    org.jdom.output.Format frm = org.jdom.output.Format.getPrettyFormat().setEncoding(enc);
    XMLOutputter xmlout = new XMLOutputter(frm);
    xmlout.output(this.xrespDoc, out);
    out.close();
    /* */ spyXMLContent("<= (XML):", this.xrespDoc, enc);
  }
  catch (IOException e) {
    shell.log(0, "respXMLContent/IO: ", e.getMessage());
  }
}

//----------------------------------------------------------------------

protected void respDoc (String sTag0)
{
  this.xrespNS  = Namespace.getNamespace("DAV:");
  this.xrespNSb = Namespace.getNamespace("b", "urn:uuid:c2f41010-65b3-11d1-a29f-00aa00c14882");

  this.xrespElem = new Element(sTag0, this.xrespNS);
  this.xrespDoc = new Document(this.xrespElem);
  this.xrespElem.addNamespaceDeclaration(this.xrespNSb);
}

//----------------------------------------------------------------------

protected Element appElem
( Element   parent
, String    sName
, Namespace ns
, String    sText
)
{
  if (ns == null) {
    ns = this.xrespNS;
  }
  Element elem = new Element(sName, ns);
  parent.addContent(elem);
  if (sText != null) {
    elem.setText(sText);
  }
  return elem;
}

//----------------------------------------------------------------------

protected void appAttr
( Element   elem
, String    sName
, Namespace ns
, String    sText
)
{
  elem.setAttribute(sName, sText, ns);
}

//----------------------------------------------------------------------

protected Namespace borrowNS
( Element elem
)
{
  return elem == null ? null :
         elem.getNamespaceURI().equals("DAV:") ? null :
         elem.getNamespace();
}

//----------------------------------------------------------------------
/*
<D:lockdiscovery>
   <D:activelock>
      <D:locktype><D:write/></D:locktype>
      <D:lockscope><D:exclusive/></D:lockscope>
      <D:depth>Infinity</D:depth>
      <D:owner>
         <D:href>http://www.ics.uci.edu/~ejw/contact.html</D:href>
      </D:owner>
      <D:timeout>Second-604800</D:timeout>
      <D:locktoken>
         <D:href>opaquelocktoken:e71d4fae-5dec-22d6-fea5-00a0c91e6be4</D:href>
      </D:locktoken>
   </D:activelock>
</D:lockdiscovery>
*/

protected void respLockDiscovery
( Element   parent
, String    sDisp
, NUMBER    nRef
, Namespace ns
)
{
  String s, st;
  NUMBER n;
  Element lockdiscovery, activelock, elem3;
  lockdiscovery = appElem(parent, "lockdiscovery", ns, null);
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(
        "begin NN$NTS_LOCK.reveal_(?, ?, ?); end;");
    cs.setString(1, sDisp);
    cs.setNUMBER(2, nRef);
    cs.registerOutParameter(3, OracleTypes.CURSOR);
    cs.execute();
    OracleResultSet rs = (OracleResultSet) cs.getCursor(3);
    if (cs.wasNull()) rs = null;
    cs.close();
    if (rs != null) {
      while (rs.next()) {
        activelock = appElem(lockdiscovery, "activelock", ns, null);

        st = rs.getString("LOCK_TOKEN");  if (rs.wasNull()) s = null;
        if (st != null) {
          s = rs.getString("LOCK_TYPE");  if (rs.wasNull()) s = "W";
          s = (s.equals("W") ? "write" : "write");
          elem3 = appElem(activelock, "locktype", ns, null);
                  appElem(elem3, s, ns, null);

          s = rs.getString("LOCK_SCOPE");  if (rs.wasNull()) s = "E";
          s = (s.equals("S") ? "shared" : "exclusive");
          elem3 = appElem(activelock, "lockscope", ns, null);
                  appElem(elem3, s, ns, null);

          s = rs.getString("OWNER");       if (rs.wasNull()) s = "SYSTEM";
          if (s.startsWith("href::"))
          {
             elem3 = appElem(activelock, "owner", ns, null);
                     appElem(elem3, "href",  ns, s.substring(6));
          }
          else
             appElem(activelock, "owner", ns, s);

          elem3 = appElem(activelock, "locktoken", ns, null);
                  appElem(elem3, "href", ns, LOCK_TOKEN_SCHEMA + st);

          s = rs.getString("LOCK_DEPTH");  if (rs.wasNull()) s = "I";
          s = (s.equals("I") ? "Infinity" : s);
          elem3 = appElem(activelock, "depth", ns, s);

          n = rs.getNUMBER("TIME_OUT");    if (rs.wasNull()) n = null;
          s = (n == null ? "Infinity" : "Second-" + n.stringValue());
          elem3 = appElem(activelock, "timeout", ns, s);

        }
      }
      rs.close();
    }
  }
  catch(SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "respLockDiscovery/SQL: ", e.getMessage());
  }
}

//----------------------------------------------------------------------

protected void respUnit (String sProfile, CLOB clob)
{
  if (clob == null) return;
  int n = BUFFER_SIZE;
  try 
  {
    PrintStream sos;
    switch (getDesiredContentEncoding())
    {
      case ENC_GZIP:
         respHeader("Content-Encoding","gzip");
         sos = new PrintStream(new GZIPOutputStream(this.response.getOutputStream()));
         shell.log(3, "Applying GZIP content encoding to the output...");
         break;
      case ENC_DEFLATE:
         respHeader("Content-Encoding","deflate");
         sos = new PrintStream(new DeflaterOutputStream(this.response.getOutputStream()));
         shell.log(3, "Applying Deflate content encoding to the output...");
         break;
      default:
         respContentLength(
            (sProfile != null ? (sProfile.length() + 19) : 0) +
            (int)clob.length()
           );
         sos = new PrintStream(this.response.getOutputStream());
    }

    if (sProfile != null) {
      sos.print("<%@attributes\r\n");
      sos.print(sProfile);
      sos.print("%>\r\n");
    }
    InputStream is = clob.getAsciiStream();
    byte[] buf = new byte[BUFFER_SIZE];
    while (n == BUFFER_SIZE) {
      n = is.read(buf, 0, BUFFER_SIZE);
      if (n > 0) sos.write(buf, 0, n);
    }
    is.close();
    sos.close();
  }
  catch (SQLException e) {
    shell.log(0, "respUnit/SQL: ", e.getMessage());
  }
  catch (IOException e) {
    shell.log(0, "respUnit/IO: ", e.getMessage());
  }
  finally {
    freeTemporary(clob);
  }
}

//----------------------------------------------------------------------

protected void respUnit (CHAR chProfile, CLOB clob)
{
  if (clob == null) return;
  int n;
  try 
  {

    PrintStream sos;
    switch (getDesiredContentEncoding())
    {
      case ENC_GZIP:
         respHeader("Content-Encoding","gzip");
         sos = new PrintStream(new GZIPOutputStream(this.response.getOutputStream()));
         shell.log(3, "Applying GZIP content encoding to the output...");
         break;
      case ENC_DEFLATE:
         respHeader("Content-Encoding","deflate");
         sos = new PrintStream(new DeflaterOutputStream(this.response.getOutputStream()));
         shell.log(3, "Applying Deflate content encoding to the output...");
         break;
      default:
         sos = new PrintStream(this.response.getOutputStream());
    }

    byte[] buf = new byte[BUFFER_SIZE];
    InputStream is;
    if (chProfile != null) {
      sos.print("<%@attributes\r\n");
      is = chProfile.binaryStreamValue();
      n = BUFFER_SIZE;
      while (n == BUFFER_SIZE) {
        n = is.read(buf, 0, BUFFER_SIZE);
        if (n > 0) sos.write(buf, 0, n);
      }
      is.close();
      sos.print("%>\r\n");
    }
    is = clob.getAsciiStream();
    n = BUFFER_SIZE;
    while (n == BUFFER_SIZE) {
      n = is.read(buf, 0, BUFFER_SIZE);
      if (n > 0) sos.write(buf, 0, n);
    }
    is.close();
    sos.close();
  }
  catch (SQLException e) {
    shell.log(0, "respUnit/SQL: ", e.getMessage());
  }
  catch (IOException e) {
    shell.log(0, "respUnit/IO: ", e.getMessage());
  }
  finally {
    freeTemporary(clob);
  }
}

//----------------------------------------------------------------------

protected void respUnitBLOB (BLOB blobProfile, BLOB blob)
{
  if (blob == null) return;
  int n;
  try {
    PrintStream sos;
    switch (getDesiredContentEncoding())
    {
      case ENC_GZIP:
         respHeader("Content-Encoding","gzip");
         sos = new PrintStream(new GZIPOutputStream(this.response.getOutputStream()));
         shell.log(3, "Applying GZIP content encoding to the output...");
         break;
      case ENC_DEFLATE:
         respHeader("Content-Encoding","deflate");
         sos = new PrintStream(new DeflaterOutputStream(this.response.getOutputStream()));
         shell.log(3, "Applying Deflate content encoding to the output...");
         break;
      default:
         respContentLength(
           (blobProfile != null ? ((int)blobProfile.length() + 19) : 0) +
           (int)blob.length()
          );
         sos = new PrintStream(this.response.getOutputStream());
    }
    byte[] buf = new byte[BUFFER_SIZE];
    InputStream is;
    if (blobProfile != null) {
      sos.print("<%@attributes\r\n");
      is = blobProfile.getBinaryStream();
      n = BUFFER_SIZE;
      while (n == BUFFER_SIZE) {
        n = is.read(buf, 0, BUFFER_SIZE);
        if (n > 0) sos.write(buf, 0, n);
      }
      is.close();
      sos.print("%>\r\n");
    }
    is = blob.getBinaryStream();
    n = BUFFER_SIZE;
    while (n == BUFFER_SIZE) {
      n = is.read(buf, 0, BUFFER_SIZE);
      if (n > 0) sos.write(buf, 0, n);
    }
    is.close();
    sos.close();
  }
  catch (SQLException e) {
    shell.log(0, "respUnitBLOB/SQL: ", e.getMessage());
  }
  catch (IOException e) {
    shell.log(0, "respUnitBLOB/IO: ", e.getMessage());
  }
  finally {
    freeTemporary(blob);
  }
}

//----------------------------------------------------------------------

protected void respUnit (CHAR chProfile, BLOB blob)
{
  if (blob == null) return;
  int n;
  try {
    PrintStream sos;
    switch (getDesiredContentEncoding())
    {
      case ENC_GZIP:
         respHeader("Content-Encoding","gzip");
         sos = new PrintStream(new GZIPOutputStream(this.response.getOutputStream()));
         shell.log(3, "Applying GZIP content encoding to the output...");
         break;
      case ENC_DEFLATE:
         respHeader("Content-Encoding","deflate");
         sos = new PrintStream(new DeflaterOutputStream(this.response.getOutputStream()));
         shell.log(3, "Applying Deflate content encoding to the output...");
         break;
      default:
         respContentLength(
           (chProfile != null ? (chProfile.stringValue().length() + 19) : 0) +
           (int)blob.length()
          );
         sos = new PrintStream(this.response.getOutputStream());
    }

    byte[] buf = new byte[BUFFER_SIZE];
    InputStream is;
    if (chProfile != null) {
      sos.print("<%@attributes\r\n");
      sos.print(chProfile.stringValue());
      sos.print("%>\r\n");
    }
    is = blob.getBinaryStream();
    n = BUFFER_SIZE;
    while (n == BUFFER_SIZE) {
      n = is.read(buf, 0, BUFFER_SIZE);
      if (n > 0) sos.write(buf, 0, n);
    }
    is.close();
    sos.close();

  }
  catch (SQLException e) {
    shell.log(0, "respUnit/SQL: ", e.getMessage());
  }
  catch (IOException e) {
    shell.log(0, "respUnit/IO: ", e.getMessage());
  }
  finally {
    freeTemporary(blob);
  }
}

//----------------------------------------------------------------------

protected void respUnit (String sProfile, BLOB blob)
{
  if (blob == null) return;
  int n   = BUFFER_SIZE;
  try 
  {
    PrintStream sos;
    switch (getDesiredContentEncoding())
    {
      case ENC_GZIP:
         respHeader("Content-Encoding","gzip");
         sos = new PrintStream(new GZIPOutputStream(this.response.getOutputStream()));
         shell.log(3, "Applying GZIP content encoding to the output...");
         break;
      case ENC_DEFLATE:
         respHeader("Content-Encoding","deflate");
         sos = new PrintStream(new DeflaterOutputStream(this.response.getOutputStream()));
         shell.log(3, "Applying Deflate content encoding to the output...");
         break;
      default:
         respContentLength(
           (sProfile != null ? (sProfile.length() + 19) : 0) +
           (int)blob.length()
         );
         sos = new PrintStream(this.response.getOutputStream());
    }

    if (sProfile != null) {
      sos.print("<%@attributes\r\n");
      sos.print(sProfile);
      sos.print("%>\r\n");
    }
    InputStream is = blob.getBinaryStream();
    byte[] buf = new byte[BUFFER_SIZE];
    while (n == BUFFER_SIZE) {
      n = is.read(buf, 0, BUFFER_SIZE);
      if (n > 0) sos.write(buf, 0, n);
    }
    is.close();
    sos.close();
  }
  catch (SQLException e) {
    shell.log(0, "respUnit/SQL: ", e.getMessage());
  }
  catch (IOException e) {
    shell.log(0, "respUnit/IO: ", e.getMessage());
  }
  finally {
    freeTemporary(blob);
  }
}

//----------------------------------------------------------------------
// Support:
//----------------------------------------------------------------------

protected String calcContentType (String sDisp)
{
  String sType;
       if (sDisp.equals("PSP")) sType = "text/plain";
  else if (sDisp.equals("DAT")) sType = "application/octet-stream";
  else sType = "text/plain";
  return sType;
}

//----------------------------------------------------------------------

static Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"), Locale.UK);

protected String calcETag (String sDisp, NUMBER nKey)
{
  return '\"' + sDisp + nKey.stringValue() +"-"+getJTime(nKey)+ '\"';
}

//----------------------------------------------------------------------
// CGI Environment support:
//----------------------------------------------------------------------

protected void prepareEnv()
{
  super.prepareEnv();
  appEnv("PATH_INFO",  this.pi.merge(1, -1));
}

//----------------------------------------------------------------------
// Authentication support:
//----------------------------------------------------------------------

protected int authorize ()
{
  int iAccl = -1;

  // Obtain authorization header:
  String sAuth = this.request.getHeader("Authorization");
  if (sAuth == null) sAuth = this.request.getHeader("HTTP_AUTHORIZATION");
  if (sAuth == null) sAuth = this.request.getHeader("AUTHORIZATION");
  if (sAuth == null) sAuth = this.request.getHeader("authorization");
  if (sAuth == null) sAuth = this.request.getHeader("http_authorization");
  if (sAuth == null) sAuth = this.request.getHeader("Http_Authorization");
  if (sAuth == null) return iAccl;

  // Parse the authorization data:
  int i = sAuth.indexOf("Basic");
  if (i < 0) return iAccl;

  String sCode = Base64.decodeToString(sAuth.substring(i+6), this.requestCharset);
  if (sCode == null) return iAccl;

  i = sCode.indexOf(":");
  if (i <= 0) return iAccl;

  String sUsername = sCode.substring(0, i).trim();
  String sPassword = sCode.substring(i+1).trim();

  // Check username/password;
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(
        shell.getCode("authenticateDAV"));
    cs.registerOutParameter(3, OracleTypes.INTEGER);
    cs.setString(1, sUsername);
    cs.setString(2, sPassword);
    cs.execute();
    i = cs.getInt(3);
    if (!cs.wasNull()) iAccl = i;
    cs.close();
  }
  catch (JOPAException je) {
    shell.log(0, "authorize/SQL: ", je.getMessage());
  }
  catch (SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "authorize/SQL: ", e.getMessage());
  }

  return iAccl;
}

//----------------------------------------------------------------------

protected void respAuthenticate (String sMsg)
{
  respStatus(401, sMsg);
  respHeader("WWW-Authenticate",
             "Basic realm=\"NTS by WebDAV\"");
}

//----------------------------------------------------------------------
// SQL support:
//----------------------------------------------------------------------

protected void impersonate (int iAccl)
{
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(
         "declare n number; begin n := NN$NTS.impersonate(?); end;");
    cs.setInt(1, iAccl);
    cs.execute();
    cs.close();
  }
  catch(SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "impersonate/SQL:", e.getMessage());
  }
}

//----------------------------------------------------------------------

protected NUMBER locatePath (String sPath)
{
  NUMBER nKey = null;
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(
         "begin ? := NN$NTS.locatePath(?); end;");
    cs.registerOutParameter(1, OracleTypes.NUMBER);
    cs.setCHAR(2, encode(sPath));
//    cs.setString(2, sPath);
    cs.execute();
    nKey = cs.getNUMBER(1);
    if (cs.wasNull()) nKey = null;
    cs.close();
  }
  catch(SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "locatePath/SQL:", e.getMessage());
  }
  return nKey;
}

//----------------------------------------------------------------------

protected boolean uniqueName (NUMBER nDir, String sName, NUMBER nKey)
{
  boolean b = true;
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(
         "declare n number := 0;" +
         " begin if NN$NTS.uniqueName(?, ?, ?) then n := 1; end if; ? := n; end;");
    cs.setNUMBER(1, nDir);
    cs.setCHAR(2, encode(sName));
//    cs.setString(2, sName);
    cs.setNUMBER(3, nKey);
    cs.registerOutParameter(4, OracleTypes.NUMBER);
    cs.execute();
    NUMBER n = cs.getNUMBER(4);
    if (!cs.wasNull()) b = (n.intValue() > 0);
    cs.close();
  }
  catch(SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "uniqueName/SQL: ", e.getMessage());
  }
  return b;
}

//----------------------------------------------------------------------

protected NUMBER ensureNode
( NUMBER nDir
, String sName
, String sDisp
) throws JOPAException
{
  NUMBER nKey = null;
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(
        "begin ? := NN$NTS.ensureNode(?, ?, ?); commit; end;");
    cs.registerOutParameter(1, OracleTypes.NUMBER);
    cs.setNUMBER(2, nDir);
    cs.setCHAR(3, encode(sName));
//    cs.setString(3, sName);
    cs.setString(4, sDisp);
    cs.executeUpdate();
    nKey = cs.getNUMBER(1);  if (cs.wasNull()) nKey = null;
    cs.close();
  }
  catch (SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "ensureNode/SQL: ", e.getMessage());
  }
  return nKey;
}

//----------------------------------------------------------------------

protected String getDisp (NUMBER nKey, String sDef)
{
  String sDisp = sDef;
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(
         "begin ? := NN$NTS.getDisp(?); end;");
    cs.registerOutParameter(1, OracleTypes.VARCHAR);
    cs.setNUMBER(2, nKey);
    cs.execute();
    sDisp = cs.getString(1);
    if (cs.wasNull()) sDisp = sDef;
    cs.close();
  }
  catch(SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "getDisp/SQL: ", e.getMessage());
  }
  return sDisp;
}

//----------------------------------------------------------------------

protected boolean setDisp (NUMBER nKey, String sDisp)
{
  boolean b = false;
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(
         "begin ? := NN$NTS.setDisp(?, ?); end;");
    cs.registerOutParameter(1, OracleTypes.NUMBER);
    cs.setNUMBER(2, nKey);
    cs.setString(3, sDisp);
    cs.executeUpdate();
    NUMBER n = cs.getNUMBER(1);
    if (cs.wasNull()) n = null;
    if (n != null) b = (n.intValue() > 0);
    cs.close();
  }
  catch(SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "setDisp/SQL: ", e.getMessage());
  }
  return b;
}

//----------------------------------------------------------------------

protected DATE getTimeStamp (NUMBER nKey)
{
  DATE d = null;
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(
         "begin ? := NN$NTS.getTimeStamp(?); end;");
    cs.registerOutParameter(1, OracleTypes.DATE);
    cs.setNUMBER(2, nKey);
    cs.execute();
    d = cs.getDATE(1);
    if (cs.wasNull()) d = null;
    cs.close();
  }
  catch(SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "getTimeStamp/SQL: ", e.getMessage());
  }
  return d;
}

protected long getJTime(NUMBER nKey)
{
  return getTimeStamp(nKey).timestampValue().getTime();
}

//----------------------------------------------------------------------

protected NUMBER getRefEx (NUMBER nKey, String sDisp)
{
  NUMBER nRef = null;
       if (sDisp.equals("DIR")) nRef = nKey;
  else if (sDisp.equals("NUL")) nRef = nKey;
  else nRef = getRef(nKey);
  return (nRef == null ? nKey : nRef);
}

//----------------------------------------------------------------------

protected NUMBER getRef (NUMBER nKey)
{
  NUMBER nRef = null;
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(
         "begin ? := NN$NTS.getRef(?); end;");
    cs.registerOutParameter(1, OracleTypes.NUMBER);
    cs.setNUMBER(2, nKey);
    cs.execute();
    nRef = cs.getNUMBER(1);
    if (cs.wasNull()) nRef = null;
    cs.close();
  }
  catch(SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "getRef/SQL: ", e.getMessage());
  }
  return nRef;
}

//----------------------------------------------------------------------

protected String getFolderName (NUMBER nKey, String sDef)
{
  String sName = sDef;
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(
        "begin ? := NN$NTS.getName(NN$NTS.getFolder(?)); end;");
    cs.registerOutParameter(1, OracleTypes.VARCHAR);
    cs.setNUMBER(2, nKey);
    cs.execute();
    sName = cs.getString(1);
    if (cs.wasNull()) sName = sDef;
    cs.close();
  }
  catch(SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "getFolderName/SQL: ", e.getMessage());
  }
  return sName;
}

//----------------------------------------------------------------------

protected String getFolderPath (NUMBER nKey, String sDef)
{
  String sPath = sDef;
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(
        "begin ? := NN$NTS.getPath(NN$NTS.getFolder(?)); end;");
    cs.registerOutParameter(1, OracleTypes.VARCHAR);
    cs.setNUMBER(2, nKey);
    cs.execute();
    sPath = cs.getString(1);
    if (cs.wasNull()) sPath = sDef;
    cs.close();
  }
  catch(SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "getFolderPath/SQL: ", e.getMessage());
  }
  return sPath;
}

//----------------------------------------------------------------------

protected OracleResultSet enumSubfolders (NUMBER nFolder, String sSort)
{
  OracleResultSet rs = null;
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(
        "begin NN$NTS.enumSubfolders(?, ?, " + sSort + "); end;");
    cs.registerOutParameter(1, OracleTypes.CURSOR);
    cs.setNUMBER(2, nFolder);
    cs.execute();
    rs = (OracleResultSet) cs.getCursor(1);
    cs.close();
  }
  catch(SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    String errstr = "";
    int errcode = e.getErrorCode();
    if(errcode == 17412) {
    errstr =  "This is a bug in the jdbc-thin driver that was solved in 10.1.0.4\n"+
                  "(search metalink for ORA-17412)";
    }
    shell.log(0, "enumSubfolders/SQL: ", "ORA-"+e.getErrorCode()+":"+e.getMessage()+"\n"+errstr);
  }
  return rs;
}

//----------------------------------------------------------------------

protected OracleResultSet enumItems (NUMBER nFolder, String sSort)
{
  OracleResultSet rs = null;
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(
        "begin NN$NTS.enumItems(?, ?, null, " + sSort + "); end;");
    cs.registerOutParameter(1, OracleTypes.CURSOR);
    cs.setNUMBER(2, nFolder);
    cs.execute();
    rs = (OracleResultSet) cs.getCursor(1);
    cs.close();
  }
  catch (SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "enumItems/SQL: ", e.getErrorCode()+":"+e.getMessage());
  }
  return rs;
}

//----------------------------------------------------------------------

protected CLOB fetchCode (NUMBER nRef)
{
  long nSize = -1;
  CLOB clob = null;
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(
        "begin NN$PSP_UNIT.fetchCode(?, ?, ?); end;");
    cs.registerOutParameter(2, OracleTypes.CLOB);
    cs.registerOutParameter(3, OracleTypes.INTEGER);
    cs.setNUMBER(1, nRef);
    cs.execute();
    nSize = cs.getLong(3);
    if (cs.wasNull()) nSize = -1;
    if (nSize >= 0) {
      clob = cs.getCLOB(2);
      if (cs.wasNull()) { clob = null;  nSize = -1; }
    }
    cs.close();
  }
  catch(SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "fetchCode/SQL: ", e.getMessage());
  }
  return clob;
}

//----------------------------------------------------------------------

protected BLOB fetchCodeBLOB (NUMBER nRef)
throws JOPAException
{
  long nSize = -1;
  BLOB blob = null;
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(
        "begin NN$PSP_UNIT.fetchCodeBlob(?, ?, ?); end;");
    cs.setNUMBER(1, nRef);
    cs.registerOutParameter(2, OracleTypes.BLOB);
    cs.registerOutParameter(3, OracleTypes.INTEGER);
    cs.execute();
    nSize = cs.getLong(3);
    if (cs.wasNull()) nSize = -1;
    if (nSize >= 0) {
      blob = cs.getBLOB(2);
      if (cs.wasNull()) { blob = null;  nSize = -1; }
    }
    cs.close();
  }
  catch(SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "fetchCodeBLOB/SQL: ", e.getMessage());
  }
  return blob;
}

//----------------------------------------------------------------------

protected BLOB fetchProfileBLOB (NUMBER nRef)
{
  BLOB blobProfile = null;
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(
        "declare b blob; s varchar2(32767); begin" +
        " NN$PSP_UNIT.fetchProfile(?, s, 'I');" +
        " sys.dbms_lob.createTemporary(b, false, dbms_lob.CALL);" +
        " sys.dbms_lob.writeAppend(b, length(s), utl_raw.cast_to_raw(s));" +
        " ? := b; end;");
    cs.setNUMBER(1, nRef);
    cs.registerOutParameter(2, OracleTypes.BLOB);
    cs.execute();
    blobProfile = cs.getBLOB(2);
    if (cs.wasNull()) blobProfile = null;
    cs.close();
  }
  catch(SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "fetchProfileCHAR/SQL: ", e.getMessage());
  }
  return blobProfile;
}

//----------------------------------------------------------------------

protected CHAR fetchProfileCHAR (NUMBER nRef)
{
  CHAR chProfile = null;
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(
        "begin NN$PSP_UNIT.fetchProfile(?, ?, 'I'); end;");
    cs.setNUMBER(1, nRef);
    cs.registerOutParameter(2, OracleTypes.VARCHAR);
    cs.execute();
    chProfile = cs.getCHAR(2);
    if (cs.wasNull()) chProfile = null;
    cs.close();
  }
  catch(SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "fetchProfileCHAR/SQL: ", e.getMessage());
  }
  return chProfile;
}

//----------------------------------------------------------------------

protected String fetchProfile (NUMBER nRef)
{
  String sAttributes = null;
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(
        "begin NN$PSP_UNIT.fetchProfile(?, ?, 'I'); end;");
    cs.setNUMBER(1, nRef);
    cs.registerOutParameter(2, OracleTypes.VARCHAR);
    cs.execute();
    sAttributes = cs.getString(2);
    if (cs.wasNull()) sAttributes = null;
    cs.close();
  }
  catch(SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "fetchProfile/SQL: ", e.getMessage());
  }
  return sAttributes;
}

//----------------------------------------------------------------------
// XML support:
//----------------------------------------------------------------------

protected void spyXMLContent (String s, Document xdoc, String enc)
{
  if (xdoc != null) {
    XMLOutputter xmlout;
    org.jdom.output.Format frm = org.jdom.output.Format.getPrettyFormat().setEncoding("ISO-8859-5");
    PrintStream out = shell.getLogStream(4);
    if (out != null) {
      out.println(s);
      try {
        xmlout = new XMLOutputter(frm.setEncoding(enc));
        xmlout.output(xdoc, out);
      }
      catch (IOException e) {}
    }
    out = shell.getDebugStream(4);
    if (out != null) {
      out.println(s);
      try {
        xmlout = new XMLOutputter(frm);
        xmlout.output(xdoc, out);
      }
      catch (IOException e) {}
    }
  }
}

//----------------------------------------------------------------------

protected static final int
  PROP_UNKNOWN              =  0,
  PROP_getcontentlength     =  1,
  PROP_getcontentlanguage   =  2,
  PROP_getcontenttype       =  3,
  PROP_getetag              =  4,
  PROP_resourcetype         =  5,
  PROP_displayname          =  6,
  PROP_lockdiscovery        =  7,
  PROP_getlastmodified      =  8,
  PROP_creationdate         =  9,
  PROP_name                 = 10,
  PROP_parentname           = 11,
  PROP_href                 = 12,
  PROP_contentclass         = 13,
  PROP_lastaccessed         = 14,
  PROP_defaultdocument      = 15,
  PROP_ishidden             = 16,
  PROP_iscollection         = 17,
  PROP_isreadonly           = 18,
  PROP_isroot               = 19,
  PROP_isstructureddocument = 20,
  PROP_supportedlock        = 21;

//----------------------------------------------------------------------

private static Hashtable  s_hashProps;

//----------------------------------------------------------------------

protected static void initProps()
{
  s_hashProps = new Hashtable(16);
  s_hashProps.put("getcontentlength",   new Integer(PROP_getcontentlength)  );
  s_hashProps.put("getcontentlanguage", new Integer(PROP_getcontentlanguage));
  s_hashProps.put("getcontenttype",     new Integer(PROP_getcontenttype)    );
  s_hashProps.put("getetag",            new Integer(PROP_getetag)           );
  s_hashProps.put("resourcetype",       new Integer(PROP_resourcetype)      );
  s_hashProps.put("displayname",        new Integer(PROP_displayname)       );
  s_hashProps.put("lockdiscovery",      new Integer(PROP_lockdiscovery)     );
  s_hashProps.put("getlastmodified",    new Integer(PROP_getlastmodified)   );
  s_hashProps.put("creationdate",       new Integer(PROP_creationdate)      );
  s_hashProps.put("name",               new Integer(PROP_name)              );
  s_hashProps.put("href",               new Integer(PROP_href)              );
  s_hashProps.put("contentclass",       new Integer(PROP_contentclass)      );
  s_hashProps.put("lastaccessed",       new Integer(PROP_lastaccessed)      );
  s_hashProps.put("ishidden",           new Integer(PROP_ishidden)          );
  s_hashProps.put("iscollection",       new Integer(PROP_iscollection)      );
  s_hashProps.put("isreadonly",         new Integer(PROP_isreadonly)        );
  s_hashProps.put("isroot",             new Integer(PROP_isroot)            );
  s_hashProps.put("supportedlock",      new Integer(PROP_supportedlock)     );
  s_hashProps.put("parentname",         new Integer(PROP_parentname)        );
}

protected static int mapProp (String sProp)
{
  if (s_hashProps == null) initProps();
  Object o = s_hashProps.get(sProp);
  if (o != null) return ((Integer)o).intValue();
  return PROP_UNKNOWN;
}

protected static Enumeration enumPropNames ()
{
  if (s_hashProps == null) initProps();
  return s_hashProps.keys();
}

protected static Enumeration enumPropCodes ()
{
  if (s_hashProps == null) initProps();
  return s_hashProps.elements();
}

//----------------------------------------------------------------------


protected static SimpleDateFormat dfGMT = null;

protected String fmtGMT (long date)
{
  return fmtGMT(new java.util.Date(date));
}

protected String fmtGMT (java.util.Date date)
{
  if (date != null) {
    if (dfGMT == null) {
      dfGMT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.UK);
      dfGMT.setTimeZone(TimeZone.getTimeZone("GMT"));
    }
//    return dfGMT.format(date)+" GMT";
      return dfGMT.format(date);
  }
  return null;
}

//----------------------------------------------------------------------

protected static SimpleDateFormat dfISO = null;

protected String fmtISO8601 (long date)
{
  return fmtISO8601(new java.util.Date(date));
}

protected String fmtISO8601 (java.util.Date date)
{
  if (date != null) {
    if (dfISO == null) {
      dfISO = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.UK);
      dfISO.setTimeZone(TimeZone.getTimeZone("GMT"));
    }
    return dfISO.format(date).replace('_', 'T')+"Z";
  }
  return null;
}

protected static SimpleDateFormat dfpGMT = null;

protected static java.util.Date parseGMTDate(String date)
{
  if (date != null)
  {
    if (dfpGMT == null)
      dfpGMT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.UK);
    try
    {
      return dfpGMT.parse(date);
    }
    catch(ParseException pe)
    {
      return null;
    }
  }
  return null;
}

//----------------------------------------------------------------------

/* Conditional request support follows */

protected boolean checkModifiedSince(String sDISP, NUMBER nKey)
{
  String sIMS   = this.request.getHeader("If-Modified-Since");
  if ( sIMS != null)
  {
    java.util.Date modsince;
    if (sIMS.indexOf(";") > 0)
      modsince = parseGMTDate(sIMS.substring(0, sIMS.indexOf(";")));
    else
      modsince = parseGMTDate(sIMS);

    java.util.Date filetime = new java.util.Date((getJTime(nKey)/1000)*1000);
    if ( modsince.after(filetime) || modsince.equals(filetime) )
    {
      respStatus(304, HTTP_NOT_MODIFIED);
      return true;
    }
  }
  return false;
}


protected boolean checkNotModifiedSince(String sDISP, NUMBER nKey)
{
  String sIMS   = this.request.getHeader("If-Unmodified-Since");
  if ( sIMS != null)
  {
    java.util.Date modsince;
    if (sIMS.indexOf(";") > 0)
      modsince = parseGMTDate(sIMS.substring(0, sIMS.indexOf(";")));
    else
      modsince = parseGMTDate(sIMS);

    java.util.Date filetime = new java.util.Date((getJTime(nKey)/1000)*1000);

    if ( filetime.after(modsince) )
    {
      respStatus(412, HTTP_PRECONDITION_FAILED);
      return true;
    }
  }
  return false;
}



protected boolean checkIfMatch(String sDISP, NUMBER nKey)
{
  String sifm = this.request.getHeader("If-Match");
  if (sifm != null && !sifm.equals("*"))
  {
    String myetag = calcETag(sDISP, nKey);
    String[] tags = sifm.split(",");
    for (int i = 0; i < tags.length ; i++)
      if (tags[i].startsWith("W/"))
        tags[i] = tags[i].substring(2);     // we will always do strong comparison
    for (int i = 0; i < tags.length ; i++)
    {
      if (myetag.equals(tags[i].trim()))
        return false;
    }
    respStatus(412, HTTP_PRECONDITION_FAILED);
    return true;
  }
  return false;
}


/**
 *  Checks if If-Range value satisfies either of these conditions:<br/>
 *   - it is an HTTP-Date and is after or equals to the file modification time (that is,
 *     the file is unchanged);<br/>
 *   - it is a list of e-tags and one of them matches the file e-tag;<br/>
 *   - no If-Range header present or the header value is *<br/>
 *
 *  If either of these conditions are satisfied, the function returns true,
 *  otherwise it returns false.
 *
 */
protected boolean checkIfRange(String sDISP, NUMBER nKey)
{
  String sifr = this.request.getHeader("If-Range");
  if (sifr != null && !sifr.equals("*"))
  {

    java.util.Date modsince;
    if (sifr.indexOf(";") > 0)
      modsince = parseGMTDate(sifr.substring(0, sifr.indexOf(";")));
    else
      modsince = parseGMTDate(sifr);

    if ( modsince != null)
    {
      java.util.Date filetime = new java.util.Date((getJTime(nKey)/1000)*1000);
      return ( modsince.after(filetime) || modsince.equals(filetime) );
    }
    else
    {
      // If-Range probably contains e-tags
      String myetag = calcETag(sDISP, nKey);
      String[] tags = sifr.split(",");
      for (int i = 0; i < tags.length ; i++)
        if (tags[i].startsWith("W/"))
          tags[i] = tags[i].substring(2);     // we will always do strong comparison
      for (int i = 0; i < tags.length ; i++)
      {
        if (myetag.equals(tags[i].trim()))
          return true;
      }
      return false;
    }
  }
  return true;
}

/**
 * Checks if none of supplied e-tags in If-None-Match header match
 * the e-tag of the entity.
 *
 * @param	file	resource to check against.
 *
 * @return	true if and only if If-None-Match header contains * special
 *		character or one of the supplied e-tags match the resource
 *		e-tag.
 */

protected boolean checkIfNoneMatch(String sDISP, NUMBER nKey)
{
  String sifnm = this.request.getHeader("If-None-Match");
  if (sifnm != null)
  {
    if (sifnm.equals("*"))
      return true;
    String myetag = calcETag(sDISP, nKey);
    String[] tags = sifnm.split(",");
    for (int i = 0; i < tags.length ; i++)
      if (tags[i].startsWith("W/"))
        tags[i] = tags[i].substring(2);    // we sill always do strong comparison
    for (int i = 0; i < tags.length ; i++)
    {
      if (myetag.equals(tags[i].trim()))
        return true;
    }
  }
  return false;
}

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.18 $";
} // class WEBDAV

//----------------------------------------------------------------------
