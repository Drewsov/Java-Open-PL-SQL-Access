//----------------------------------------------------------------------
// UDAV.HEAD.java
//
// $Id: HEAD.java,v 1.8 2005/04/19 10:15:56 Bob Exp $
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

public class HEAD extends UDAV
implements JOPAMethod
{

public HEAD()
{
  this.accessModeRequired = ACCESS_MODE_READONLY;
}

//----------------------------------------------------------------------

public void service ()
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
    if (checkModifiedSince(file))
      return;
    if (file.isDirectory()) {
      respDir(file, this.sReqPath);
    }
    else {
      respFile(file, this.sReqPath);
    }
  }
}

//----------------------------------------------------------------------

private void respDir (File file, String path)
throws ServletException, IOException
{
  respStatus(200, HTTP_OK);
  respWebDAV(3);
  respHeader("ETag", calcETag(file));
  respHeader("Last-Modified", fmtGMT(file.lastModified()));
  respContentType("text/html;charset="+this.effCharset);
}

//----------------------------------------------------------------------

private void respFile (File file, String path)
throws ServletException, IOException
{
  respStatus(200, HTTP_OK);
  respWebDAV(3);
  respHeader("ETag", calcETag(file));
  respHeader("Last-Modified", fmtGMT(file.lastModified()));
  respContentType(guessContentType(file.getName()));
  long len = file.length();
  respContentLength((int)len);
}

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.8 $";
} // class HEAD

//----------------------------------------------------------------------
