//----------------------------------------------------------------------
// JOPAException.java
//
// $Id: JOPAException.java,v 1.3 2005/01/13 11:41:26 Bob Exp $
//
//----------------------------------------------------------------------

package com.nnetworks.jopa;

import com.nnetworks.shell.Logger;

import java.io.UnsupportedEncodingException;

//----------------------------------------------------------------------

public class JOPAException extends java.lang.Exception
{

//----------------------------------------------------------------------

public String sWhere;

//----------------------------------------------------------------------

public JOPAException (String sMsg)
{ 
  super(sMsg);
  this.sWhere = "";
}

public JOPAException (String sWhere, String sMsg)
{
    super(sWhere + ": " + sMsg);
    this.sWhere = sWhere;
}

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.3 $";
} // class JOPAException

//----------------------------------------------------------------------
