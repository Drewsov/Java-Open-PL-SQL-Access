//----------------------------------------------------------------------
// JOPAShell.java
//
// $Id: JOPAShell.java,v 1.4 2005/01/13 11:42:42 Bob Exp $
//
//----------------------------------------------------------------------

package com.nnetworks.jopa;

import com.nnetworks.jopa.*;
import com.nnetworks.resources.ResourcePool;

import java.io.*;

//----------------------------------------------------------------------

public interface JOPAShell
{

//----------------------------------------------------------------------
// Info:

public String getVersion ();

//----------------------------------------------------------------------
// Config support:

public File getFileCfg ();
public ResourcePool getCfg ();
public int loadCfg ();
public int saveCfg ();
public void configParameters ();

//----------------------------------------------------------------------
// Logging support:

public void log (int lev, String msg);
public void log (int lev, String msg1, String msg2);
public void log (int lev, String msg1, String msg2, String msg3);
public void log (int lev, String msg1, String msg2, String msg3, String msg4);
public PrintStream getLogStream (int lev);
public PrintStream getDebugStream (int lev);

//----------------------------------------------------------------------
// Connection support:

public ConnectionPool getConnectionPool();

//----------------------------------------------------------------------
// Code support:

public String getCode
( String sName
) throws JOPAException;

public String getCode
( String sName
, String sPar1, String sVal1
) throws JOPAException;

public String getCode
( String sName
, String sPar1, String sVal1
, String sPar2, String sVal2
) throws JOPAException;

public String getCode
( String sName
, String sPar1, String sVal1
, String sPar2, String sVal2
, String sPar3, String sVal3
) throws JOPAException;

public StringBuffer getCodeBuffer
( String sName
) throws JOPAException;

public void substCode
( StringBuffer sb
, String sPar, String sVal
);

//----------------------------------------------------------------------
// Shelf support:

public void putOnShelf (String sKey, Object obj);

public Object getFromShelf (String sKey);

//----------------------------------------------------------------------
}  // interface JOPAShell

//----------------------------------------------------------------------
