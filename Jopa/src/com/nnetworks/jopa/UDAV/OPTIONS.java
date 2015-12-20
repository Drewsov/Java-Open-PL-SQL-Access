//----------------------------------------------------------------------
// UDAV.OPTIONS.java
//
// $Id: OPTIONS.java,v 1.5 2005/04/18 10:08:17 Bob Exp $
//
//----------------------------------------------------------------------

package com.nnetworks.jopa.UDAV;

import com.nnetworks.jopa.UDAV.*;
import com.nnetworks.jopa.*;

import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import java.text.*;
import java.util.*;

//----------------------------------------------------------------------

public class OPTIONS extends UDAV
implements JOPAMethod
{

public OPTIONS()
{
  this.accessModeRequired = ACCESS_MODE_READONLY;
}

//----------------------------------------------------------------------

public void service ()
throws ServletException, IOException
{
  if (authenticate() > 0) {
    respStatus(200, HTTP_OK);
    respWebDAV(4);
  }
}

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.5 $";
} // class OPTIONS

//----------------------------------------------------------------------
