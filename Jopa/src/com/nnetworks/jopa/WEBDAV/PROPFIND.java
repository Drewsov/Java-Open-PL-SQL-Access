//----------------------------------------------------------------------
// WEBDAV.PROPFIND.java
//
// $Id: PROPFIND.java,v 1.7 2005/04/19 12:19:53 Bob Exp $
// $Id: PROPFIND.java,v 1.7.1 2006/04/16 12:19:53 Drew Exp $
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

//----------------------------------------------------------------------

public class PROPFIND extends WEBDAV
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
    }

    reqURI();
    reqDepth(0, 0);

    impersonate(iAccl);
    NTSNode rNode = reqNode(this.sReqPath);
    if (rNode == null) {
      respStatus(404, HTTP_NOT_FOUND);
      return;
    }

    respStatus(207, HTTP_MULTI_STATUS);
    respWebDAV(4);
    int iMode = reqPropfindMode();
    respDoc("multistatus");
    switch (iMode) {
      case PROPFIND_PROPNAME:  respPropname(0, rNode);  break;
      case PROPFIND_ALLPROP:   respAllprop(0, rNode);   break;
      case PROPFIND_PROP:      respProp(0, rNode);      break;
    }
//        respXMLContent("UTF-8"); // v 1.6.1
      respXMLContent(this.requestCharset); // v 1.7.1
  }
  catch (JOPAException e) {
    respFatal(500, e);
  }
  finally {
    releaseConnection();
  }
}

//----------------------------------------------------------------------

