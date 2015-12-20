//----------------------------------------------------------------------
// UDAV.MOVE.java
//
// $Id: MOVE.java,v 1.7 2005/04/19 10:15:56 Bob Exp $
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

public class MOVE extends UDAV
implements JOPAMethod
{

public MOVE()
{
  this.accessModeRequired = ACCESS_MODE_READWRITE;
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

    PathInfo piDst = reqDst();
    if (piDst == null) {
      respStatus(409, HTTP_CONFLICT);
      return;
    }

    File fileDstDir = locateResource(piDst.merge(1, piDst.iLast));
    if (fileDstDir == null) {
      respStatus(400, HTTP_BAD_REQUEST);
      return;
    }
    if (!fileDstDir.exists()) {
      respStatus(409, HTTP_CONFLICT);
      return;
    }

    File fileDst = locateResource(piDst.merge(1, piDst.iCount));
    if (fileDst == null) {
      respStatus(400, HTTP_BAD_REQUEST);
      return;
    }

    if (file.equals(fileDst)) {
      shell.log(4, "Source and target files are the same.");
      respStatus(403, HTTP_FORBIDDEN);
      return;
    }

    //-----\
    if (isLocked(file) || isLocked(fileDst))
    {
      respStatus(423, HTTP_LOCKED);
      return;
    }
    if (file.isDirectory()) {
      if (lockedAnyChild(file, this.sReqPath, 0)) {
        respStatus(409, HTTP_CONFLICT);
        return;
      }
    }
    //-----/

    String ovwt = this.request.getHeader("Overwrite");
    ovwt = (ovwt == null) ? "T" : ovwt.toUpperCase();
    boolean overwriting = fileDst.exists();

    if (overwriting && !ovwt.equals("T")) {
      shell.log(4, fileDst.getAbsolutePath()+" already exists.");
      respStatus(412, HTTP_PRECONDITION_FAILED);
      return;
    }

    // we are overwriting, so we MUST delete the destination with Depth=Infinity
    if ( overwriting && !deleteRecursively(fileDst) )
    {
      respStatus(412, HTTP_PRECONDITION_FAILED);
      return;
    }

    if (!file.renameTo(fileDst)) {
      respStatus(412, HTTP_PRECONDITION_FAILED);
      return;
    }

    if (overwriting)
      respStatus(204,HTTP_NO_CONTENT); // the destination existed
    else
      respStatus(201, HTTP_CREATED);   // the destination didn't exist
  }
}

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.7 $";
} // class MOVE

//----------------------------------------------------------------------
