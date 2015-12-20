//----------------------------------------------------------------------
// UDAV.LOCK.java
//
// $Id: LOCK.java,v 1.15 2005/04/26 16:20:55 Bob Exp $
//
//----------------------------------------------------------------------
//
// TODO:
//
//    - add support for locking collections (that is, iteratively lock
//      all collection elements and return multistatus response.)

package com.nnetworks.jopa.UDAV;

import com.nnetworks.jopa.UDAV.*;
import com.nnetworks.jopa.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.jdom.*;
import org.jdom.input.*;
import org.jdom.output.*;
import org.jdom.xpath.*;

import java.io.*;
import java.util.*;
import java.security.*;

//----------------------------------------------------------------------

public class LOCK extends UDAV
implements JOPAMethod
{

  private volatile String  m_newLockToken = null;
  private volatile String  m_lockscope    = null;
  private volatile String  m_lockOwner    = null;
  private volatile String  m_depth        = null;
  private volatile String  m_timeout      = null;
  private volatile boolean isNull         = false;

public LOCK()
{
  this.accessModeRequired = ACCESS_MODE_READWRITE;
}

//----------------------------------------------------------------------

public void service()
throws ServletException, IOException
{
  checkLocalBase();
  if (authenticate() > 0) {
    reqURI();
    File file = locateResource(this.sReqPath);
    if (file == null) {
      respStatus(400, HTTP_BAD_REQUEST);
      return;
    }
    // Checking any already existing locks
    if (isLocked(file)) 
    {
      respStatus(423, HTTP_LOCKED);
      return;
    }
    try
    {
      File parent = file.getParentFile();
      while (parent != null && parent.getCanonicalPath().startsWith(m_localbase)) // don't escape the local directory
      {
         if(isLocked(parent))
         {
           if (!file.isDirectory())
           {
             respStatus(423, HTTP_LOCKED);
             return;
           }
           else
           { // collection operations require multi-status responses
             respStatus(207, HTTP_MULTI_STATUS);
             respDoc("multistatus");
             Element rsp = appElem(this.xrespElem, "response",null,null);
             appElem(rsp, "status", null, "HTTP/1.1 423 Locked");
             appElem(rsp, "href", null, makeRelHRef(parent.getAbsolutePath().substring(m_localbase.length()+1),file));
             respXMLContent("UTF-8");
             return;
           }
         }
         // walk up the hierarchy and check that no parent collection is locked already
         parent = parent.getParentFile();
      }
    }
    catch(IOException e)
    {
      // could not evaluate the canonical path, the path was probably invalid
      respStatus(400, HTTP_BAD_REQUEST);
      return;
    }

    if (reportLockedChildren(file))
      return;

    // Lock checks complete

    if (!file.exists()) 
    try
    {
      file.createNewFile();
      isNull = true;
    }
    catch(Exception ioe)
    {
      // most probably failed because the owning collection doesn't exist
      shell.log(4, "Failed to create new file "+file.getAbsolutePath());
      respStatus(412, HTTP_PRECONDITION_FAILED);
      return;
    }

    // parsing the lock info and validating the request
    if (this.request.getContentLength() > 0) 
    {
      reqDepth(0, Integer.MAX_VALUE);
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

        operLOCK(file, reqLockOwner(null), reqTimeout(),
                 (this.iDepth == 0 ? '0' : 'I'));
      }
      else {
        respStatus(412, HTTP_PRECONDITION_FAILED);
      }
    }
    else 
    {
      String sToken = reqLockToken();
      shell.log(4,"About to refresh lock "+sToken);
      if (sToken != null) 
      {
        if (!operRELOCK(file, sToken, reqTimeout()))
          respStatus(412, HTTP_PRECONDITION_FAILED); // could not refresh the lock

      }
      else
        respStatus(412, HTTP_PRECONDITION_FAILED);
    }
  }
}

//----------------------------------------------------------------------

private void operLOCK
( File   file
, String sOwner
, int    iTimeout
, char   cDepth
)
{
  openLocks();
  m_newLockToken = genLockToken(file);
  m_lockOwner    = sOwner;
  m_timeout      = iTimeout < 0 ? "Infinite" : ""+iTimeout;
  m_depth        = cDepth == 'I' ? "Infinity" : "0";
  this.locks.addLock
  ( this.pi.getItem(0)
  , m_newLockToken
  , calcETag(file)
  , this.sReqPath
  , sOwner
  , cDepth
  , iTimeout
  , m_lockscope
  , isNull
  );
  cleanLocks();
  respLocked(file);
}

