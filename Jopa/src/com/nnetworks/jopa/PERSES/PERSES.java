//----------------------------------------------------------------------
// PERSES.java
//----------------------------------------------------------------------
// Oracle dictionary WebDAV gateway
// 
// $Id: PERSES.java,v 1.9 2007/01/01 10:00:00 Drew Exp $
//----------------------------------------------------------------------

package com.nnetworks.jopa.PERSES;

import com.nnetworks.jopa.PERSES.*;
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

class PERSES extends Processor
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
protected String    sStatus;
protected Document  xrespDoc;
protected Element   xrespElem;
protected Namespace xrespNS;
protected Namespace xrespNSb;
protected int       iPref;

// LockTable:
protected LockTable locks;
protected int       iLimit;

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

protected void openLocks ()
{
  if (this.locks == null) {
    this.locks = (LockTable) this.shell.getFromShelf("LockTable");
    this.iLimit = getInt(this.props.getProperty("lock_limit", "10"), 10);
    if (this.locks == null) {
      this.locks = new LockTable(this.shell,
                       this.props.getProperty("lock_table", "jopa.lck"));
      this.shell.putOnShelf("LockTable", this.locks);
    }
  }
}

//----------------------------------------------------------------------

protected void cleanLocks ()
{
  if (this.locks != null) {
    this.locks.clean(this.iLimit);
  }
}

//----------------------------------------------------------------------

protected int getInt (String s, int iDef)
{
  if (s != null) {
    try { return Integer.parseInt(s, 10); }
    catch (NumberFormatException e) {}
  }
  return iDef;
}

//----------------------------------------------------------------------
// Probe:
//----------------------------------------------------------------------

protected static final int
  LAYER_ROOT    = 0,
  LAYER_OBJTYPE = 1,
  LAYER_OBJECT  = 2;

//----------------------------------------------------------------------

protected Probe locateProbe ()
{
  Probe probe = new Probe();
  this.iStatus = 200;
  this.sStatus = "OK";

  // Root:
  int it = 0;
  String s = this.props.getProperty("username", "SYS");
  if (!materializeSchema(probe, s)) {
    this.iStatus = (it == this.pi.iLast ? 0 : 404);
    this.sStatus = "No such schema: " + s;
    return probe;
  }
  probe.iLayer = LAYER_ROOT;
  probe.iObjtype = 0;
  probe.bCollection = true;
  probe.sParentName = "";
  probe.sName = "/";
  probe.sHRef = this.sReqBase;
  if (!probe.sHRef.endsWith("/")) probe.sHRef = probe.sHRef + '/';
  if (it == this.pi.iLast) return probe;

  // Objtype:
  it++;
  s = this.pi.getItem(it).trim();
  int i = encodeViewObjtype(s);
  if (i < 1) {
    this.iStatus = (it == this.pi.iLast ? 0 : 404);
    this.sStatus = "Unknown object type: " + s;
    return probe;
  }
  probe.iLayer = LAYER_OBJTYPE;
  probe.iObjtype = i;
  probe.sParentName = probe.sName;
  probe.sName = s;
  if (!probe.sHRef.endsWith("/")) probe.sHRef = probe.sHRef + '/';
  probe.sHRef = probe.sHRef + escape(probe.sName);
  if (it == this.pi.iLast) return probe;

  // Object:
  it++;
  s = this.pi.getItem(it).trim();
  if (!materializeObject(probe, s)) {
    this.iStatus = (it == this.pi.iLast ? 0 : 404);
    this.sStatus = "No such object: " + s;
    return probe;
  }
  probe.iLayer = LAYER_OBJECT;
  probe.bCollection = false;
  if (!probe.sHRef.endsWith("/")) probe.sHRef = probe.sHRef + '/';
  probe.sHRef = probe.sHRef + escape(probe.sName);
  if (it == this.pi.iLast) return probe;

  // Too long path:
  this.iStatus = 404;
  this.sStatus = "Incorrect path";
  return probe;
}

