//----------------------------------------------------------------------
// DPSPDAV.java
//----------------------------------------------------------------------
// DPSP3 WebDAV gateway
// [09.01.2004]  S.Ageshin
//----------------------------------------------------------------------

package com.nnetworks.jopa.DPSPDAV;

import com.nnetworks.jopa.DPSPDAV.*;
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
import java.util.*;

//----------------------------------------------------------------------

class DPSPDAV extends Processor
{

//----------------------------------------------------------------------

public static final String LOCK_TOKEN_SCHEMA = "opaquelocktoken:";

//----------------------------------------------------------------------

// Request data:
protected String    sReqBase;
protected String    sReqPath;
protected String    sReqName;
protected int       iTop;
protected int       iDepth;
protected Document  xreqDoc;
protected Element   xreqElem;
protected List      xreqList;

// Response data:
protected int       iStatus;
protected CHAR      cStatus;
protected Document  xrespDoc;
protected Element   xrespElem;
protected Namespace xrespNS;
protected Namespace xrespNSb;
protected int       iPref;

//----------------------------------------------------------------------
// Session support:
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
  for (int i = 1; i < this.pi.iCount; i++) {
    this.pi.asPathInfo[i] = unescape(this.pi.asPathInfo[i]);
  }
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
        "begin ? := NN$USR.login(?,?); end;");
    cs.registerOutParameter(1, OracleTypes.INTEGER);
    cs.setString(2, sUsername);
    cs.setString(3, sPassword);
    cs.execute();
    i = cs.getInt(1);
    if (!cs.wasNull()) iAccl = i;
    cs.close();
  }
  catch (SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "authorize/SQL: ", e.getMessage());
  }

  return iAccl;
}

//----------------------------------------------------------------------

protected int authenticate (int iMinAccl)
{
  int iAccl = authorize();
  if (iAccl < 0) {
    respAuthenticate("Introduce yourself for DPSP WebDAV");
    return -2;
  }
  if (iAccl < iMinAccl) {
    respStatus(403, "Forbidden");
    return -1;
  }
  return iAccl;
}

//----------------------------------------------------------------------
// Request support:
//----------------------------------------------------------------------

