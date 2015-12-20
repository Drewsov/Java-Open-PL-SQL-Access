//----------------------------------------------------------------------
// PERSES.LockEntry.java
//----------------------------------------------------------------------

package com.nnetworks.jopa.PERSES;

import com.nnetworks.jopa.PERSES.*;
import com.nnetworks.jopa.*;

import java.io.*;
import java.util.*;

//----------------------------------------------------------------------

public class LockEntry
implements Serializable
{

//----------------------------------------------------------------------

private static GregorianCalendar gc;

//----------------------------------------------------------------------

public String sSchema;
public String sToken;
public String sETag;
public String sOwner;
public char   cDepth;
public int    iTimeout;
public Date   dLocked;
public Date   dExpire;

//----------------------------------------------------------------------

public void set
( String sSchema
, String sToken
, String sETag
, String sOwner
, char   cDepth
, int    iTimeout
)
{
  this.sSchema = sSchema;
  this.sToken  = sToken;
  this.sETag   = sETag;
  this.sOwner  = sOwner;
  this.cDepth  = cDepth;
  refresh(iTimeout);
}

//----------------------------------------------------------------------

public void refresh (int iTimeout)
{
  this.iTimeout = (iTimeout > 0 ? iTimeout : 0);
  refresh();
}

//----------------------------------------------------------------------

public void refresh ()
{
  this.dLocked = new Date();
  if (this.iTimeout > 0) {
    if (gc == null) gc = new GregorianCalendar();
    gc.setTime(this.dLocked);
    gc.add(Calendar.SECOND, this.iTimeout);
    this.dExpire = gc.getTime();
  }
  else {
    this.dExpire  = null;
  }
}

//----------------------------------------------------------------------

} // class LockEntry

//----------------------------------------------------------------------