//----------------------------------------------------------------------
// Objtype support:
//----------------------------------------------------------------------

protected static final int
  OBJTYPE_PROCEDURE    = 1,       OBJTYPE_SOURCE_MIN   = 1,
  OBJTYPE_FUNCTION     = 2,
  OBJTYPE_PACKAGE      = 3,
  OBJTYPE_PACKAGE_BODY = 4,
  OBJTYPE_TRIGGER      = 5,
  OBJTYPE_JAVA_SOURCE  = 6,
  OBJTYPE_TYPE         = 7,       OBJTYPE_SOURCE_MAX   = 7,
  OBJTYPE_VIEW         = 8,
  OBJTYPE_ERROR        = 9,
  OBJTYPE_UNKNOWN      = 0;

//----------------------------------------------------------------------

protected int encodeOracleObjtype (String s)
{
  String s1 = s.toUpperCase();
       if (s1.equals("PROCEDURE"))    return OBJTYPE_PROCEDURE;
  else if (s1.equals("FUNCTION"))     return OBJTYPE_FUNCTION;
  else if (s1.equals("PACKAGE"))      return OBJTYPE_PACKAGE;
  else if (s1.equals("PACKAGE BODY")) return OBJTYPE_PACKAGE_BODY;
  else if (s1.equals("TRIGGER"))      return OBJTYPE_TRIGGER;
  else if (s1.equals("JAVA SOURCE"))  return OBJTYPE_JAVA_SOURCE;
  else if (s1.equals("TYPE"))         return OBJTYPE_TYPE;
  else if (s1.equals("VIEW"))         return OBJTYPE_VIEW;
  else if (s1.equals("ERROR"))        return OBJTYPE_ERROR;
  else return OBJTYPE_UNKNOWN;
}

//----------------------------------------------------------------------

protected int encodeViewObjtype (String s)
{
  String s1 = s.toLowerCase();
       if (s1.equals("procedures"))     return OBJTYPE_PROCEDURE;
  else if (s1.equals("functions"))      return OBJTYPE_FUNCTION;
  else if (s1.equals("packages"))       return OBJTYPE_PACKAGE;
  else if (s1.equals("package bodies")) return OBJTYPE_PACKAGE_BODY;
  else if (s1.equals("triggers"))       return OBJTYPE_TRIGGER;
  else if (s1.equals("java source"))    return OBJTYPE_JAVA_SOURCE;
  else if (s1.equals("types"))          return OBJTYPE_TYPE;
  else if (s1.equals("views"))          return OBJTYPE_VIEW;
  else if (s1.equals("errors"))         return OBJTYPE_ERROR;
  else return OBJTYPE_UNKNOWN;
}

//----------------------------------------------------------------------

protected String decodeOracleObjtype (int i)
{
  switch (i) {
    case OBJTYPE_PROCEDURE:      return "PROCEDURE";
    case OBJTYPE_FUNCTION:       return "FUNCTION";
    case OBJTYPE_PACKAGE:        return "PACKAGE";
    case OBJTYPE_PACKAGE_BODY:   return "PACKAGE BODY";
    case OBJTYPE_TRIGGER:        return "TRIGGER";
    case OBJTYPE_JAVA_SOURCE:    return "JAVA SOURCE";
    case OBJTYPE_TYPE:           return "TYPE";
    case OBJTYPE_VIEW:           return "VIEW";
    case OBJTYPE_ERROR:          return "ERROR";
  }
  return "";
}

//----------------------------------------------------------------------

protected String decodeViewObjtype (int i)
{
  switch (i) {
    case OBJTYPE_PROCEDURE:      return "Procedures";
    case OBJTYPE_FUNCTION:       return "Functions";
    case OBJTYPE_PACKAGE:        return "Packages";
    case OBJTYPE_PACKAGE_BODY:   return "Package Bodies";
    case OBJTYPE_TRIGGER:        return "Triggers";
    case OBJTYPE_JAVA_SOURCE:    return "Java Source";
    case OBJTYPE_TYPE:           return "Types";
    case OBJTYPE_VIEW:           return "Views";
    case OBJTYPE_ERROR:          return "Errors";
  }
  return "";
}

