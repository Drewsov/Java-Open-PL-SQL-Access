//----------------------------------------------------------------------
// PERSES.LOCK.java
//----------------------------------------------------------------------

package com.nnetworks.jopa.PERSES;

import com.nnetworks.jopa.PERSES.*;
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
import java.util.*;

//----------------------------------------------------------------------

public class LOCK extends PERSES
implements JOPAMethod
{

//----------------------------------------------------------------------

public void service ()
throws ServletException, IOException
{
  try {
    establishConnection();
    if (authenticate() > 0) {
      reqURI();
      if (this.request.getContentLength() > 0) {
        reqDepth(0, Integer.MAX_VALUE);
        if (reqXMLContent()) {
          operLOCK
          ( reqLockOwner("SYSTEM")
          , reqTimeout()
          , (this.iDepth == 0 ? '0' : 'I')
          );
        }
        else {
          respStatus(412, "Precondition Failed");
        }
      }
      else {
        String sToken = reqLockToken();
        if (sToken == null) {
          respStatus(412, "Lock-Token header must be specified");
        }
        else {
          operRELOCK
          ( sToken
          , reqTimeout()
          );
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

private void operLOCK
( String sOwner
, int    iTimeout
, char   cDepth
)
{
  Probe probe = locateProbe();
  if (this.iStatus == 200) {
    if (probe.nObjID == null) {
      respStatus(422, "Unprocessible entity");
      return;
    }
  }
  else {
    respStatus(this.iStatus, this.sStatus);
    return;
  }
  openLocks();
  this.locks.addLock
  ( probe.sSchema
  , genLockToken(probe)
  , calcETag(probe)
  , sOwner
  , cDepth
  , iTimeout
  );
  cleanLocks();
  respLocked(probe);
}

//----------------------------------------------------------------------

private void operRELOCK
( String sToken
, int    iTimeout
)
{
  Probe probe = locateProbe();
  if (this.iStatus == 200) {
    if (probe.nObjID == null) {
      respStatus(422, "Unprocessible entity");
      return;
    }
  }
  else {
    respStatus(this.iStatus, this.sStatus);
    return;
  }

  openLocks();
  if (iTimeout > 0) {
    this.locks.refreshLock
    ( probe.sSchema
    , sToken
    , iTimeout
    );
  }
  else {
    this.locks.refreshLock
    ( probe.sSchema
    , sToken
    );
  }
  respLocked(probe);

}

//----------------------------------------------------------------------
// TimeOut  = "Timeout" ":" 1#TimeType
// TimeType = ("Infinite" | "Second-" 1*digits | "Extend" field-value)

private int reqTimeout ()
{
  String sTimeType;
  String sTimeout = this.request.getHeader("Timeout");
  if (sTimeout != null) {
    StringTokenizer st = new StringTokenizer(sTimeout, ",");
    while (st.hasMoreTokens()) {
      sTimeType = st.nextToken().trim().toLowerCase();
      if (sTimeType.equals("infinite")) {
        return 0;  //++ new NUMBER(0);  new NUMBER(86400); NUMBER(Integer.MAX_VALUE);
      }
      else if (sTimeType.startsWith("second-")) {
        String s = sTimeType.substring(7).trim();
        try { return Integer.parseInt(s, 10); }
        catch (NumberFormatException e) {}
      }
    }
  }
  return 0;
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
              if (sOwner.length() > 0) return sOwner;
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
( Probe  probe
)
{
  respStatus(200, "OK");
  respWebDAV(2);
  respDoc("prop");
  respLockDiscovery
  ( this.xrespElem
  , borrowNS(this.xreqElem)
  , probe
  );
  respXMLContent("UTF-8");
}

//----------------------------------------------------------------------

private void respLockToken (String sLockToken)
{
  StringBuffer sb = new StringBuffer(64);
  sb.append('<');
  sb.append(LOCK_TOKEN_SCHEMA);
  sb.append(sLockToken);
  sb.append('>');
  respHeader("Lock-Token", sb.toString());
}

//----------------------------------------------------------------------

private String genLockToken (Probe probe)
{
  String s = Long.toString(System.currentTimeMillis());
  StringBuffer sb = new StringBuffer(64);
  sb.append(Integer.toString(probe.iObjtype));
  if (probe.nObjID != null) sb.append(probe.nObjID.stringValue());
  sb.append(s);
  sb.append(Integer.toString(System.identityHashCode(probe)));
  while (sb.length() < 36) { sb.append(s); }
  //
  sb.insert(4,  '-');
  sb.insert(9,  '-');
  sb.insert(14, '-');
  sb.insert(27, ':');
  if (sb.length() > 40) sb.setLength(40);
  return sb.toString();
}

//----------------------------------------------------------------------

} // class LOCK

//----------------------------------------------------------------------
