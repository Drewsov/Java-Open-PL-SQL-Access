//----------------------------------------------------------------------
// WEBDAV.MKCOL.java
//
// $Id: MKCOL.java,v 1.4 2005/04/19 12:19:53 Bob Exp $
//
//----------------------------------------------------------------------

package com.nnetworks.jopa.WEBDAV;

import com.nnetworks.jopa.WEBDAV.*;
import com.nnetworks.jopa.*;

import javax.servlet.*;
import javax.servlet.http.*;

import oracle.sql.*;
import oracle.jdbc.driver.*;

import java.sql.*;
import java.io.*;
import java.text.*;
import java.util.*;

//----------------------------------------------------------------------

public class MKCOL extends WEBDAV
implements JOPAMethod
{

//----------------------------------------------------------------------

public void service ()
throws ServletException, IOException
{
  try {
    establishConnection();

    // Authentication:
    int iAccl = authorize();
    if (iAccl <= 0) {
      respAuthenticate("Introduce yourself for NTS WebDAV");
      return;
    }
    if (iAccl < 4) {
      respStatus(403, HTTP_FORBIDDEN);
    }

    reqURI();
    impersonate(iAccl);
    NUMBER nDir = locatePath(this.pi.merge(1, this.pi.iLast));
    if (nDir == null) {
      respStatus(409, HTTP_CONFLICT);
    }
    else {
      NUMBER nKey = locatePath(this.sReqPath);
      if (nKey != null) {
        respStatus(405, HTTP_BAD_METHOD);
      }
      else {
        nKey = ensureNode(nDir, this.sReqName, "DIR");
        if (nKey == null) {
          respStatus(507, HTTP_INSUFFICIENT_SPACE);
        }
        else {
          respStatus(201, HTTP_CREATED);
        }
      }
    }
  }
  catch (JOPAException e) {
    respFatal(500, e);
  }
  finally {
    releaseConnection();
  }
}

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.4 $";
} // class MKCOL

//----------------------------------------------------------------------
