//----------------------------------------------------------------------
// NATIVE.GET.java
//
// $Id: GET.java,v 1.4 2005/01/17 15:40:20 Bob Exp $
//
//----------------------------------------------------------------------

package com.nnetworks.jopa.NATIVE;

import com.nnetworks.jopa.NATIVE.NATIVE;
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

public class GET extends NATIVE
implements JOPAMethod
{

//----------------------------------------------------------------------	
public void service ()
throws ServletException, IOException
{
  try {
    establishConnection();
    setCGIEnv();
    switch (m_iQueryMode) {
      case 1:
//        prepareParams(this.request.getQueryString(), preparePath());
        prepareParams(this.request, preparePath());
        doProc();
        break;
      case 2:
        shell.log(3,"GET.doDownload:");
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