protected void reqURI ()
{
  StringBuffer sb = new StringBuffer(256);
  sb.append("http://");
  sb.append(this.request.getHeader("Host"));
  //sb.append(this.request.getServletPath());
  sb.append(Processor.getServletPath);
  sb.append('/');
  sb.append(this.pi.getItem(0));
  this.sReqBase = sb.toString();
  this.sReqPath = this.pi.merge(1, -1);
  this.sReqName = this.pi.getItem(this.pi.iLast);
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

protected PathInfo reqDst ()
{
  String sDst = this.request.getHeader("Destination");
  if (sDst == null) return null;
  PathInfo dpi = new PathInfo(sDst, this.request.getServletPath());
  for (int i = 1; i < dpi.iCount; i++) {
    dpi.asPathInfo[i] = unescape(dpi.asPathInfo[i]);
    //dpi.asPathInfo[i] = adjustString(unescape(dpi.asPathInfo[i]));
  }
  return dpi;
}

//----------------------------------------------------------------------

protected String stripPath (String sURI, String sServletPath)
{
  if (sURI == null) return null;
  if (sURI.length() == 0) return sURI;
  int i = sURI.indexOf(sServletPath);
  return (i >= 0 ? '<' + sURI.substring(i + sServletPath.length()) : sURI);
}

//----------------------------------------------------------------------

protected String adjustIfv (String sIfv, String sServletPath)
{
  if (sIfv == null) return null;
  int iLen = sIfv.length();
  if (iLen == 0) return sIfv;
  //
  int iLPar, iRPar;
  String s;
  StringBuffer sb = new StringBuffer(iLen);
  int iPos = 0;
  while (iPos < iLen) {
    iLPar = sIfv.indexOf("(", iPos);
    if (iLPar < iPos) break;
    if (iLPar > iPos) {
      if (sb.length() > 0) sb.append(' ');
      s = sIfv.substring(iPos, iLPar).trim();
      if (s.length() > 0) {
        sb.append(stripPath(s, sServletPath));
        sb.append(' ');
      }
    }
    iPos = iLPar + 1;
    iRPar = sIfv.indexOf(")", iPos);
    if (iRPar < iPos) break;
    iPos = iRPar + 1;
    sb.append(sIfv.substring(iLPar, iPos));
  }
  return sb.toString();
}

//----------------------------------------------------------------------

protected String reqIfv ()
{
  String sIfv = this.request.getHeader("If");
  if (sIfv == null) return null;
  int iLen = sIfv.length();
  if (iLen == 0) return sIfv;
  return adjustIfv(sIfv, this.request.getServletPath());
}

//----------------------------------------------------------------------

protected String reqLockToken ()
{
  String sLockToken = this.request.getHeader("Lock-Token");
  if (sLockToken == null) return null;
  int n = sLockToken.length();
  if (n <= 0) return null;
  int i = n-1;
  if (sLockToken.charAt(0) == '<' &&
      sLockToken.charAt(i) == '>') {
    sLockToken = sLockToken.substring(1, i);
  }
  i = sLockToken.indexOf(':');
  if (i >= 0) {
    sLockToken = sLockToken.substring(i+1);
  }
  return sLockToken;
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
// Responses:
//----------------------------------------------------------------------

protected void respWebDAV (int i)
{
  if (i > 0) respHeader("Server", shell.getVersion());
  if (i > 1) respHeader("MS-Author-Via", "DAV");
  if (i > 2) respHeader("Allow",
    "GET,HEAD,OPTIONS,DELETE,PUT,COPY,MOVE,MKCOL,PROPFIND,LOCK,UNLOCK");
  if (i > 3) respHeader("DAV", "1,2");
}

//----------------------------------------------------------------------

protected void respAuthenticate (String sMsg)
{
  respStatus(401, sMsg);
  respHeader("WWW-Authenticate", "Basic realm=\"DPSP via WebDAV\"");
}

//----------------------------------------------------------------------
// XML response:
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

protected Namespace borrowNS
( Element elem
)
{
  return elem == null ? null :
         elem.getNamespaceURI().equals("DAV:") ? null :
         elem.getNamespace();
}

//----------------------------------------------------------------------

protected Element appElem
( Element   parent
, String    sName
, Namespace ns
, String    sText
)
{
  if (ns == null) { ns = this.xrespNS; }
  Element elem = new Element(sName, ns);
  parent.addContent(elem);
  if (sText != null) { elem.setText(sText); }
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

protected void respXMLContent (String enc)
{
  respContentType("text/xml; charset=\"" + enc + '\"');
  try {
    OutputStream out = this.response.getOutputStream();
    Format frm = Format.getPrettyFormat().setEncoding(enc);
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

protected String appPath
( String sPath
, String sName
)
{
  StringBuffer sb = new StringBuffer(sPath);
  if (!sPath.endsWith("/")) {
    sb.append('/');
  }
  sb.append(sName);
  return sb.toString();
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

//----------------------------------------------------------------------

protected void respActiveLock
( Element   elem1
, Namespace ns
, DAVLock   rLock
)
{
  String s;
  Element elem2, elem3;

  if (rLock.sToken != null) {
    elem2 = appElem(elem1, "activelock", ns, null);

    if (rLock.sType != null) {
      if (rLock.sType.equals("W")) s = "write";
      else s = "write";
      elem3 = appElem(elem2, "locktype", ns, null);
              appElem(elem3, s,          ns, null);
    }

    if (rLock.sScope != null) {
      if (rLock.sScope.equals("E")) s = "exclusive";
      else if (rLock.sScope.equals("S")) s = "shared";
      else s = "exclusive";
      elem3 = appElem(elem2, "lockscope", ns, null);
              appElem(elem3, s,           ns, null);
    }

    if (rLock.sOwner != null) {
      elem3 = appElem(elem2, "owner", ns, null);
              appElem(elem3, "href",  ns, rLock.sOwner);
    }

    elem3 = appElem(elem2, "locktoken", ns, null);
            appElem(elem3, "href",      ns, LOCK_TOKEN_SCHEMA + rLock.sToken);

    if (rLock.sDepth != null) {
      if (rLock.sDepth.equals("I")) s = "Infinity";
      else s = rLock.sDepth;
      elem3 = appElem(elem2, "depth", ns, s);
    }

    s = (rLock.nTimeout == null ? "Infinity" :
         rLock.nTimeout.sign() <= 0 ? "Infinity" :
         "Second-" + rLock.nTimeout.stringValue());
    elem3 = appElem(elem2, "timeout", ns, s);

  }
}

//----------------------------------------------------------------------

protected void respLockDiscovery
( Element   parent
, Namespace ns
, NUMBER    nEnt
)
{
  Element elem1 = appElem(parent, "lockdiscovery", ns, null);
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(
        "begin NN$REP.enumLocks(?, ?, true); end;");
    cs.registerOutParameter(1, OracleTypes.CURSOR);
    cs.setNUMBER(2, nEnt);
    cs.execute();
    OracleResultSet rs = (OracleResultSet) cs.getCursor(1);
    if (cs.wasNull()) rs = null;
    cs.close();
    //
    if (rs != null) {
      DAVLock rLock = new DAVLock();
      while (rs.next()) {
        rLock.materialize(rs);
        respActiveLock(elem1, ns, rLock);
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

protected void respLockDiscovery
( Element   parent
, Namespace ns
, String    sToken
)
{
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(
        "declare r NN$REP.tRecLock; begin NN$REP.obtainLock(r, ?); " +
        " ? := r.LOCK_TOKEN;  ? := r.OWNER;      ? := r.TIMEOUT;   " +
        " ? := r.LOCK_DEPTH;  ? := r.LOCK_SCOPE; ? := r.LOCK_TYPE; end;");
    cs.setString(1, sToken);
    DAVLock.registerOutParameters(cs, 1);
    cs.execute();
    { DAVLock rLock = new DAVLock();
      Element elem1 = appElem(parent, "lockdiscovery", ns, null);
      rLock.materialize(cs, 1);
      respActiveLock(elem1, ns, rLock);
    }
    cs.close();
  }
  catch(SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "respLockDiscovery/SQL: ", e.getMessage());
  }
}

//----------------------------------------------------------------------
/*
protected void respLockDiscovery
( Element   parent
, Namespace ns
, NUMBER    nEnt
)
{
  String s, st;
  NUMBER n;
  Element elem1, elem2, elem3;
  elem1 = appElem(parent, "lockdiscovery", ns, null);
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(
        "begin NN$REP.enumLocks(?, ?, true); end;");
    cs.registerOutParameter(1, OracleTypes.CURSOR);
    cs.setNUMBER(2, nEnt);
    cs.execute();
    OracleResultSet rs = (OracleResultSet) cs.getCursor(1);
    if (cs.wasNull()) rs = null;
    cs.close();
    if (rs != null) {
      while (rs.next()) {
        elem2 = appElem(elem1, "activelock", ns, null);

        st = rs.getString("LOCK_TOKEN");  if (rs.wasNull()) st = null;
        if (st != null) {
          s = rs.getString("LOCK_TYPE");  if (rs.wasNull()) s = null;
               if (s == null) s = "write";
          else if (s.equals("W")) s = "write";
          else s = "write";
          elem3 = appElem(elem2, "locktype", ns, null);
                  appElem(elem3, s, ns, null);

          s = rs.getString("LOCK_SCOPE");  if (rs.wasNull()) s = null;
               if (s == null) s = "exclusive";
          else if (s.equals("E")) s = "exclusive";
          else if (s.equals("S")) s = "shared";
          else s = "exclusive";
          elem3 = appElem(elem2, "lockscope", ns, null);
                  appElem(elem3, s, ns, null);

          s = rs.getString("OWNER");       if (rs.wasNull()) s = null;
          if (s == null) s = "SYSTEM";
          elem3 = appElem(elem2, "owner", ns, null);
                  appElem(elem3, "href", ns, s);

          elem3 = appElem(elem2, "locktoken", ns, null);
                  appElem(elem3, "href", ns, LOCK_TOKEN_SCHEMA + st);

          s = rs.getString("LOCK_DEPTH");  if (rs.wasNull()) s = null;
          if (s == null) s = "0";
          else if (s.equals("I")) s = "Infinity";
          elem3 = appElem(elem2, "depth", ns, s);

          n = rs.getNUMBER("TIMEOUT");    if (rs.wasNull()) n = null;
          s = (n == null ? "Infinity" : "Second-" + n.stringValue());
          elem3 = appElem(elem2, "timeout", ns, s);
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
*/
//----------------------------------------------------------------------
// DAVNode support:
//----------------------------------------------------------------------

private static String stmt_obtainNode
  = "declare r NN$DAV.tRecNode; \n"
  + "begin NN$DAV.obtainNode(?, ?, r, ?, ?); \n"
  + " ? := r.ID;       ? := r.ID_DIR;   ? := r.NAME;    ? := r.KIND; "
  + " ? := r.DT_INS;   ? := r.DT_UPD;   ? := r.ETAG;    ? := r.ACCL; "
  + " ? := r.BODYSIZE; ? := r.MIMETYPE; ? := r.CHARSET; ? := r.DT;   "
  + " ? := r.PATH; "
  + "end;";

//----------------------------------------------------------------------

private static String stmt_locateNode
  = "declare r NN$DAV.tRecNode; \n"
  + "begin NN$DAV.locateNode(?, ?, r, ?, ?); \n"
  + " ? := r.ID;       ? := r.ID_DIR;   ? := r.NAME;    ? := r.KIND; "
  + " ? := r.DT_INS;   ? := r.DT_UPD;   ? := r.ETAG;    ? := r.ACCL; "
  + " ? := r.BODYSIZE; ? := r.MIMETYPE; ? := r.CHARSET; ? := r.DT;   "
  + " ? := r.PATH; "
  + "end;";

//----------------------------------------------------------------------

protected DAVNode obtainNode (NUMBER nEnt, int iOpl)
{
  DAVNode rNode = null;
  if (nEnt == null) return null;
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(stmt_obtainNode);
    cs.registerOutParameter(1, OracleTypes.NUMBER);
    cs.registerOutParameter(2, OracleTypes.VARCHAR);
    cs.setNUMBER(3, nEnt);
    cs.setInt(4, iOpl);
    DAVNode.registerOutParameters(cs, 4);
    cs.execute();
    this.iStatus = cs.getInt(1);
    this.cStatus = cs.getCHAR(2);
    if (this.iStatus == 200) {
      rNode = new DAVNode();
      rNode.materialize(cs, 4);
    }
    cs.close();
  }
  catch (SQLException e) {
    try { m_conn.clearWarnings(); } catch (SQLException e1) {}
    shell.log(0, "obtainNode/SQL:", e.getMessage());
  }
  return rNode;
}

//----------------------------------------------------------------------

protected DAVNode locateNode (String sPath, int iOpl)
{
  DAVNode rNode = null;
  if (sPath == null) return null;
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(stmt_locateNode);
    cs.registerOutParameter(1, OracleTypes.NUMBER);
    cs.registerOutParameter(2, OracleTypes.VARCHAR);
    cs.setCHAR(3, encode(sPath));
//    cs.setString(3, sPath);
    cs.setInt(4, iOpl);
    DAVNode.registerOutParameters(cs, 4);
    cs.execute();
    this.iStatus = cs.getInt(1);
    this.cStatus = cs.getCHAR(2);
    if (this.iStatus == 200) {
      rNode = new DAVNode();
      rNode.materialize(cs, 4);
    }
    cs.close();
  }
  catch(SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "locateNode/SQL:", e.getMessage());
  }
  return rNode;
}

//----------------------------------------------------------------------

protected OracleResultSet enumSubNodes (NUMBER nEnt, int iOpl)
{
  OracleResultSet rs = null;
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(
        "begin NN$DAV.enumSubNodes(?, ?, ?); end;");
    cs.registerOutParameter(1, OracleTypes.CURSOR);
    cs.setNUMBER(2, nEnt);
    cs.setInt(3, iOpl);
    cs.execute();
    rs = (OracleResultSet) cs.getCursor(1);
    cs.close();
  }
  catch(SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "enumSubNodes/SQL: ", e.getMessage());
  }
  return rs;
}

//----------------------------------------------------------------------
// SQL support:
//----------------------------------------------------------------------

protected CHAR getKeyPath (NUMBER nKey)
{
  CHAR cPath = null;
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(
        "begin ? := NN$REP.getPath(?); end;");
    cs.registerOutParameter(1, OracleTypes.VARCHAR);
    cs.setNUMBER(2, nKey);
    cs.execute();
    cPath = cs.getCHAR(1);
    if (cs.wasNull()) cPath = null;
    cs.close();
  }
  catch(SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "getKeyPath/SQL: ", e.getMessage());
  }
  return cPath;
}

