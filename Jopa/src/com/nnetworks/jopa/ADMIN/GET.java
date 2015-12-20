//----------------------------------------------------------------------
// ADMIN.GET.java
//
// $Id: GET.java,v 1.4 2005/01/17 15:38:56 Bob Exp $
//
//----------------------------------------------------------------------

package com.nnetworks.jopa.ADMIN;

import com.nnetworks.jopa.ADMIN.ADMIN;
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

public class GET extends ADMIN
implements JOPAMethod
{

//----------------------------------------------------------------------

public void service ()
throws ServletException, IOException
{
  try {
    switch (m_iQueryMode) {
      case 1:
//        prepareParams(this.request.getQueryString());
        prepareParams(this.request);
        doProc();
        break;
      case 2:
        doDownload();
        break;
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
} // class GET

//----------------------------------------------------------------------
