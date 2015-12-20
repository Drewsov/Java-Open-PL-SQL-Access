//----------------------------------------------------------------------
// UDAV.LockEntry.java
//
// $Id: LockEntry.java,v 1.7 2005/04/26 16:27:34 Bob Exp $
//
//----------------------------------------------------------------------

package com.nnetworks.jopa.UDAV;

import com.nnetworks.jopa.UDAV.*;
import com.nnetworks.jopa.*;

import java.io.*;
import java.util.*;

//----------------------------------------------------------------------

public class LockEntry
implements Serializable
{

//----------------------------------------------------------------------

private static Calendar gc;

//----------------------------------------------------------------------

public String sSchema;
public String sToken;
public String sETag;
public String sOwner;
public char   cDepth;
public int    iTimeout;
public Date   dLocked;
public Date   dExpire;
public String href;
public String sScope;
public boolean isNullResource;

//----------------------------------------------------------------------

public void set
( String sSchema
, String sToken
, String sETag
, String sHref
, String sOwner
, char   cDepth
, int    iTimeout
, String scope
, boolean isNull
)
{
  this.sSchema = sSchema;
  this.sToken  = sToken;
  this.sETag   = sETag;
  this.sOwner  = sOwner;
  this.href    = sHref;
  this.cDepth  = cDepth;
  this.sScope  = scope;
  this.isNullResource = isNull;
  refresh(iTimeout);
}

//----------------------------------------------------------------------

public void refresh (int iTimeout)
{
  this.iTimeout = (iTimeout >= 0 ? iTimeout : -1);
  refresh();
}

//----------------------------------------------------------------------

public void refresh ()
{
  this.dLocked = new Date();
  if (this.iTimeout >= 0) 
  {
    if (gc == null) gc = Calendar.getInstance();
    gc.setTime(this.dLocked);
    gc.add(Calendar.SECOND, this.iTimeout);
    this.dExpire = gc.getTime();
  }
  else
    this.dExpire  = null;
}

public boolean expired()
{
  return this.dExpire != null && new Date().after(this.dExpire);
}

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.7 $";
} // class LockEntry

//----------------------------------------------------------------------