//----------------------------------------------------------------------

protected boolean isSourceObject (int i)
{
  return ((i >= OBJTYPE_SOURCE_MIN) && (i <= OBJTYPE_SOURCE_MAX));
}

//----------------------------------------------------------------------

protected String decodeObjtypeExt (int i)
{
  switch (i) {
    case OBJTYPE_PROCEDURE:      return ".prc";
    case OBJTYPE_FUNCTION:       return ".prc";
    case OBJTYPE_PACKAGE:        return ".pkg";
    case OBJTYPE_PACKAGE_BODY:   return ".pkb";
    case OBJTYPE_TRIGGER:        return ".trg";
    case OBJTYPE_JAVA_SOURCE:    return ".java";
    case OBJTYPE_TYPE:           return ".sql";
    case OBJTYPE_VIEW:           return ".sql";
    case OBJTYPE_ERROR:          return ".err";
  }
  return ".sql";
}

//----------------------------------------------------------------------

protected String stripName (String s)
{
  int i = s.lastIndexOf(".");
  if (i > 0) return s.substring(0, i);
  return s;
}

//----------------------------------------------------------------------

protected String stripExt (String s, String sDef)
{
  int i = s.lastIndexOf(".");
  if (i > 0) return s.substring(i);
  return sDef;
}

//----------------------------------------------------------------------

protected String getObjectName (Probe probe)
{
  if (probe.iObjtype == OBJTYPE_JAVA_SOURCE) {
    return stripName(probe.sName);
  }
  return stripName(probe.sName).toUpperCase();
}

//----------------------------------------------------------------------
// Queries:
//----------------------------------------------------------------------

private static final String stmt_schema =
  "begin select CREATED into ? from SYS.ALL_USERS where USERNAME = upper(?);" +
  " select max(LAST_DDL_TIME) into ? from SYS.ALL_OBJECTS where OWNER = upper(?); end;";

//----------------------------------------------------------------------

protected boolean materializeSchema (Probe probe, String sName)
{
  int n = 0;
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(stmt_schema);
    cs.registerOutParameter(1, OracleTypes.DATE);
    cs.setString(2, sName);
    cs.registerOutParameter(3, OracleTypes.DATE);
    cs.setString(4, sName);
    cs.execute();
    probe.dCre = cs.getDATE(1);
    probe.dUpd = cs.getDATE(3);
    n++;
    cs.close();
    probe.sSchema = sName;
  }
  catch (SQLException e) {
    try { m_conn.clearWarnings(); } catch (SQLException e1) {}
    shell.log(0, "materializeSchema/SQL: ", e.getMessage());
  }
  return (n > 0);
}

//----------------------------------------------------------------------

private static final String stmt_object_error =
  "begin select OBJECT_ID, min(LAST_DDL_TIME) into ?, ? from SYS.ALL_OBJECTS " +
  " where OWNER = upper(?) and OBJECT_NAME = ? and STATUS = 'INVALID' group by OBJECT_ID; end;";

private static final String stmt_object =
  "begin select OBJECT_ID, STATUS, CREATED, LAST_DDL_TIME into ?, ?, ?, ? from SYS.ALL_OBJECTS " +
  " where OWNER = upper(?) and OBJECT_TYPE = ? and OBJECT_NAME = ?; end;";

//----------------------------------------------------------------------

