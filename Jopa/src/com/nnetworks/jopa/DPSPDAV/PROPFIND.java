//----------------------------------------------------------------------
// jopa.DPSPDAV.PROPFIND.java
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

public class PROPFIND extends DPSPDAV
implements JOPAMethod
{

//----------------------------------------------------------------------

private static final int
  PROPFIND_PROPNAME = 0,
  PROPFIND_ALLPROP  = 1,
  PROPFIND_PROP     = 2;

//----------------------------------------------------------------------

public void service ()
throws ServletException, IOException
{
  try {
    establishConnection();
    if (authenticate(1) < 0) return;
    reqURI();
    reqDepth(0, 0);
    operPROPFIND(this.sReqPath);
  }
  catch (JOPAException e) {
    respFatal(500, e);
  }
  finally {
    releaseConnection();
  }
}

//----------------------------------------------------------------------

private void operPROPFIND
( String sPath
) throws ServletException, IOException
{
  DAVNode rParent = null;
  DAVNode rNode = locateNode(sPath, 1);
  if (rNode == null) {
    respStatus
    ( this.iStatus == 0 ? 404 : this.iStatus
    , this.cStatus.stringValue()
    );
    return;
  }

  int iMode = reqPropfindMode();
  if ((iMode != PROPFIND_PROPNAME) && (rNode.nDir != null)) {
    rParent = obtainNode(rNode.nDir, 1);
  }
    shell.log(4, "requestCharset/responseCharset: ", this.requestCharset, " / ", this.responseCharset);
    shell.log(4, "serverCharset/effCharset: ", this.serverCharset, " / ", this.effCharset);

  respStatus(207, "Multi-Status");
  respWebDAV(4);
  respDoc("multistatus");
  switch (iMode) {
    case PROPFIND_PROPNAME:  respPropname(0, rNode);          break;
    case PROPFIND_ALLPROP:   respAllprop(0, rNode, rParent);  break;
    case PROPFIND_PROP:      respProp(0, rNode, rParent);     break;
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

private void respPropname
( int     iLevel
, DAVNode rNode
) throws ServletException, IOException
{
  if (iLevel >= this.iTop) {
    Element elem0, elem1, elem2, elem3;
    elem0 = appElem(this.xrespElem, "response", null, null);
    elem1 = appElem(elem0, "href", null, makeHRef(rNode.sPath));
    elem1 = appElem(elem0, "propstat", null, null);
    elem2 = appElem(elem1, "status", null, "HTTP/1.1 200 OK");
    elem2 = appElem(elem1, "prop", null, null);

    String sProp;
    Enumeration en = enumPropNames();
    while (en.hasMoreElements()) {
      sProp = (String) en.nextElement();
      appElem(elem2, sProp, null, null);
    }
  }

  // Deep-quest:
  if (iLevel < this.iDepth) {
    if (rNode.sKind.equals("DIR")) {
      DAVNode rSubNode = new DAVNode();
      try {
        OracleResultSet rs = enumSubNodes(rNode.nEnt, 1);
        if (rs != null) {
          while (rs.next()) {
            if (rSubNode.materialize(rs)) {
              respPropname
              ( iLevel + 1
              , rSubNode
              );
            }
          }
          rs.close();
        }
      }
      catch (SQLException e) {}
    }
  }
}

//----------------------------------------------------------------------

private void respAllprop
( int     iLevel
, DAVNode rNode
, DAVNode rParent
) throws ServletException, IOException
{
  if (iLevel >= this.iTop) {
    Element elem0, elem1, elem2, elem3;
    elem0 = appElem(this.xrespElem, "response", null, null);
    elem1 = appElem(elem0, "href", null, makeHRef(rNode.sPath));
      elem1 = appElem(elem0, "propstat", null, null);
        elem2 = appElem(elem1, "status", null, "HTTP/1.1 200 OK");
        elem2 = appElem(elem1, "prop", null, null);

    String sProp;
    Enumeration en = enumPropNames();
    while (en.hasMoreElements()) {
      sProp = (String) en.nextElement();
      respOneProp(elem2, sProp, null, rNode, rParent);
    }
  }

  // Deep-quest:
  if (iLevel < this.iDepth) {
    if (rNode.sKind.equals("DIR")) {
      DAVNode rSubNode = new DAVNode();
      try {
        OracleResultSet rs = enumSubNodes(rNode.nEnt, 1);
        if (rs != null) {
          while (rs.next()) {
            if (rSubNode.materialize(rs)) {
              respAllprop
              ( iLevel + 1
              , rSubNode
              , rNode
              );
            }
          }
          rs.close();
        }
      }
      catch (SQLException e) {}
    }
  }
}

//----------------------------------------------------------------------

private void respProp
( int     iLevel
, DAVNode rNode
, DAVNode rParent
) throws ServletException, IOException
{
  if (iLevel >= this.iTop) {
    Element elem0, elem1, elem2, elem3, elem1n, elem2n;
    elem0 = appElem(this.xrespElem, "response", null, null);
    elem1 = appElem(elem0, "href", null, makeHRef(rNode.sPath));
    elem1 = appElem(elem0, "propstat", null, null);
    elem2 = appElem(elem1, "status", null, "HTTP/1.1 200 OK");
    elem2 = appElem(elem1, "prop", null, null);

    elem2n = null;

    Element child;
    String sProp;
    Namespace ns;
    int n;
    Iterator iter = this.xreqList.iterator();
    while (iter.hasNext()) {
      child = (Element)iter.next();
      sProp = child.getName();
      //ns = child.getNamespaceURI().equals("DAV:") ? null :
      //     child.getNamespace();
      ns = borrowNS(child);
      n = respOneProp(elem2, sProp, ns, rNode, rParent);
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

  // Deep-quest:
  if (iLevel < this.iDepth) {
    if (rNode.sKind.equals("DIR")) {
      DAVNode rSubNode = new DAVNode();
      try {
        OracleResultSet rs = enumSubNodes(rNode.nEnt, 1);
        if (rs != null) {
          while (rs.next()) {
            if (rSubNode.materialize(rs)) {
              respProp
              ( iLevel + 1
              , rSubNode
              , rNode
              );
            }
          }
          rs.close();
        }
      }
      catch (SQLException e) {}
    }
  }
}

//----------------------------------------------------------------------
/*
  + PROP_getcontentlength     =  1,
  + PROP_getcontentlanguage   =  2,
  + PROP_getcontenttype       =  3,
  + PROP_getetag              =  4,
  + PROP_resourcetype         =  5,
  + PROP_displayname          =  6,
  + PROP_lockdiscovery        =  7,
  + PROP_getlastmodified      =  8,
  + PROP_creationdate         =  9,
  + PROP_name                 = 10,
  + PROP_parentname           = 11,
  + PROP_href                 = 12,
  ? PROP_contentclass         = 13,
    PROP_lastaccessed         = 14,
    PROP_defaultdocument      = 15,
  + PROP_ishidden             = 16,
  + PROP_iscollection         = 17,
  + PROP_isreadonly           = 18,
  + PROP_isroot               = 19,
    PROP_isstructureddocument = 20,
  + PROP_supportedlock        = 21;
*/

private int respOneProp
( Element   parent
, String    sProp
, Namespace ns
, DAVNode   rNode
, DAVNode   rParent
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
    if (u == 32) {
      sb.append('+');
    }
    else if ((u > 127) || (u <= 32) || (u == 37)) {
      sb.append('%');
      x = (u >> 4) & 0x0F;
      sb.append((char)(x < 10 ? x+48 : x+55));
      x = u & 0x0F;
      sb.append((char)(x < 10 ? x+48 : x+55));
    }
    else {
      sb.append((char)u);
    }
  }
  return sb.toString();
}

//----------------------------------------------------------------------

private String makeHRef
( String sPath
)
{
  return makeHRef(sPath, this.effCharset);
}

private String makeHRef
( String sPath
, String enc
)
{
  StringBuffer sb = new StringBuffer(256);
  sb.append(this.sReqBase);
  if (!sPath.startsWith("/")) sb.append('/');
  int n = sPath.length();
  int i = 0;
  int j = 0;
  while (i < n) {
    j = sPath.indexOf('/', i);
    if (j < 0) break;
    if (j > i) sb.append( escape(sPath.substring(i, j), enc) );
    sb.append('/');
    i = j + 1;
  }
  if (i < n) sb.append( escape(sPath.substring(i), enc) );
  //
  i = sb.indexOf("+", 0);
  while (i >= 0) {
    sb.replace(i, i+1, "%20");
    i = sb.indexOf("+", i+3);
  }
  return sb.toString();
}

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.4 $";
} // class PROPFIND

//----------------------------------------------------------------------
