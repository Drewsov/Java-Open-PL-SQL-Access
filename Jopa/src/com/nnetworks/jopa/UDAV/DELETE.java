//----------------------------------------------------------------------
// UDAV.DELETE.java
//
// $Id: DELETE.java,v 1.9 2005/04/19 10:15:56 Bob Exp $
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

import org.jdom.*;

//----------------------------------------------------------------------

public class DELETE extends UDAV
implements JOPAMethod
{

public DELETE()
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
    if (!file.exists()) {
      respStatus(404, HTTP_NOT_FOUND);
      return;
    }
    //-----\
    if (isLocked(file)) {
      respStatus(423, HTTP_LOCKED);
      return;
    }
    if (reportLockedChildren(file))
      return;
    //-----/
    if ( !deleteRecursively(file) ) 
    {
      respStatus(423, HTTP_LOCKED);
    }
    respStatus(204, HTTP_NO_CONTENT);
  }
}

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.9 $";
} // class DELETE

//----------------------------------------------------------------------