protected boolean materializeObject (Probe probe, String sName)
{
  int n = 1;
  String sObjName = stripName(sName);
  if (probe.iObjtype == OBJTYPE_JAVA_SOURCE) {}
  else {
    sObjName = sObjName.toUpperCase();
  }
  try {
    OracleCallableStatement cs = null;
    if (probe.iObjtype == OBJTYPE_ERROR) {
      cs = (OracleCallableStatement)m_conn.prepareCall(stmt_object_error);
      cs.registerOutParameter(1, OracleTypes.NUMBER);
      cs.registerOutParameter(2, OracleTypes.DATE);
      cs.setString(3, probe.sSchema);
      cs.setString(4, sObjName);
      cs.execute();
      probe.nObjID  = cs.getNUMBER(1);
      probe.sStatus = "INVALID";
      probe.dCre    = cs.getDATE(2);
      probe.dUpd    = probe.dCre;
    }
    else {
      cs = (OracleCallableStatement)m_conn.prepareCall(stmt_object);
      cs.registerOutParameter(1, OracleTypes.NUMBER);
      cs.registerOutParameter(2, OracleTypes.VARCHAR);
      cs.registerOutParameter(3, OracleTypes.DATE);
      cs.registerOutParameter(4, OracleTypes.DATE);
      cs.setString(5, probe.sSchema);
      cs.setString(6, decodeOracleObjtype(probe.iObjtype));
      cs.setString(7, sObjName);
      cs.execute();
      probe.nObjID  = cs.getNUMBER(1);
      probe.sStatus = cs.getString(2);
      probe.dCre    = cs.getDATE(3);
      probe.dUpd    = cs.getDATE(4);
    }
    cs.close();
    probe.sParentName = probe.sName;
    probe.sName = sName;
  }
  catch (SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    if (e.getErrorCode() == 1403) {
      n = 0;
    }
    else {
      n = -1;
      shell.log(0, "materializeObject/SQL: ", e.getMessage());
    }
  }
  return (n > 0);
}

//----------------------------------------------------------------------

private static final int
  CH_CR    = 0x0D,
  CH_LF    = 0x0A,
  CH_QUOTE = 0x22,
  CH_SLASH = 0x2F;

private static final String
  CREATE_OR_REPLACE  = "create or replace\n",
  CREATE_JAVA_SOURCE = "create or replace and compile java source named ",
  CREATE_VIEW        = "create or replace view ",
  AS = " as\n";

//----------------------------------------------------------------------

protected byte[] readObject (Probe probe)
{
  int n = 0;
  byte[] au = null;
  ByteArrayOutputStream baos = null;
  try {
    switch (probe.iObjtype) {
      case OBJTYPE_PROCEDURE:
      case OBJTYPE_FUNCTION:
      case OBJTYPE_PACKAGE:
      case OBJTYPE_PACKAGE_BODY:
      case OBJTYPE_TRIGGER:
        au = readSource(probe);
        if (au != null) {
          n = au.length;
          baos = new ByteArrayOutputStream
                       (n + CREATE_OR_REPLACE.length() + 2);
          baos.write(CREATE_OR_REPLACE.getBytes());
          if (n > 0) {
            baos.write(au);
            if (au[n-1] == CH_LF) {} else { baos.write(CH_LF); }
          }
          baos.write(CH_SLASH);
          au = baos.toByteArray();
          baos.close();
        }
        break;

      case OBJTYPE_JAVA_SOURCE:
        au = readSource(probe);
        if (au != null) {
          n = au.length;
          baos = new ByteArrayOutputStream
                       (n +
                        CREATE_JAVA_SOURCE.length() +
                        probe.sName.length() +
                        AS.length() + 4);
          baos.write(CREATE_JAVA_SOURCE.getBytes());
          baos.write(CH_QUOTE);
          baos.write(stripName(probe.sName).getBytes());
          baos.write(CH_QUOTE);
          baos.write(AS.getBytes());
          if (n > 0) {
            baos.write(au);
            if (au[n-1] == CH_LF) {} else { baos.write(CH_LF); }
          }
          baos.write(CH_SLASH);
          au = baos.toByteArray();
          baos.close();
        }
        break;

      case OBJTYPE_VIEW:
        au = readView(probe);
        if (au != null) {
          n = au.length;
          baos = new ByteArrayOutputStream
                       (n +
                        CREATE_VIEW.length() +
                        probe.sName.length() +
                        AS.length() + 2);
          baos.write(CREATE_VIEW.getBytes());
          baos.write(stripName(probe.sName).getBytes());
          baos.write(AS.getBytes());
          if (n > 0) {
            baos.write(au);
            if (au[n-1] == CH_LF) {} else { baos.write(CH_LF); }
          }
          baos.write(CH_SLASH);
          au = baos.toByteArray();
          baos.close();
        }
        break;

      case OBJTYPE_ERROR:
        au = readError(probe);
        break;
    }
  }
  catch (java.io.IOException ioe) {}

  return au;
}