//----------------------------------------------------------------------

protected CHAR getKeyName (NUMBER nKey)
{
  CHAR cName = null;
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(
        "declare r NN$REP.tRecKey; " +
        " begin NN$REP.obtainKey(r, ?); ? := r.NAME; end;");
    cs.setNUMBER(1, nKey);
    cs.registerOutParameter(2, OracleTypes.VARCHAR);
    cs.execute();
    cName = cs.getCHAR(2);
    if (cs.wasNull()) cName = null;
    cs.close();
  }
  catch(SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "getKeyName/SQL: ", e.getMessage());
  }
  return cName;
}

//----------------------------------------------------------------------
// Support:
//----------------------------------------------------------------------

protected String calcETag (NUMBER nEnt)
{
  if (nEnt == null) return null;
  return "DPSP" + nEnt.stringValue();
}

//----------------------------------------------------------------------

protected String guessContentType (String sKind)
{
  String sType;
       if (sKind.equals("PSP")) sType = "text/plain";
  else if (sKind.equals("DAT")) sType = "application/octet-stream";
  else sType = "application/octet-stream";
  return sType;
}

//----------------------------------------------------------------------
// Guess:
//----------------------------------------------------------------------

protected String guessKind (String sName, String sDef)
{
  int i = sName.lastIndexOf('.');
  if (i >= 0) {
    String sExt = sName.substring(i).toLowerCase();
    if (sExt.equals(".psp"))  return "PSP";
    if (sExt.equals(".dpsp")) return "PSP";
    if (sExt.equals(".htm"))  return "PSP";
    if (sExt.equals(".html")) return "PSP";
    if (sExt.equals(".css"))  return "PSP";
    if (sExt.equals(".js"))   return "PSP";
    if (sExt.equals(".wml"))  return "PSP";
  }
  return sDef;
}

