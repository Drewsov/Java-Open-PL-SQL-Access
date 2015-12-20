//----------------------------------------------------------------------
// UDAV.PROPFIND.java
//
// $Id: PROPFIND.java,v 1.12 2005/04/26 16:49:47 Bob Exp $
//
//----------------------------------------------------------------------

package com.nnetworks.jopa.UDAV;

import com.nnetworks.jopa.UDAV.*;
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

public class PROPFIND extends UDAV
implements JOPAMethod
{


public PROPFIND()
{
  this.accessModeRequired = ACCESS_MODE_READONLY;
}

//----------------------------------------------------------------------

public void service ()
throws ServletException, IOException
{
  checkLocalBase();
  if (authenticate() > 0) {

    reqURI();
    reqDepth(0, 0);

    File file = locateResource(this.sReqPath);
    if (file == null) {
      respStatus(400, HTTP_BAD_REQUEST);
      return;
    }
    if (!file.exists()) {
      respStatus(404, HTTP_NOT_FOUND);
      return;
    }

    respStatus(207, HTTP_MULTI_STATUS);
    respWebDAV(4);
    int iMode = reqPropfindMode();
    respDoc("multistatus");
    switch (iMode) {
      case PROPFIND_PROPNAME:  respPropname(0, file, this.sReqPath);  break;
      case PROPFIND_ALLPROP:   respAllprop(0, file, this.sReqPath);   break;
      case PROPFIND_PROP:      respProp(0, file, this.sReqPath);      break;
    }
    respXMLContent("UTF-8");
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
      if (this.xreqElem.getName().equalsIgnoreCase("propfind")) {
        Element child = (Element)(this.xreqElem.getChildren().get(0));
        if (child != null) {
          String sName = child.getName();
          if (sName.equalsIgnoreCase("allprop"))  return PROPFIND_ALLPROP;
          if (sName.equalsIgnoreCase("propname")) return PROPFIND_PROPNAME;
          if (sName.equalsIgnoreCase("prop")) {
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
( int    iLevel
, File   file
, String sPath
) throws ServletException, IOException
{
  if (iLevel >= this.iTop) {
      shell.log(3,"respPropname.iLevel: "+iLevel);
      shell.log(3,"respPropname.this.iTop: "+this.iTop);
      shell.log(3,"respPropname.this.iDepth: "+this.iDepth);
      
    Element elem0, elem1, elem2, elem3;
    elem0 = appElem(this.xrespElem, "response", null, null);
    elem1 = appElem(elem0, "href", null, makeRelHRef(sPath,file));
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

  // deep-quest:
  if (iLevel < this.iDepth) {
    if (file.isDirectory()) {
      File[] files = file.listFiles();
      int iLevel1 = iLevel + 1;
      for (int i = 0; i < files.length; i++) {
        respPropname(iLevel1, files[i], sPath + '/' + files[i].getName());
      }
    }
  }
}

//----------------------------------------------------------------------

private void respAllprop
( int    iLevel
, File   file
, String sPath
) throws ServletException, IOException
{
  if (iLevel >= this.iTop) {

            shell.log(3,"respAllprop.iLevel: "+iLevel);
            shell.log(3,"respAllprop.this.iTop: "+this.iTop);
            shell.log(3,"respPropname.this.iDepth: "+this.iDepth);


    Element elem0, elem1, elem2, elem3;
    elem0 = appElem(this.xrespElem, "response", null, null);
    elem1 = appElem(elem0, "href", null, makeRelHRef(sPath,file));
    elem1 = appElem(elem0, "propstat", null, null);
    elem2 = appElem(elem1, "status", null, "HTTP/1.1 200 OK");
    elem2 = appElem(elem1, "prop", null, null);

    String sProp;
    Enumeration en = enumPropNames();
    while (en.hasMoreElements()) {
      sProp = (String) en.nextElement();
      respPropSwitch(elem2, sProp, null, file, sPath);
    }
  }

  // deep-quest:
  if (iLevel < this.iDepth) {
    if (file.isDirectory()) {
      File[] files = file.listFiles();
      int iLevel1 = iLevel + 1;
      for (int i = 0; i < files.length; i++) {
        respAllprop(iLevel1, files[i], sPath + '/' + files[i].getName());
      }
    }
  }
}

//----------------------------------------------------------------------

private void respProp
( int     iLevel
, File   file
, String sPath
) throws ServletException, IOException
{
  if (iLevel >= this.iTop) {

            shell.log(3,"-------------------------------------");
            shell.log(3,"=> respProp.iLevel:"+iLevel+":"+sPath);
            shell.log(3,"=> respProp.this.iTop:"+this.iTop);
            shell.log(3,"=> respProp.this.iDepth:"+this.iDepth);
            shell.log(3,"-------------------------------------");
            
    Element elem0, elem1, elem2, elem3, elem1n, elem2n;
    elem0 = appElem(this.xrespElem, "response", null, null);
    elem1 = appElem(elem0, "href", null, makeRelHRef(sPath,file));
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
      ns = borrowNS(child);
      n = respPropSwitch(elem2, sProp, ns, file, sPath);
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

  // deep-quest:
  if (iLevel < this.iDepth) {
    if (file.isDirectory()) {
      File[] files = file.listFiles();
      int iLevel1 = iLevel + 1;
      for (int i = 0; i < files.length; i++) {
        respProp(iLevel1, files[i], sPath + '/' + files[i].getName());
      }
    }
  }
}

//----------------------------------------------------------------------

private int respPropSwitch
( Element   parent
, String    sProp
, Namespace ns
, File      file
, String    sPath
)
{
  Element elem;
  switch (mapProp(sProp)) {

    case PROP_getcontentlength:
      elem = appElem(parent, "getcontentlength", ns,
               Long.toString(file.length()));
      elem.setAttribute("dt", "int", this.xrespNSb);
      return 1;

    case PROP_getcontentlanguage:
      appElem(parent, "getcontentlanguage", ns, "us english");
      return 1;

    case PROP_getcontenttype:
      appElem(parent, "getcontenttype", ns, guessContentType(file.getName()));
      return 1;

    case PROP_getetag:
      appElem(parent, "getetag", ns, calcETag(file));
      return 1;

    case PROP_resourcetype:
      elem = appElem(parent, "resourcetype", ns, null);
      if (file.isDirectory()) appElem(elem, "collection", ns, null);
      return 1;

    case PROP_displayname:
      appElem(parent, "displayname", ns, file.getName());
      return 1;

    case PROP_lockdiscovery:
      respLockDiscovery(parent, ns, file);
      return 1;

    case PROP_supportedlock:
      respSupportedLock(parent, ns);
      return 1;

    case PROP_getlastmodified:
      elem = appElem(parent, "getlastmodified", ns, fmtGMT(file.lastModified()));
      elem.setAttribute("dt", "dateTime.rfc1123", this.xrespNSb);
      return 1;

    case PROP_creationdate:
      appElem(parent, "creationdate", ns, fmtISO8601(file.lastModified()));
      return 1;

    case PROP_name:
      appElem(parent, "name", ns, file.getName());
      return 1;

    case PROP_href:
      appElem(parent, "href", ns, makeRelHRef(sPath,file));
      return 1;

    case PROP_contentclass:
      appElem(parent, "contentclass", ns, guessContentType(file.getName()));
      return 1;

    case PROP_ishidden:
      appElem(parent, "ishidden", ns,  (file.isHidden() ? "1" : "0"));
      return 1;

    case PROP_iscollection:
      appElem(parent, "iscollection", ns, (file.isDirectory() ? "1" : "0"));
      return 1;

    case PROP_isreadonly:
      appElem(parent, "isreadonly", ns, (file.canWrite() ? "0" : "1"));
      return 1;

    case PROP_isroot:
      appElem(parent, "isroot", ns, (sPath.length() == 0 ? "1" : "0"));
      return 1;

    /*
    case PROP_lastaccessed:
      if (rNode.dStamp == null) break;
      elem = appElem(parent, sPref, "lastaccessed", fmtGMT(rNode.dStamp));
      elem.setAttribute("b:dt", "dateTime.rfc1123");
      return 1;
    */

    case PROP_parentname:
      appElem(parent, "parentname", ns, stripParent(sPath));
      return 1;

  }
  return 0;
}

//----------------------------------------------------------------------

private void respSupportedLock (Element parent, Namespace ns)
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


//----------------------------------------------------------------------

private String stripParent (String sPath)
{
  int i = sPath.lastIndexOf('/');
  if (i > 0) {
    return sPath.substring(0, i);
  }
  return "";
}

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.12 $";
} // class PROPFIND

//----------------------------------------------------------------------