//----------------------------------------------------------------------

private static final String stmt_error =
  "select utl_raw.cast_to_raw(to_char(SEQUENCE)||' ['||to_char(LINE)||','||to_char(POSITION)||'] '||TEXT||chr(13)||chr(10)) TEXT" +
  " from SYS.ALL_ERRORS where OWNER = upper(?) and NAME = ?" +
  " order by SEQUENCE asc";

//----------------------------------------------------------------------

protected byte[] readError (Probe probe)
{
  final int TEXT_SIZE = 8192;
  byte[] au = null;
  try {
    OraclePreparedStatement ps =
      (OraclePreparedStatement)m_conn.prepareStatement(stmt_error);
    ps.setString(1, probe.sSchema);
    ps.setString(2, getObjectName(probe));
    OracleResultSet rs = (OracleResultSet) ps.executeQuery();
    if (rs != null) {
      int n;
      InputStream is = null;
      byte[] buf = new byte[TEXT_SIZE];
      ByteArrayOutputStream baos = new ByteArrayOutputStream(BUFFER_SIZE);
      while (rs.next()) {
        is = rs.getBinaryStream(1);
        n  = TEXT_SIZE;
        while (n == TEXT_SIZE) {
          n = is.read(buf, 0, TEXT_SIZE);
          if (n > 0) baos.write(buf, 0, n);
        }
        is.close();
      }
      au = baos.toByteArray();
      baos.close();
      buf = null;
      rs.close();
    }
    rs = null;
    ps.close();
    ps = null;
  }
  catch (SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "readError/SQL: ", e.getMessage());
  }
  catch (IOException ioe) {
    shell.log(0, "readError/IO: ", ioe.getMessage());
  }
  return au;
}

//----------------------------------------------------------------------

private static final String stmt_source =
  "select utl_raw.cast_to_raw(TEXT) TXT from SYS.ALL_SOURCE" +
  " where OWNER = upper(?) and TYPE = ? and NAME = ? order by LINE asc";

//----------------------------------------------------------------------

protected byte[] readSource (Probe probe)
{
  final int TEXT_SIZE = 4096;
  byte[] au = null;
  try {
    OraclePreparedStatement ps =
      (OraclePreparedStatement)m_conn.prepareStatement(stmt_source);
    ps.setString(1, probe.sSchema);
    ps.setString(2, decodeOracleObjtype(probe.iObjtype));
    ps.setString(3, getObjectName(probe));
    OracleResultSet rs = (OracleResultSet) ps.executeQuery();
    if (rs != null) {
      int n;
      InputStream is = null;
      byte[] buf = new byte[TEXT_SIZE];
      ByteArrayOutputStream baos = new ByteArrayOutputStream(BUFFER_SIZE);
      while (rs.next()) {
        is = rs.getBinaryStream(1);
        n  = TEXT_SIZE;
        while (n == TEXT_SIZE) {
          n = is.read(buf, 0, TEXT_SIZE);
          if (n > 0) baos.write(buf, 0, n);
        }
        is.close();
      }
      au = baos.toByteArray();
      baos.close();
      buf = null;
      rs.close();
    }
    rs = null;
    ps.close();
    ps = null;
  }
  catch (SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "readSource/SQL: ", e.getMessage());
  }
  catch (IOException ioe) {
    shell.log(0, "readSource/IO: ", ioe.getMessage());
  }
  return au;
}

