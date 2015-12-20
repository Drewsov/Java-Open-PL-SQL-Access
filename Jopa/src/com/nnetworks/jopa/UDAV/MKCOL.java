//----------------------------------------------------------------------
// UDAV.MKCOL.java
//
// $Id: MKCOL.java,v 1.6 2005/04/19 10:15:56 Bob Exp $
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

public class MKCOL extends UDAV
implements JOPAMethod
{

public MKCOL()
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
    File parent = locateResource(this.pi.merge(1, this.pi.iLast));
    if (parent == null) {
      respStatus(400, HTTP_BAD_REQUEST);
      return;
    }
    if (!parent.exists()) {
      respStatus(409, HTTP_CONFLICT);
      return;
    }
    if (!parent.isDirectory()) {
      respStatus(409, HTTP_CONFLICT);
      return;
    }
    //-----\
    if (isLocked(parent)) {
      respStatus(423, HTTP_LOCKED);
      return;
    }
    //-----/
    File file = locateResource(this.sReqPath);
    if (file == null) {
      respStatus(400, HTTP_BAD_REQUEST);
      return;
    }
    if (file.exists()) {
      respStatus(405, HTTP_BAD_METHOD);
      return;
    }
    if (!file.mkdir()) {
      respStatus(507, HTTP_INSUFFICIENT_SPACE);
    }
    respStatus(201, HTTP_CREATED);
  }
}

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.6 $";
} // class MKCOL

//----------------------------------------------------------------------