private static final int
  PROPFIND_PROPNAME = 0,
  PROPFIND_ALLPROP  = 1,
  PROPFIND_PROP     = 2;

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
, NTSNode rNode
) throws ServletException, IOException, JOPAException
{
  if (iLevel >= this.iTop) {
    Element elem0, elem1, elem2, elem3;
    elem0 = appElem(this.xrespElem, "response", null, null);
    elem1 = appElem(elem0, "href", null, makeHRef(rNode));
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
  //shell.log(3,"respPropname rNode.sDisp:"+rNode.sDisp);
  if (rNode.sDisp.equals("DIR") && (iLevel < this.iDepth)) {
    NTSNode rcur = new NTSNode();
    try {
      OracleResultSet rs = enumSubfolders(rNode.nKey, "\'NAME\'");
      if (rs != null) {
        while (rs.next()) {
          if (rcur.materialize(rs)) {
            respPropname(iLevel + 1, rcur);
          }
        }
        rs.close();
      }
      rs = enumItems(rNode.nKey, "\'NAME, DISP\'");
      if (rs != null) {
        while (rs.next()) {
          if (rcur.materialize(rs)) {
            respPropname(iLevel + 1, rcur);
          }
        }
        rs.close();
      }
    }
    catch (SQLException e) {}
  }
}

//----------------------------------------------------------------------

private void respAllprop
( int     iLevel
, NTSNode rNode
) throws ServletException, IOException, JOPAException
{
  if (iLevel >= this.iTop) {
    Element elem0, elem1, elem2, elem3;
    elem0 = appElem(this.xrespElem, "response", null, null);
      elem1 = appElem(elem0, "href", null, makeHRef(rNode));
      elem1 = appElem(elem0, "propstat", null, null);
        elem2 = appElem(elem1, "status", null, "HTTP/1.1 200 OK");
        elem2 = appElem(elem1, "prop", null, null);

    String sProp;
    Enumeration en = enumPropNames();
    while (en.hasMoreElements()) {
      sProp = (String) en.nextElement();
      respPropSwitch(elem2, sProp, null, rNode);
    }
  }
    //shell.log(3,"respAllprop rNode.sDisp:"+rNode.sDisp);
  if (rNode.sDisp.equals("DIR") && (iLevel < this.iDepth)) {
    NTSNode rcur = new NTSNode();
    try {
      OracleResultSet rs = enumSubfolders(rNode.nKey, "\'NAME\'");
      if (rs != null) {
        while (rs.next()) {
          if (rcur.materialize(rs)) {
            respAllprop(iLevel + 1, rcur);
          }
        }
        rs.close();
      }
      rs = enumItems(rNode.nKey, "\'NAME, DISP\'");
      if (rs != null) {
        while (rs.next()) {
          if (rcur.materialize(rs)) {
            respAllprop(iLevel + 1, rcur);
          }
        }
        rs.close();
      }
    }
    catch (SQLException e) {}
  }
}

//----------------------------------------------------------------------

private void respProp
( int     iLevel
, NTSNode rNode
) throws ServletException, IOException, JOPAException
{
  if (iLevel >= this.iTop) {
    Element elem0, elem1, elem2, elem3, elem1n, elem2n;
    elem0 = appElem(this.xrespElem, "response", null, null);
    elem1 = appElem(elem0, "href", null, makeHRef(rNode));
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

     //shell.log(3,"respProp rNode.sDisp:"+rNode.sDisp+":"+iLevel+":"+this.iDepth);
 // respPropSwitch
      n = respPropSwitch(elem2, sProp, ns, rNode);
//      

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

  if (rNode.sDisp.equals("DIR") && (iLevel < this.iDepth)) {
    NTSNode rcur = new NTSNode();
    try {
      OracleResultSet rs = enumSubfolders(rNode.nKey, "\'NAME\'");
      if (rs != null) {
        while (rs.next()) {
          if (rcur.materialize(rs)) {
            respProp(iLevel+1, rcur);
          }
        }
        rs.close();
      }
      rs = enumItems(rNode.nKey, "\'NAME, DISP\'");
      if (rs != null) {
        while (rs.next()) {
          if (rcur.materialize(rs)) {
            respProp(iLevel+1, rcur);
          }
        }
        rs.close();
      }
    }
    catch (SQLException e) {}
  }
}

//----------------------------------------------------------------------

private int respPropSwitch
( Element   parent
, String    sProp
, Namespace ns
, NTSNode   rNode
)
{
  Element elem;
  switch (mapProp(sProp)) {

    case PROP_getcontentlength:
      elem = appElem(parent, "getcontentlength", ns,
               (rNode.nSize == null ? "0" : rNode.nSize.stringValue()));
      elem.setAttribute("dt", "int", this.xrespNSb);
      return 1;

    case PROP_getcontentlanguage:
      appElem(parent, "getcontentlanguage", ns, "us english");
      return 1;

    case PROP_getcontenttype:
    // AAT 30.11.2006
     //if (!rNode.sDisp.equalsIgnoreCase("DIR")){     
     appElem(parent, "getcontenttype", ns,
              calcContentType(rNode.sDisp));
      return 1;

    case PROP_getetag:
      appElem(parent, "getetag", ns,
              calcETag(rNode.sDisp, rNode.nKey));
      return 1;

    case PROP_resourcetype:
      elem = appElem(parent, "resourcetype", ns, null);
      if (rNode.sDisp.equals("DIR")) appElem(elem, "collection", ns, null);
      return 1;

    case PROP_displayname:
       appElem(parent, "displayname", ns, rNode.sName); // v 1.6.1
      // v 1.7.1
      //try {appElem(parent, "displayname", ns,(new String(rNode.sName.getBytes("Cp1251"))));}
      //catch (UnsupportedEncodingException e) {appElem(parent, "displayname", ns, rNode.sName);}
      return 1;

    case PROP_lockdiscovery:
      respLockDiscovery(parent, rNode.sDisp,
          (rNode.sDisp.equals("DIR") ? rNode.nKey :
           rNode.sDisp.equals("NUL") ? rNode.nKey :
           rNode.nRef == null ? rNode.nKey : rNode.nRef), ns);
      return 1;

    case PROP_supportedlock:
      respSupportedLock(parent, ns);
      return 1;

    case PROP_getlastmodified:
      if (rNode.dStamp == null) break;
      elem = appElem(parent, "getlastmodified", ns, fmtGMT(rNode.dStamp.timestampValue().getTime()));
      elem.setAttribute("dt", "dateTime.rfc1123", this.xrespNSb);
      return 1;

    case PROP_creationdate:
      if (rNode.dStamp == null) break;
      appElem(parent, "creationdate", ns, fmtISO8601(rNode.dStamp.timestampValue().getTime()));
      return 1;

    case PROP_name:
      appElem(parent, "name", ns, rNode.sName);
      return 1;

    case PROP_href:
      appElem(parent, "href", ns, makeHRef(rNode));
      return 1;

    case PROP_contentclass:
      appElem(parent, "contentclass", ns,
              calcContentType(rNode.sDisp));
      return 1;

    case PROP_ishidden:
      appElem(parent, "ishidden", ns,  "0");
      return 1;

    case PROP_iscollection:
      appElem(parent, "iscollection", ns,
              (rNode.sDisp.equals("DIR") ? "1" : "0"));
      return 1;

    case PROP_isreadonly:
      appElem(parent, "isreadonly", ns, "0");
      return 1;

    case PROP_isroot:
      appElem(parent, "isroot", ns,
              (rNode.sPath.equals("/") ? "1" : "0"));
      return 1;

    /*
    case PROP_lastaccessed:
      if (rNode.dStamp == null) break;
      elem = appElem(parent, sPref, "lastaccessed", fmtGMT(rNode.dStamp.timestampValue().getTime()));
      elem.setAttribute("b:dt", "dateTime.rfc1123");
      return 1;
    */

    case PROP_parentname:
      appElem(parent, "parentname", ns, getFolderName(rNode.nKey, "/"));
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

private String makeHRef
( NTSNode rNode
)
{
  return makeHRef(rNode, this.effCharset);
}

private String makeHRef
( NTSNode rNode
, String enc
)
{
  StringBuffer sb = new StringBuffer(256);
  sb.append(this.sReqBase);
  if (!rNode.sPath.startsWith("/")) sb.append('/');
  int n = rNode.sPath.length();
  int i = 0;
  int j = 0;
  while (i < n) {
    j = rNode.sPath.indexOf('/', i);
    if (j < 0) break;
    if (j > i) sb.append( escape(rNode.sPath.substring(i, j), enc) );
    sb.append('/');
    i = j + 1;
  }
  if (i < n) sb.append( escape(rNode.sPath.substring(i), enc) );
  //
  i = sb.indexOf("+", 0);
  while (i >= 0) {
    sb.replace(i, i+1, "%20");
    i = sb.indexOf("+", i+3);
  }
  if (!rNode.sPath.endsWith("/")&&rNode.sDisp.equalsIgnoreCase("DIR"))sb.append('/');
  return sb.toString();
}

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.7 $";
} // class PROPFIND

//----------------------------------------------------------------------