//----------------------------------------------------------------------

private static final String stmt_view =
  "select TEXT_LENGTH, TEXT from SYS.ALL_VIEWS where OWNER = upper(?) and VIEW_NAME = ?";

//----------------------------------------------------------------------

protected byte[] readView (Probe probe)
{
  byte[] au = null;
  try {
    OraclePreparedStatement ps =
      (OraclePreparedStatement)m_conn.prepareStatement(stmt_view);
    ps.setString(1, probe.sSchema);
    ps.setString(2, getObjectName(probe));
    OracleResultSet rs = (OracleResultSet) ps.executeQuery();
    if (rs != null) {
      if (rs.next()) {
        /*
        au = rs.getBytes(2);
        */
        au = convertToBytes(rs.getString(2));
      }
      rs.close();
    }
    rs = null;
    ps.close();
    ps = null;
  }
  catch (SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "readView/SQL: ", e.getMessage());
  }
  return au;
}

//----------------------------------------------------------------------

private static final String stmt_dyn_sql =
  "declare ic integer; ir integer; begin" +
  " ic := sys.DBMS_SQL.open_cursor; sys.DBMS_SQL.parse(ic, ?, sys.DBMS_SQL.NATIVE);" +
  " ir := sys.DBMS_SQL.execute(ic); sys.DBMS_SQL.close_cursor(ic); end;";

//----------------------------------------------------------------------

protected int execDynSQL (StringBuffer sbCode)
{
  return execDynSQL(sbCode.toString());
}

//----------------------------------------------------------------------

protected int execDynSQL (String sCode)
{
  int res = 1;
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(stmt_dyn_sql);
    cs.setCHAR(1, encode(sCode));
    cs.execute();
    cs.close();
  }
  catch (SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "execDynSQL/SQL: ", e.getMessage());
    res = 0;
  }
  return res;
}

//----------------------------------------------------------------------
// Authentication support:
//----------------------------------------------------------------------

protected int authorize ()
{
  // Obtain authorization header:
  String sAuth = this.request.getHeader("Authorization");
  if (sAuth == null) sAuth = this.request.getHeader("HTTP_AUTHORIZATION");
  if (sAuth == null) sAuth = this.request.getHeader("AUTHORIZATION");
  if (sAuth == null) sAuth = this.request.getHeader("authorization");
  if (sAuth == null) sAuth = this.request.getHeader("http_authorization");
  if (sAuth == null) sAuth = this.request.getHeader("Http_Authorization");
  if (sAuth == null) return -1;

  // Parse the authorization data:
  int i = sAuth.indexOf("Basic");
  if (i < 0) return -1;

  String sCode = Base64.decodeToString(sAuth.substring(i+6), this.requestCharset);
  if (sCode == null) return -1;

  i = sCode.indexOf(":");
  if (i <= 0) return -1;

  String sUser = sCode.substring(0, i).trim();
  String sPasw = sCode.substring(i+1).trim();

  String sUsername = this.props.getProperty("username", "*");
  //String sPassword = this.props.getProperty("password", "*");
  String sPassword = Base64.decryptPasw(this.props.getProperty("password", "*"));
  
  shell.log(3, "User: ", sUser, " | ", sUsername);
  shell.log(3, "Pasw: ", sPasw, " | ", sPassword);
  

  // Check username/password;
  if (sUser.equalsIgnoreCase(sUsername) && sPasw.equalsIgnoreCase(sPassword)) {
    return 1;
  }

  return 0;
}

//----------------------------------------------------------------------

