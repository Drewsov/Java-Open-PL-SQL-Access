//----------------------------------------------------------------------
// WEBDAV.LOCK.java
//
// $Id: LOCK.java,v 1.9 2005/04/19 12:19:53 Bob Exp $
//
//----------------------------------------------------------------------

package com.nnetworks.jopa.WEBDAV;

import com.nnetworks.jopa.WEBDAV.*;
import com.nnetworks.jopa.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.jdom.*;
import org.jdom.input.*;
import org.jdom.output.*;

import oracle.sql.*;
import oracle.jdbc.driver.*;

import java.sql.*;
import java.io.*;
import java.text.*;
import java.util.*;

//----------------------------------------------------------------------

public class LOCK extends WEBDAV
implements JOPAMethod
{

  private volatile String m_lockscope;

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
    if (iAccl < 3) {
      respStatus(403, HTTP_FORBIDDEN);
    }

    reqURI();
    //setCGIEnv();
    impersonate(iAccl);
    NUMBER nKey  = locatePath(this.sReqPath);
    if (nKey == null) {
      NUMBER nDir = locatePath(this.pi.merge(1, this.pi.iLast));
      if (nDir == null) {
        respStatus(409, HTTP_CONFLICT);
        return;
      }
      nKey = ensureNode(nDir, this.sReqName, "NUL");
      if (nKey == null) {
        respStatus(409, HTTP_CONFLICT);
        return;
      }
    }
    String sDisp = getDisp(nKey, "NUL");
    NUMBER nRef = getRefEx(nKey, sDisp);
    String sLockToken = null;
    if (this.request.getContentLength() > 0) {
      reqDepth(0, 0);
      if (reqXMLContent()) 
      {
        boolean locktypeOk = false;
        boolean lockscopeOk = false;
        // check if we support this lock type
        if ( !(this.xreqElem.getNamespaceURI()+":"+this.xreqElem.getName()).equals("DAV::lockinfo") )
        {
          shell.log(4, "Unexpected root element: "+this.xreqElem.getNamespaceURI()+":"+this.xreqElem.getName());
          respStatus(400, HTTP_BAD_REQUEST);
          return;
        }
        for ( Iterator itr = this.xreqElem.getChildren().iterator(); itr.hasNext(); )
        {
          Element e = (Element)itr.next();
          if ( e.getName().equals("locktype") && e.getNamespaceURI().equals("DAV:") )
          {
             Element locktype = e.getChild("write", Namespace.getNamespace("DAV:"));
             if (locktype == null)
             {
               for (Iterator i2 = e.getChildren().iterator(); i2.hasNext(); )
               {
                 Element bad = (Element)i2.next();
                 shell.log(4, "Unexpected locktype element: "+bad.getNamespaceURI()+":"+bad.getName());
               }
               respStatus(400, HTTP_BAD_REQUEST);
               return;
             }
             else
               locktypeOk = true;
          }
          if ( e.getName().equals("lockscope") && e.getNamespaceURI().equals("DAV:") )
          {
             Element exclusive = e.getChild("exclusive", Namespace.getNamespace("DAV:"));
             Element shared = e.getChild("shared", Namespace.getNamespace("DAV:"));

             if ( (exclusive == null && shared == null ) ||  // neither
                  (exclusive != null && shared != null)    ) // both
             {
               for (Iterator i2 = e.getChildren().iterator(); i2.hasNext(); )
               {
                 Element bad = (Element)i2.next();
                 shell.log(4, "Unexpected lockscope element: "+bad.getNamespaceURI()+":"+bad.getName());
               }
               respStatus(400, HTTP_BAD_REQUEST);
               return;
             }
             else
             {
               lockscopeOk = true;
               m_lockscope = shared == null ? "exclusive" : "shared";
             }
          }
        }
        if (!lockscopeOk || !locktypeOk)
        {
           respStatus(400, HTTP_BAD_REQUEST);
           return;
        }

        sLockToken = lock
                     ( sDisp
                     , nRef
                     , reqTimeout()
                     , reqLockOwner("SYSTEM")
                     , (this.iDepth == 0 ? "0" : "I")
                     );
        if (sLockToken != null) {
          respLocked(sLockToken, sDisp, nRef);
        }
        else {
          respStatus(424, HTTP_METHOD_FAILURE);
        }
      }
      else {
        respStatus(412, HTTP_PRECONDITION_FAILED);
      }
    }
    else {
      sLockToken = reqLockToken();
      if (sLockToken == null) {
        respStatus(412, HTTP_PRECONDITION_FAILED);
      }
      else {
        int n = refreshLock(sDisp, nRef, sLockToken);
        if (n >= 0) {
          respLocked(null, sDisp, nRef);
        }
        else {
          respStatus(424, HTTP_METHOD_FAILURE);
        }
      }
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
// TimeOut  = "Timeout" ":" 1#TimeType
// TimeType = ("Infinite" | "Second-" 1*digits | "Extend" field-value)

private NUMBER reqTimeout ()
{
  String sTimeType;
  String sTimeout = this.request.getHeader("Timeout");
  if (sTimeout != null) {
    StringTokenizer st = new StringTokenizer(sTimeout, ",");
    while (st.hasMoreTokens()) {
      sTimeType = st.nextToken().trim().toLowerCase();
      if (sTimeType.equals("infinite")) {
        return new NUMBER(86400);   //+++NUMBER(Integer.MAX_VALUE);
      }
      else if (sTimeType.startsWith("second-")) {
        String s = sTimeType.substring(7).trim();
        try { return new NUMBER(Integer.parseInt(s, 10)); }
        catch (NumberFormatException e) {}
      }
    }
  }
  return null;
}

//----------------------------------------------------------------------

private String reqLockOwner (String sDefOwner)
{
  if (this.xreqElem != null) {
    String sOwner;
    List list = this.xreqElem.getChildren();
    Iterator iter = list.iterator();
    while (iter.hasNext()) {
      Element child = (Element)(iter.next());
      if (child.getName().equals("owner")) {
        List cl = child.getChildren();
        if (!cl.isEmpty()) {
          Element own = (Element)(cl.get(0));
          if (own.getName().equals("href")) {
            sOwner = own.getTextTrim();
            if (sOwner != null) {
              if (sOwner.length() > 0) return "href::"+sOwner;
            }
          }
        }
        else {
          sOwner = child.getTextTrim();
          if (sOwner != null) {
            if (sOwner.length() > 0) return sOwner;
          }
        }
      }
    }
  }
  return sDefOwner;
}

//----------------------------------------------------------------------

private void respLocked
( String sLockToken
, String sDisp
, NUMBER nRef
)
{
  respStatus(200, HTTP_OK);
  respWebDAV(2);
  respLockToken(sLockToken);
  respDoc("prop");
  respLockDiscovery(this.xrespElem, sDisp, nRef, borrowNS(this.xreqElem));
  respXMLContent("UTF-8");
}

//----------------------------------------------------------------------

private void respLockToken (String sLockToken)
{
  if (sLockToken != null)
    respHeader("Lock-Token", "<"+LOCK_TOKEN_SCHEMA+sLockToken+">");
}

//----------------------------------------------------------------------

private String lock
( String sDisp
, NUMBER nRef
, NUMBER nTimeout
, String sOwner
, String sDepth
)
{
  String sLockToken = null;
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(
         "begin ? := NN$NTS_LOCK.lock_(?, ?, null, ?, ?, ?); end;");
    cs.registerOutParameter(1, OracleTypes.VARCHAR);
    cs.setString(2, sDisp);
    cs.setNUMBER(3, nRef);
    cs.setNUMBER(4, nTimeout);
    cs.setString(5, sOwner);
    cs.setString(6, sDepth);
    cs.executeUpdate();
    sLockToken = cs.getString(1);
    if (cs.wasNull()) sLockToken = null;
    cs.close();
    if (sLockToken != null) 
      m_conn.commit();
  }
  catch(SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "lock/SQL: ", e.getMessage());
  }
  return sLockToken;
}

//----------------------------------------------------------------------

private int refreshLock
( String sDisp
, NUMBER nRef
, String sLockToken
)
{
  int iRes = -1;
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(
         "begin ? := NN$NTS_LOCK.refresh_(?, ?, ?); end;");
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
    shell.log(0, "refreshLock/SQL: ", e.getMessage());
  }
  return iRes;
}

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.9 $";
} // class LOCK

//----------------------------------------------------------------------