//----------------------------------------------------------------------

private boolean operRELOCK
( File   file
, String sToken
, int    iTimeout
)
{
  openLocks();
  LockEntry _lock = this.locks.getLock(this.pi.getItem(0), sToken);
  if (_lock != null)
  {
    shell.log(4, "Refreshing lock "+sToken);
    m_newLockToken = sToken;
    m_lockOwner    = _lock.sOwner;
    m_timeout      = _lock.iTimeout < 0 ? "Infinite" : ""+iTimeout;
    m_depth        = _lock.cDepth == 'I' ? "Infinity" : "0";
    m_lockscope    = _lock.sScope;
    if (iTimeout >= 0) {
      this.locks.refreshLock
      ( this.pi.getItem(0)
      , sToken
      , iTimeout
      );
    }
    else {
      this.locks.refreshLock
      ( this.pi.getItem(0)
      , sToken
      );
    }
    respLocked(file);
    return true;
  }
  shell.log(4, "Could not find the lock being refreshed: "+sToken);
  return false;
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
      if (sTimeType.equalsIgnoreCase("infinite")) {
        return -1;  //++ new NUMBER(0);  new NUMBER(86400); NUMBER(Integer.MAX_VALUE);
      }
      else if (sTimeType.startsWith("second-")) {
        String s = sTimeType.substring(7).trim();
        try { return Integer.parseInt(s, 10); }
        catch (NumberFormatException e) {}
      }
    }
  }
  return -1;
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
      if (child.getName().equalsIgnoreCase("owner")) {
        List cl = child.getChildren();
        if (!cl.isEmpty()) {
          Element own = (Element)(cl.get(0));
          if (own.getName().equalsIgnoreCase("href")) {
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
( File  file
)
{
  respStatus(200, HTTP_OK);
  respWebDAV(2);
  respLockToken(m_newLockToken);
  respDoc("prop");
  Element activelock = appElem(xrespElem,"lockdiscovery",null,null);
  activelock = appElem(activelock, "activelock", null, null);
  Element e = appElem(activelock,"locktype",null,null);
  appElem(e, "write", null, null);

  e = appElem(activelock,"lockscope",null,null);
  appElem(e, m_lockscope, null, null);
  if ( m_lockOwner.startsWith("href::") )
  {
    e = appElem(activelock, "owner", null, null);
    appElem(e, "href", null, m_lockOwner.substring(6));
  }
  else
    appElem(activelock,"owner", null, m_lockOwner);
  e = appElem(activelock,"locktoken",null, null);
  appElem(e, "href", null, LOCK_TOKEN_SCHEMA+m_newLockToken);
  appElem(activelock, "depth", null, m_depth);
  appElem(activelock, "timeout", null, m_timeout);
  respXMLContent("UTF-8");
}

//----------------------------------------------------------------------

private void respLockToken (String sLockToken)
{
  if (sLockToken != null)
    respHeader("Lock-Token", "<"+LOCK_TOKEN_SCHEMA+sLockToken+">");
}

//----------------------------------------------------------------------

private String genLockToken (File file)
{
  return UUID();
}

private static String digits(long val, int digits)
{
  long hi = 1L << (digits * 4);
  return Long.toHexString(hi | (val & (hi - 1))).substring(1);
}


private String UUIDToString(byte[] uuid)
{
  long msb = 0;
  long lsb = 0;
  for (int i=0; i<8; i++)
      msb = (msb << 8) | (uuid[i] & 0xff);
  for (int i=8; i<16; i++)
      lsb = (lsb << 8) | (uuid[i] & 0xff);

  return (digits(msb >> 32, 8) + "-" +
  	  digits(msb >> 16, 4) + "-" +
	  digits(msb, 4) + "-" +
	  digits(lsb >> 48, 4) + "-" +
	  digits(lsb, 12));
}

private String UUID()
{
  SecureRandom ng = RNG;
  if (ng == null) 
  {
       RNG = ng = new SecureRandom();
  }

  byte[] randomBytes = new byte[16];
  ng.nextBytes(randomBytes);
  randomBytes[6]  &= 0x0f;  /* clear version        */
  randomBytes[6]  |= 0x40;  /* set to version 4     */
  randomBytes[8]  &= 0x3f;  /* clear variant        */
  randomBytes[8]  |= 0x80;  /* set to IETF variant  */

  return UUIDToString(randomBytes);
}


//----------------------------------------------------------------------

private volatile SecureRandom RNG = null;
private static String fileRevision = "$Revision: 1.15 $";
} // class LOCK

//----------------------------------------------------------------------