protected int authenticate ()
{
  int i = authorize();
  if (i < 0) {
    respAuthenticate("Introduce yourself for PERSES");
  }
  else if (i == 0) {
    respStatus(403, "Forbidden");
    shell.log(3, "Forbidden from authenticate");
  }
  return i;
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

protected void reqDepth (int iTopDef, int iDepthDef)
{
  String sDepth = this.request.getHeader("Depth");
  if (sDepth == null)
    { this.iTop = iTopDef;  this.iDepth = iDepthDef; }
  else if (sDepth.equals("0"))
    { this.iTop = 0;  this.iDepth = 0; }
  else if (sDepth.equals("1"))
    { this.iTop = 0;  this.iDepth = 1; }
  else if (sDepth.equalsIgnoreCase("infinity"))
    { this.iTop = 0;  this.iDepth = Integer.MAX_VALUE; }
  else if (sDepth.equalsIgnoreCase("1,noroot"))
    { this.iTop = 1;  this.iDepth = 1; }
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

protected String stripPath (String sURI, String sServletPath)
{
  if (sURI == null) return null;
  if (sURI.length() == 0) return sURI;
  int i = sURI.indexOf(sServletPath);
  return (i >= 0 ? '<' + sURI.substring(i + sServletPath.length()) : sURI);
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

protected int reqContentBuffer (StringBuffer sb, int uSkip)
{
  int m = 0;
  try {
    ServletInputStream sin = this.request.getInputStream();
    int u = sin.read();
    while (u >= 0) {
      if (u == uSkip) {}
      else {
        sb.append((char)u);
        m++;
      }
      u = sin.read();
    }
  }
  catch (IOException e) {}
  return m;
}

/*
protected int reqContentBuffer (StringBuffer sb)
{
  int m = 0;
  try {
    ServletInputStream sin = this.request.getInputStream();
    byte[] buf = new byte[BUFFER_SIZE];
    int n = sin.read(buf, 0, BUFFER_SIZE);
    while (n > 0) {
      sb.append(new String(buf, 0, n, this.effCharset));
      m += n;
      n = sin.read(buf, 0, BUFFER_SIZE);
    }
  }
  catch (IOException e) {}
  return m;
}
*/
//----------------------------------------------------------------------
// Responses:
//----------------------------------------------------------------------

protected void respWebDAV (int i)
{
  if (i > 0) respHeader("Server", shell.getVersion());
  if (i > 1) respHeader("MS-Author-Via", "DAV");
  if (i > 2) respHeader("Allow",
    "GET,HEAD,OPTIONS,PROPFIND,PUT,LOCK,UNLOCK,DELETE,MOVE,MKCOL");
//    "GET,HEAD,OPTIONS,PUT,PROPFIND,LOCK,UNLOCK,DELETE,COPY,MOVE,MKCOL");
//    "GET,HEAD,OPTIONS,DELETE,PUT,COPY,MOVE,MKCOL,PROPFIND,LOCK,UNLOCK");
  if (i > 3) respHeader("DAV", "1,2");
}

//----------------------------------------------------------------------

protected void respAuthenticate (String sMsg)
{
  respStatus(401, sMsg);
  respHeader("WWW-Authenticate", "Basic realm=\"PERSES\"");
}

//----------------------------------------------------------------------

protected void respBinary (byte[] au)
{
  try {
    ServletOutputStream sos = this.response.getOutputStream();
    sos.write(au);
    sos.close();
  }
  catch (IOException ioe) {
    shell.log(0, "respBinary/IO: ", ioe.getMessage());
  }
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

protected void respActiveLock
( Element   elem1
, Namespace ns
, LockEntry lock
)
{
  String s;
  Element elem2, elem3;

  if (lock.sToken != null) {
    elem2 = appElem(elem1, "activelock", ns, null);

    s = "write";
    elem3 = appElem(elem2, "locktype", ns, null);
            appElem(elem3, s,          ns, null);

    s = "exclusive";
    elem3 = appElem(elem2, "lockscope", ns, null);
            appElem(elem3, s,           ns, null);

    if (lock.sOwner != null) {
      elem3 = appElem(elem2, "owner", ns, null);
              appElem(elem3, "href",  ns, lock.sOwner);
    }

    elem3 = appElem(elem2, "locktoken", ns, null);
            appElem(elem3, "href",      ns, LOCK_TOKEN_SCHEMA + lock.sToken);

    s = (lock.cDepth == '0' ? "0" : "Infinity");
    elem3 = appElem(elem2, "depth", ns, s);

    s = (lock.iTimeout <= 0 ? "Infinity" :
         "Second-" + Integer.toString(lock.iTimeout));
    elem3 = appElem(elem2, "timeout", ns, s);

  }
}

//----------------------------------------------------------------------

protected void respLockDiscovery
( Element   parent
, Namespace ns
, Probe     probe
)
{
  Element elem1 = appElem(parent, "lockdiscovery", ns, null);
  openLocks();
  Vector vec = this.locks.enumLocks
    ( probe.sSchema
    , calcETag(probe)
    , true
    );
  int n = vec.size();
  if (n > 0) {
    for (int i = 0; i < n; i++) {
      respActiveLock(elem1, ns, (LockEntry) vec.elementAt(i));
    }
    vec.removeAllElements();
  }
  vec = null;
}

//----------------------------------------------------------------------
// Support:
//----------------------------------------------------------------------

protected byte[] convertToBytes (String s)
{
  if (s == null) return null;
  if (m_cset != null) {
    return m_cset.convertWithReplacement(s);
  }
  return s.getBytes();
}

//----------------------------------------------------------------------

protected String calcETag (Probe probe)
{
  switch (probe.iLayer) {
    case LAYER_ROOT:
      return "Root";
    case LAYER_OBJTYPE:
      return decodeViewObjtype(probe.iObjtype).replace(' ', '_');
    case LAYER_OBJECT:
      return (probe.iObjtype == OBJTYPE_ERROR ? "ERR" : "OBJ") +
             (probe.nObjID == null ? "000000" : probe.nObjID.stringValue());
  }
  return "Unknown";
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

protected void adjustSourceCode (StringBuffer sb)
{
  int i = sb.indexOf("\r");
  while (i >= 0) {
    sb.deleteCharAt(i);
    i = sb.indexOf("\r");
  }
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
  s_hashProps = new Hashtable(24);
  s_hashProps.put("getcontentlength",     new Integer(PROP_getcontentlength)    );
  s_hashProps.put("getcontentlanguage",   new Integer(PROP_getcontentlanguage)  );
  s_hashProps.put("getcontenttype",       new Integer(PROP_getcontenttype)      );
  s_hashProps.put("getetag",              new Integer(PROP_getetag)             );
  s_hashProps.put("resourcetype",         new Integer(PROP_resourcetype)        );
  s_hashProps.put("displayname",          new Integer(PROP_displayname)         );
  s_hashProps.put("lockdiscovery",        new Integer(PROP_lockdiscovery)       );
  s_hashProps.put("getlastmodified",      new Integer(PROP_getlastmodified)     );
  s_hashProps.put("creationdate",         new Integer(PROP_creationdate)        );
  s_hashProps.put("name",                 new Integer(PROP_name)                );
  s_hashProps.put("parentname",           new Integer(PROP_parentname)          );
  s_hashProps.put("href",                 new Integer(PROP_href)                );
  s_hashProps.put("contentclass",         new Integer(PROP_contentclass)        );
  s_hashProps.put("lastaccessed",         new Integer(PROP_lastaccessed)        );
  s_hashProps.put("defaultdocument",      new Integer(PROP_defaultdocument)     );
  s_hashProps.put("ishidden",             new Integer(PROP_ishidden)            );
  s_hashProps.put("iscollection",         new Integer(PROP_iscollection)        );
  s_hashProps.put("isreadonly",           new Integer(PROP_isreadonly)          );
  s_hashProps.put("isroot",               new Integer(PROP_isroot)              );
  s_hashProps.put("isstructureddocument", new Integer(PROP_isstructureddocument));
  s_hashProps.put("supportedlock",        new Integer(PROP_supportedlock)       );
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

} // class PERSES

//----------------------------------------------------------------------