//----------------------------------------------------------------------

protected String guessMIMEType (String sName, String sDef)
{
  int i = sName.lastIndexOf('.');
  if (i >= 0) {
    String sExt = sName.substring(i).toLowerCase();
    if (sExt.equals(".gif"))  return "image/gif";
    if (sExt.equals(".jpg"))  return "image/pjpeg";
    if (sExt.equals(".bmp"))  return "image/bmp";
    if (sExt.equals(".ico"))  return "image/x-icon";
    if (sExt.equals(".css"))  return "text/css";
    if (sExt.equals(".htm"))  return "text/html";
    if (sExt.equals(".html")) return "text/html";
    if (sExt.equals(".data")) return "text/plain";
    if (sExt.equals(".dpsp")) return "text/plain";
    if (sExt.equals(".psp"))  return "text/plain";
    if (sExt.equals(".js"))   return "text/plain";
    if (sExt.equals(".wml"))  return "text/plain";
    if (sExt.equals(".zip"))  return "application/x-zip-compressed";
    if (sExt.equals(".jar"))  return "application/x-zip-compressed";
    if (sExt.equals(".rar"))  return "application/x-rar-compressed";
  }
  return sDef;
}

//----------------------------------------------------------------------

protected String guessCodeType (String sName)
{
  int i = sName.lastIndexOf('.');
  if (i < 0) return "PSP";
  String sExt = sName.substring(i).toLowerCase();
  if (sExt.equals(".psp")) return "PSP";
  if (sExt.equals(".css")) return "CSS";
  if (sExt.equals(".js"))  return "JAVASCRIPT";
  if (sExt.equals(".wml")) return "WML";
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
// XML support:
//----------------------------------------------------------------------

protected void spyXMLContent (String s, Document xdoc, String enc)
{
  if (xdoc != null) {
    XMLOutputter xmlout;
    Format frm = Format.getPrettyFormat().setEncoding("ISO-8859-5");
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
// Properties:
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

protected String fmtGMT (DATE date)
{
  if (date != null) {
    try {
      return date.toText("Dy, DD Mon YYYY HH24:MM:SS", "ENGLISH") + " GMT";
    }
    catch (SQLException e) {}
  }
  return null;
}

protected String fmtISO8601 (DATE date)
{
  if (date != null) {
    try {
      return date.toText("YYYY-MM-DD_HH24:MM:SS", "ENGLISH").replace('_', 'T') + "Z";
    }
    catch (SQLException e) {}
  }
  return null;
}

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.5 $";
} // class DPSPDAV

//----------------------------------------------------------------------
