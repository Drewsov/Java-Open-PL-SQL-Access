//----------------------------------------------------------------------
// PERSES.PROPFIND.java
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

public class PROPFIND extends PERSES
implements JOPAMethod
{

//----------------------------------------------------------------------

private static final int
  PROPFIND_PROPNAME = 0,
  PROPFIND_ALLPROP  = 1,
  PROPFIND_PROP     = 2;

//----------------------------------------------------------------------

private int iPropfindMode;

private OraclePreparedStatement ps;

//----------------------------------------------------------------------

public void service ()
throws ServletException, IOException
{
  try {
    establishConnection();
    if (authenticate() > 0) {
      reqURI();
      reqDepth(0, 0);
      operPROPFIND();
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

private void operPROPFIND ()
throws ServletException, IOException
{
  Probe probe = locateProbe();
  if (this.iStatus != 200) {
    respStatus
    ( this.iStatus == 0 ? 404 : this.iStatus
    , this.sStatus
    );
    return;
  }

  this.iPropfindMode = reqPropfindMode();

  respStatus(207, "Multi-Status");
  respWebDAV(4);
  respDoc("multistatus");

  switch (probe.iLayer) {
    case LAYER_ROOT:     propfindRoot(0, probe);     break;
    case LAYER_OBJTYPE:  propfindObjtype(0, probe);  break;
    case LAYER_OBJECT:   propfindObject(0, probe);   break;
  }

  respXMLContent("UTF-8");
}

//----------------------------------------------------------------------

private int reqPropfindMode ()
throws IOException
{
  this.xreqList = null;
  if (this.request.getContentLength() > 0) {
    if (reqXMLContent()) {
      if (this.xreqElem.getName().equals("propfind")) {
        Element child = (Element)(this.xreqElem.getChildren().get(0));
        if (child != null) {
          String sName = child.getName();
          if (sName.equals("allprop"))  return PROPFIND_ALLPROP;
          if (sName.equals("propname")) return PROPFIND_PROPNAME;
          if (sName.equals("prop")) {
            this.xreqList = child.getChildren();
            if (this.xreqList != null) return PROPFIND_PROP;
          }
        }
      }
    }
  }
  return PROPFIND_ALLPROP;
}

//----------------------------------------------------------------------
// Root support;
//----------------------------------------------------------------------

private void propfindRoot
( int    iLevel
, Probe  probe
) throws ServletException, IOException
{
  // Response:
  if (iLevel >= this.iTop) {
    switch (this.iPropfindMode) {
      case PROPFIND_PROPNAME:  respPropnameRoot(probe);  break;
      case PROPFIND_ALLPROP:   respAllprop(probe);       break;
      case PROPFIND_PROP:      respProp(probe);          break;
    }
  }

  // Deep-quest:
  if (iLevel < this.iDepth) {
    propfindObjtypeFor(iLevel, probe, OBJTYPE_PROCEDURE);
    propfindObjtypeFor(iLevel, probe, OBJTYPE_FUNCTION);
    propfindObjtypeFor(iLevel, probe, OBJTYPE_PACKAGE);
    propfindObjtypeFor(iLevel, probe, OBJTYPE_PACKAGE_BODY);
    propfindObjtypeFor(iLevel, probe, OBJTYPE_TRIGGER);
    propfindObjtypeFor(iLevel, probe, OBJTYPE_JAVA_SOURCE);
    propfindObjtypeFor(iLevel, probe, OBJTYPE_VIEW);
    propfindObjtypeFor(iLevel, probe, OBJTYPE_ERROR);
  }
}

//----------------------------------------------------------------------

private void propfindObjtypeFor
( int    iLevel
, Probe  probeParent
, int    iObjtype
) throws ServletException, IOException
{
  Probe probe = new Probe();
  probe.iLayer = LAYER_OBJTYPE;
  probe.iObjtype = iObjtype;
  probe.bCollection = true;
  probe.sSchema = probeParent.sSchema;
  probe.sParentName = probeParent.sName;
  probe.sName = decodeViewObjtype(iObjtype);
  if (!probeParent.sHRef.endsWith("/"))
    probe.sHRef = probeParent.sHRef + '/' + escape(probe.sName);
  else if (!probeParent.sHRef.endsWith("/")&&probe.bCollection)
      probe.sHRef = probeParent.sHRef + '/';
  else
    probe.sHRef = probeParent.sHRef + escape(probe.sName);
  probe.nObjID = null;
  probe.sStatus = "";
  probe.dCre = probeParent.dCre;
  probe.dUpd = null;
  propfindObjtype(iLevel+1, probe);
}

//----------------------------------------------------------------------

private void respPropnameRoot
( Probe probe
) throws ServletException, IOException
{
  Element elem0, elem1, elem2, elem3;
  elem0 = appElem(this.xrespElem, "response", null, null);
  elem1 = appElem(elem0, "href", null, probe.sHRef);
  elem1 = appElem(elem0, "propstat", null, null);
  elem2 = appElem(elem1, "status", null, "HTTP/1.1 200 OK");
  elem2 = appElem(elem1, "prop", null, null);
  //
  appElem(elem2, "getcontentlanguage",   null, null);
  appElem(elem2, "resourcetype",         null, null);
  appElem(elem2, "displayname",          null, null);
  appElem(elem2, "name",                 null, null);
  appElem(elem2, "href",                 null, null);
  appElem(elem2, "ishidden",             null, null);
  appElem(elem2, "iscollection",         null, null);
  appElem(elem2, "isreadonly",           null, null);
  appElem(elem2, "isroot",               null, null);
  appElem(elem2, "isstructureddocument", null, null);
}

//----------------------------------------------------------------------
// Schema support:
//----------------------------------------------------------------------
/*
private void propfindSchema
( int    iLevel
, Probe  probe
) throws ServletException, IOException
{
  // Response:
  if (iLevel >= this.iTop) {
    switch (this.iPropfindMode) {
      case PROPFIND_PROPNAME:  respPropnameSchema(probe);  break;
      case PROPFIND_ALLPROP:   respAllprop(probe);         break;
      case PROPFIND_PROP:      respProp(probe);            break;
    }
  }

  // Deep-quest:
  if (iLevel < this.iDepth) {
    propfindObjtypeFor(iLevel, probe, OBJTYPE_PROCEDURE);
    propfindObjtypeFor(iLevel, probe, OBJTYPE_FUNCTION);
    propfindObjtypeFor(iLevel, probe, OBJTYPE_PACKAGE);
    propfindObjtypeFor(iLevel, probe, OBJTYPE_PACKAGE_BODY);
    propfindObjtypeFor(iLevel, probe, OBJTYPE_TRIGGER);
    propfindObjtypeFor(iLevel, probe, OBJTYPE_JAVA_SOURCE);
    propfindObjtypeFor(iLevel, probe, OBJTYPE_TYPE);
    propfindObjtypeFor(iLevel, probe, OBJTYPE_VIEW);
  }
}
*/
//----------------------------------------------------------------------
/*
private void respPropnameSchema
( Probe probe
) throws ServletException, IOException
{
  Element elem0, elem1, elem2, elem3;
  elem0 = appElem(this.xrespElem, "response", null, null);
  elem1 = appElem(elem0, "href", null, probe.sHRef);
  elem1 = appElem(elem0, "propstat", null, null);
  elem2 = appElem(elem1, "status", null, "HTTP/1.1 200 OK");
  elem2 = appElem(elem1, "prop", null, null);
  //
  appElem(elem2, "getcontentlanguage",   null, null);
  appElem(elem2, "resourcetype",         null, null);
  appElem(elem2, "displayname",          null, null);
  appElem(elem2, "getlastmodified",      null, null);
  appElem(elem2, "creationdate",         null, null);
  appElem(elem2, "name",                 null, null);
  appElem(elem2, "parentname",           null, null);
  appElem(elem2, "href",                 null, null);
  appElem(elem2, "ishidden",             null, null);
  appElem(elem2, "iscollection",         null, null);
  appElem(elem2, "isreadonly",           null, null);
  appElem(elem2, "isroot",               null, null);
  appElem(elem2, "isstructureddocument", null, null);
}
*/
//----------------------------------------------------------------------
// Objtype support:
//----------------------------------------------------------------------

private void propfindObjtype
( int    iLevel
, Probe  probe
) throws ServletException, IOException
{
  // Response:
  if (iLevel >= this.iTop) {
    switch (this.iPropfindMode) {
      case PROPFIND_PROPNAME:  respPropnameObjtype(probe);  break;
      case PROPFIND_ALLPROP:   respAllprop(probe);          break;
      case PROPFIND_PROP:      respProp(probe);             break;
    }
  }

  // Deep-quest:
  if (iLevel < this.iDepth) {
    int iLevel1 = iLevel + 1;
    Probe probe1 = new Probe();
    probe1.iLayer = LAYER_OBJECT;
    probe1.iObjtype = probe.iObjtype;
    probe1.bCollection = false;
    probe1.sSchema = probe.sSchema;
    probe1.sParentName = probe.sName;
    try {
      if (probe1.iObjtype == OBJTYPE_ERROR) {
        OracleResultSet rs = enumErrors(probe1.sSchema);
        if (rs != null) {
          while (rs.next()) {
            probe1.sName = rs.getString("OBJECT_NAME") + decodeObjtypeExt(probe1.iObjtype);
            if (!rs.wasNull()) {
              if (probe.sHRef.endsWith("/"))
                probe1.sHRef = probe.sHRef + probe1.sName;
              else
                probe1.sHRef = probe.sHRef + '/' + probe1.sName;
              probe1.nObjID  = rs.getNUMBER("OBJECT_ID");
              probe1.sStatus = "INVALID";
              probe1.dCre    = rs.getDATE("CREATED");
              probe1.dUpd    = null;
              propfindObject(iLevel1, probe1);
            }
          }
          rs.close();
          this.ps.close();
        }
      }
      else {
        OracleResultSet rs = enumObjects(probe1.sSchema, probe1.iObjtype);
        if (rs != null) {
          while (rs.next()) {
            probe1.sName = rs.getString("OBJECT_NAME") + decodeObjtypeExt(probe1.iObjtype);
            if (!rs.wasNull()) {
              if (probe.sHRef.endsWith("/"))
                probe1.sHRef = probe.sHRef + probe1.sName;
              else
                probe1.sHRef = probe.sHRef + '/' + probe1.sName;
              probe1.nObjID  = rs.getNUMBER("OBJECT_ID");
              probe1.sStatus = rs.getString("STATUS");
              probe1.dCre    = rs.getDATE("CREATED");
              probe1.dUpd    = rs.getDATE("LAST_DDL_TIME");
              propfindObject(iLevel1, probe1);
            }
          }
          rs.close();
          this.ps.close();
        }
      }
    }
    catch (SQLException e) {
      shell.log(0, "Object deep-quest: ", e.getMessage());
    }
  }
}

//----------------------------------------------------------------------

private void respPropnameObjtype
( Probe probe
) throws ServletException, IOException
{
  Element elem0, elem1, elem2, elem3;
  elem0 = appElem(this.xrespElem, "response", null, null);
  elem1 = appElem(elem0, "href", null, probe.sHRef);
  elem1 = appElem(elem0, "propstat", null, null);
  elem2 = appElem(elem1, "status", null, "HTTP/1.1 200 OK");
  elem2 = appElem(elem1, "prop", null, null);
  //
  appElem(elem2, "getcontentlanguage",   null, null);
  appElem(elem2, "resourcetype",         null, null);
  appElem(elem2, "displayname",          null, null);
  appElem(elem2, "getlastmodified",      null, null);
  appElem(elem2, "creationdate",         null, null);
  appElem(elem2, "name",                 null, null);
  appElem(elem2, "parentname",           null, null);
  appElem(elem2, "href",                 null, null);
  appElem(elem2, "ishidden",             null, null);
  appElem(elem2, "iscollection",         null, null);
  appElem(elem2, "isreadonly",           null, null);
  appElem(elem2, "isroot",               null, null);
  appElem(elem2, "isstructureddocument", null, null);
}

//----------------------------------------------------------------------
// Object support:
//----------------------------------------------------------------------

private void propfindObject
( int    iLevel
, Probe  probe
) throws ServletException, IOException
{
  // Response:
  if (iLevel >= this.iTop) {
    switch (this.iPropfindMode) {
      case PROPFIND_PROPNAME:  respPropnameObject(probe);  break;
      case PROPFIND_ALLPROP:   respAllprop(probe);         break;
      case PROPFIND_PROP:      respProp(probe);            break;
    }
  }
}

//----------------------------------------------------------------------

private void respPropnameObject
( Probe probe
) throws ServletException, IOException
{
  Element elem0, elem1, elem2, elem3;
  elem0 = appElem(this.xrespElem, "response", null, null);
  elem1 = appElem(elem0, "href", null, probe.sHRef);
  elem1 = appElem(elem0, "propstat", null, null);
  elem2 = appElem(elem1, "status", null, "HTTP/1.1 200 OK");
  elem2 = appElem(elem1, "prop", null, null);
  //
  appElem(elem2, "getcontentlength",     null, null);
  appElem(elem2, "getcontentlanguage",   null, null);
  appElem(elem2, "getcontenttype",       null, null);
  appElem(elem2, "getetag",              null, null);
  appElem(elem2, "resourcetype",         null, null);
  appElem(elem2, "displayname",          null, null);
  appElem(elem2, "lockdiscovery",        null, null);
  appElem(elem2, "getlastmodified",      null, null);
  appElem(elem2, "creationdate",         null, null);
  appElem(elem2, "name",                 null, null);
  appElem(elem2, "parentname",           null, null);
  appElem(elem2, "href",                 null, null);
  appElem(elem2, "ishidden",             null, null);
  appElem(elem2, "iscollection",         null, null);
  appElem(elem2, "isreadonly",           null, null);
  appElem(elem2, "isroot",               null, null);
  appElem(elem2, "isstructureddocument", null, null);
  appElem(elem2, "supportedlock",        null, null);
}

//----------------------------------------------------------------------
// Responses:
//----------------------------------------------------------------------

private void respAllprop
( Probe probe
) throws ServletException, IOException
{
  Element elem0, elem1, elem2, elem3;
  elem0 = appElem(this.xrespElem, "response", null, null);
  elem1 = appElem(elem0, "href", null, probe.sHRef);
    elem1 = appElem(elem0, "propstat", null, null);
      elem2 = appElem(elem1, "status", null, "HTTP/1.1 200 OK");
      elem2 = appElem(elem1, "prop", null, null);

  String sProp;
  Enumeration en = enumPropNames();
  while (en.hasMoreElements()) {
    sProp = (String) en.nextElement();
    respOneProp(elem2, sProp, null, probe);
  }
}

//----------------------------------------------------------------------

private void respProp
( Probe probe
) throws ServletException, IOException
{
  Element elem0, elem1, elem2, elem3, elem1n, elem2n;
  elem0  = appElem(this.xrespElem, "response", null, null);
  elem1  = appElem(elem0, "href", null, probe.sHRef);
  elem1  = appElem(elem0, "propstat", null, null);
  elem2  = appElem(elem1, "status", null, "HTTP/1.1 200 OK");
  elem2  = appElem(elem1, "prop", null, null);
  elem2n = null;

  Element child;
  String sProp;
  Namespace ns;
  int n;
  Iterator iter = this.xreqList.iterator();
  while (iter.hasNext()) {
    child = (Element)iter.next();
    sProp = child.getName();
    ns = borrowNS(child);
    n = respOneProp(elem2, sProp, ns, probe);
    if (n <= 0) {
      if (elem2n == null) {
        elem1n = appElem(elem0,  "propstat", null, null);
        elem2n = appElem(elem1n, "status", null, "HTTP/1.1 404 Not found");
        elem2n = appElem(elem1n, "prop", null, null);
      }
      appElem(elem2n, sProp, ns, null);
    }
  }
}

//----------------------------------------------------------------------

private int respOneProp
( Element   parent
, String    sProp
, Namespace ns
, Probe     probe
)
{
  Element elem;
  switch (mapProp(sProp)) {

    case PROP_getcontentlength:
      if (probe.iLayer != LAYER_OBJECT) break;
      { NUMBER n = queryObjectLength(probe);
        appElem(parent, "getcontentlength", ns, (n == null ? "0" : n.stringValue()));
      }
      return 1;

    case PROP_getcontentlanguage:
      appElem(parent, "getcontentlanguage", ns, "us english");
      return 1;

    case PROP_getcontenttype:
      if (probe.iLayer != LAYER_OBJECT) break;
      appElem(parent, "getcontenttype", ns, "text/plain");
      return 1;

    case PROP_getetag:
      if (probe.iLayer != LAYER_OBJECT) break;
      if (probe.nObjID == null) break;
      appElem(parent, "getetag", ns, calcETag(probe));
      return 1;

    case PROP_resourcetype:
      elem = appElem(parent, "resourcetype", ns, null);
      if (probe.bCollection) { appElem(elem, "collection", ns, null); }
      return 1;

    case PROP_displayname:
      appElem(parent, "displayname", ns, probe.sName);
      return 1;

    case PROP_lockdiscovery:
      respLockDiscovery(parent, ns, probe);
      return 1;

    case PROP_getlastmodified:
      if (probe.iLayer == LAYER_ROOT) break;
      { DATE d = probe.dUpd;
        if (d == null) d = probe.dCre;
        if (d == null) break;
        elem = appElem(parent, "getlastmodified", ns, fmtGMT(d));
        elem.setAttribute("dt", "dateTime.rfc1123", this.xrespNSb);
      }
      return 1;

    case PROP_creationdate:
      if (probe.iLayer == LAYER_ROOT) break;
      if (probe.dCre == null) break;
      appElem(parent, "creationdate", ns, fmtISO8601(probe.dCre));
      return 1;

    case PROP_name:
      appElem(parent, "name", ns, probe.sName);
      return 1;

    case PROP_parentname:
      if (probe.sParentName == null) break;
      if (probe.sParentName.length() == 0) break;
      appElem(parent, "parentname", ns, probe.sParentName);
      return 1;

    case PROP_href:
      appElem(parent, "href", ns, probe.sHRef);
      return 1;

    /*
    case PROP_lastaccessed:
      provideDUpd(rNode);  if (rNode.dUpd == null) break;
      elem = appElem(parent, "lastaccessed", ns, fmtGMT(rNode.dUpd));
      elem.setAttribute("dt", "dateTime.rfc1123", this.xrespNSb);
      return 1;
    */

    case PROP_ishidden:
      appElem(parent, "ishidden", ns,  "0");
      return 1;

    case PROP_iscollection:
      appElem(parent, "iscollection", ns, (probe.bCollection ? "1" : "0"));
      return 1;

    case PROP_isreadonly:
      appElem(parent, "isreadonly", ns,
        (probe.iObjtype == OBJTYPE_ERROR ? "1" : "0"));
      return 1;

    case PROP_isroot:
      appElem(parent, "isroot", ns,
        (probe.iLayer == LAYER_ROOT ? "1" : "0"));
      return 1;

    case PROP_isstructureddocument:
      appElem(parent, "isstructureddocument", ns, "0");
      return 1;

    case PROP_supportedlock:
      respSupportedLock(parent, ns);
      return 1;

  }
  return 0;
}

//----------------------------------------------------------------------
// Queries:
//----------------------------------------------------------------------

private static final String stmt_enum_errors =
  "select o.OBJECT_NAME OBJECT_NAME, o.OBJECT_ID OBJECT_ID" +
  ", min(o.LAST_DDL_TIME) CREATED from SYS.ALL_OBJECTS o, SYS.ALL_ERRORS e" +
  " where o.OWNER = upper(?) and o.STATUS = 'INVALID'" +
  "   and o.OBJECT_NAME = e.NAME and o.OWNER = e.OWNER" +
  " group by o.OBJECT_NAME, o.OBJECT_ID";

//----------------------------------------------------------------------

private OracleResultSet enumErrors (String sSchema)
{
  OracleResultSet rs = null;
  try {
    this.ps =
      (OraclePreparedStatement)m_conn.prepareStatement(stmt_enum_errors);
    this.ps.setString(1, sSchema);
    rs = (OracleResultSet) this.ps.executeQuery();
  }
  catch (SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "enumErrors/SQL: ", e.getMessage());
  }
  return rs;
}

//----------------------------------------------------------------------

private static final String stmt_enum_objects =
  "select * from SYS.ALL_OBJECTS where OWNER = upper(?) and OBJECT_TYPE = ?";

//----------------------------------------------------------------------

private OracleResultSet enumObjects (String sSchema, int iObjtype)
{
  OracleResultSet rs = null;
  try {
    this.ps =
      (OraclePreparedStatement)m_conn.prepareStatement(stmt_enum_objects);
    this.ps.setString(1, sSchema);
    this.ps.setString(2, decodeOracleObjtype(iObjtype));
    rs = (OracleResultSet) this.ps.executeQuery();
  }
  catch (SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "enumObjects/SQL: ", e.getMessage());
  }
  return rs;
}

//----------------------------------------------------------------------

private static final String stmt_error_length =
  "begin select sum(length(TEXT) + length(to_char(SEQUENCE)) + length(to_char(LINE)) + length(to_char(POSITION)) + 7)" +
  " into ? from SYS.ALL_ERRORS where OWNER = upper(?) and NAME = ?; end;";

private static final String stmt_view_length =
  "begin select TEXT_LENGTH + length(VIEW_NAME) + 29 into ? from SYS.ALL_VIEWS" +
  " where OWNER = upper(?) and VIEW_NAME = ?; end;";

private static final String stmt_source_length =
  "begin select sum(length(TEXT)) + 20 into ? from SYS.ALL_SOURCE" +
  " where OWNER = upper(?) and TYPE = ? and NAME = ?; end;";

//----------------------------------------------------------------------

protected NUMBER queryObjectLength (Probe probe)
{
  NUMBER n = null;
  String sObjName = stripName(probe.sName);
  if (probe.iObjtype == OBJTYPE_JAVA_SOURCE) {}
  else {
    sObjName = sObjName.toUpperCase();
  }
  try {
    OracleCallableStatement cs = null;
    if (probe.iObjtype == OBJTYPE_ERROR) {
      cs = (OracleCallableStatement)m_conn.prepareCall(stmt_error_length);
      cs.registerOutParameter(1, OracleTypes.NUMBER);
      cs.setString(2, probe.sSchema);
      cs.setString(3, sObjName);
      cs.execute();
      n = cs.getNUMBER(1);  if (cs.wasNull()) n = null;
      cs.close();
    }
    else if (probe.iObjtype == OBJTYPE_VIEW) {
      cs = (OracleCallableStatement)m_conn.prepareCall(stmt_view_length);
      cs.registerOutParameter(1, OracleTypes.NUMBER);
      cs.setString(2, probe.sSchema);
      cs.setString(3, sObjName);
      cs.execute();
      n = cs.getNUMBER(1);  if (cs.wasNull()) n = null;
      cs.close();
    }
    else {
      cs = (OracleCallableStatement)m_conn.prepareCall(stmt_source_length);
      cs.registerOutParameter(1, OracleTypes.NUMBER);
      cs.setString(2, probe.sSchema);
      cs.setString(3, decodeOracleObjtype(probe.iObjtype));
      cs.setString(4, sObjName);
      cs.execute();
      n = cs.getNUMBER(1);  if (cs.wasNull()) n = null;
      cs.close();
    }
  }
  catch (SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "queryObjectLength/SQL: ", e.getMessage());
  }
  return n;
}

//----------------------------------------------------------------------
// Encoding:
//----------------------------------------------------------------------

protected String escape (String s)
{
  return escape(s, this.effCharset);
}

protected String escape (String s, String enc)
{
  if (s == null) return "";
  byte[] au;
  try { au = s.getBytes(enc); }
  catch (UnsupportedEncodingException uee) { return s; }
  int n = au.length;
  StringBuffer sb = new StringBuffer(3*n);
  int u, x;
  for (int i = 0; i < n; i++) {
    u = ((int)(au[i])) & 0x0FF;
    if ((u > 127) || (u <= 32) || (u == 37)) {
      sb.append('%');
      x = (u >> 4) & 0x0F;
      sb.append((char)(x < 10 ? x+48 : x+55));
      x = u & 0x0F;
      sb.append((char)(x < 10 ? x+48 : x+55));
    }
    else if (u == 32) {
      sb.append('+');
    }
    else {
      sb.append((char)u);
    }
  }
  return sb.toString();
}

//----------------------------------------------------------------------
//----------------------------------------------------------------------
//----------------------------------------------------------------------
//----------------------------------------------------------------------
//----------------------------------------------------------------------
//----------------------------------------------------------------------
//----------------------------------------------------------------------
//----------------------------------------------------------------------
//----------------------------------------------------------------------
/*
private int respOneProp
( Element   parent
, String    sProp
, Namespace ns
)
{
  Element elem;
  switch (mapProp(sProp)) {

    case PROP_getcontentlength:
      elem = appElem(parent, "getcontentlength", ns,
               rNode.nSize == null ? "0" : rNode.nSize.stringValue());
      elem.setAttribute("dt", "int", this.xrespNSb);
      return 1;

    case PROP_getcontentlanguage:
      appElem(parent, "getcontentlanguage", ns, "us english");
      return 1;

    case PROP_getcontenttype:
      if (rNode.sMIMEType == null) break;
      appElem(parent, "getcontenttype", ns, rNode.sMIMEType);
      return 1;

    case PROP_getetag:
      if (rNode.sETag == null) break;
      appElem(parent, "getetag", ns, rNode.sETag);
      return 1;

    case PROP_resourcetype:
      elem = appElem(parent, "resourcetype", ns, null);
      if (rNode.sKind.equals("DIR")) appElem(elem, "collection", ns, null);
      return 1;

    case PROP_displayname:
      appElem(parent, "displayname", ns, rNode.sName);
      return 1;

    case PROP_lockdiscovery:
      respLockDiscovery(parent, ns, rNode.nEnt);
      return 1;

    case PROP_getlastmodified:
      if (rNode.dUpd == null) break;
      elem = appElem(parent, "getlastmodified", ns, fmtGMT(rNode.dUpd));
      elem.setAttribute("dt", "dateTime.rfc1123", this.xrespNSb);
      return 1;

    case PROP_creationdate:
      if (rNode.dIns == null) break;
      appElem(parent, "creationdate", ns, fmtISO8601(rNode.dIns));
      return 1;

    case PROP_name:
      appElem(parent, "name", ns, rNode.sName);
      return 1;

    case PROP_parentname:
      if (rParent == null) break;
      appElem(parent, "parentname", ns, rParent.sName);
      return 1;

    case PROP_href:
      appElem(parent, "href", ns, makeHRef(rNode.sPath));
      return 1;

    case PROP_contentclass:
      if (rNode.sMIMEType == null) break;
      appElem(parent, "contentclass", ns, rNode.sMIMEType);
      return 1;

    case PROP_ishidden:
      appElem(parent, "ishidden", ns,  "0");
      return 1;

    case PROP_iscollection:
      appElem(parent, "iscollection", ns,
        (rNode.sKind.equals("DIR") ? "1" : "0"));
      return 1;

    case PROP_isreadonly:
      appElem(parent, "isreadonly", ns,
        (rNode.nDir == null ? "1" : "0"));
      return 1;

    case PROP_isroot:
      appElem(parent, "isroot", ns,
        (rNode.nDir == null ? "1" : "0"));
      return 1;

    case PROP_supportedlock:
      respSupportedLock(parent, ns);
      return 1;

  }
  return 0;
}
*/
    /*
    case PROP_lastaccessed:
      provideDUpd(rNode);  if (rNode.dUpd == null) break;
      elem = appElem(parent, "lastaccessed", ns, fmtGMT(rNode.dUpd));
      elem.setAttribute("dt", "dateTime.rfc1123", this.xrespNSb);
      return 1;
    */
//----------------------------------------------------------------------

private void respSupportedLock
( Element    parent
, Namespace  ns
)
{
  Element elem1 = appElem(parent, "supportedlock", ns, null);
  Element elem2 = appElem(elem1,  "lockentry", ns, null);
  Element elem3 = appElem(elem2,  "lockscope", ns, null);
                  appElem(elem3,  "exclusive", ns, null);
          elem3 = appElem(elem2,  "locktype", ns, null);
                  appElem(elem3,  "write", ns, null);
}

/*
    <D:supportedlock>
         <D:lockentry>
              <D:lockscope><D:exclusive/></D:lockscope>
              <D:locktype><D:write/></D:locktype>
         </D:lockentry>
         <D:lockentry>
              <D:lockscope><D:shared/></D:lockscope>
              <D:locktype><D:write/></D:locktype>
         </D:lockentry>
    </D:supportedlock>
*/
//----------------------------------------------------------------------
/*
private static final String stmt_all_schemas =
  "select au.USERNAME USERNAME, au.USER_ID USER_ID, au.CREATED CREATED, ao.LAST_DDL_TIME LAST_DDL_TIME" +
  " from SYS.ALL_USERS au, ( select OWNER, max(LAST_DDL_TIME) LAST_DDL_TIME from SYS.ALL_OBJECTS group by OWNER ) ao" +
  " where au.USERNAME = ao.OWNER";
*/
//----------------------------------------------------------------------
/*
private OracleResultSet enumSchemas ()
{
  OracleResultSet rs = null;
  try {
    this.ps =
      (OraclePreparedStatement)m_conn.prepareStatement(stmt_all_schemas);
    rs = (OracleResultSet) this.ps.executeQuery();
  }
  catch (SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "enumSchemas/SQL: ", e.getMessage());
  }
  return rs;
}
*/
//----------------------------------------------------------------------

} // class PROPFIND

//----------------------------------------------------------------------
