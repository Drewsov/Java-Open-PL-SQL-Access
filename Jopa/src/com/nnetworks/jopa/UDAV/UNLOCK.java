//----------------------------------------------------------------------
// UDAV.UNLOCK.java
//
// $Id: UNLOCK.java,v 1.9 2005/04/19 10:15:57 Bob Exp $
//
//----------------------------------------------------------------------

package com.nnetworks.jopa.UDAV;

import com.nnetworks.jopa.UDAV.*;
import com.nnetworks.jopa.*;

import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import java.util.*;

//----------------------------------------------------------------------

public class UNLOCK extends UDAV
implements JOPAMethod
{

public UNLOCK()
{
  this.accessModeRequired = ACCESS_MODE_READWRITE;
}

//----------------------------------------------------------------------

public void service()
throws ServletException, IOException
{
  checkLocalBase();
  String sToken = reqLockToken();
  if (sToken == null) {
    respStatus(412, HTTP_PRECONDITION_FAILED);
    return;
  }
  if (authenticate() > 0) 
  {
    reqURI();
    openLocks();
    LockEntry _lock = this.locks.getLock(this.pi.getItem(0), sToken);
    boolean wasNull = false;
    if (_lock != null)
    {
      // check that the lock is removed from the right resource
      if (!_lock.href.equals(this.sReqPath))
      {
        respStatus(409,HTTP_CONFLICT);
        return;
      }
      // remove null resource created due to this lock if it's still null
      if (_lock.isNullResource)
      {
        File nullres = locateResource(_lock.href);
        if (nullres != null)
        {
          if ( (nullres.isDirectory() && nullres.listFiles().length == 0) ||
               (!nullres.isDirectory() && nullres.length() == 0) )
          {
             wasNull = true;
             deleteRecursively(nullres);
          }
        }
      }
    }
    this.locks.removeLock(this.pi.getItem(0), sToken);
    cleanLocks();
    if (!wasNull && !locateResource(this.sReqPath).exists() )
      respStatus(404, HTTP_NOT_FOUND);
    else
      respStatus(204, HTTP_NO_CONTENT);
  }
}

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.9 $";
} // class UNLOCK

//----------------------------------------------------------------------
