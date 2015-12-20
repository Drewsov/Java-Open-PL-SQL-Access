//----------------------------------------------------------------------
// OWA.GET.java
//
// $Id: GET.java,v 1.8 2007/01/01 10:08:16 Drew Exp $
//
//----------------------------------------------------------------------

package com.nnetworks.jopa.OWA;

import com.nnetworks.jopa.OWA.OWA;
import com.nnetworks.jopa.*;

import javax.servlet.*;
import javax.servlet.http.*;

import oracle.sql.*;
import oracle.jdbc.driver.*;

import java.sql.*;
import java.io.*;
import java.util.*;
import java.text.MessageFormat;

//----------------------------------------------------------------------

public class GET extends OWA
implements JOPAMethod
{

//----------------------------------------------------------------------

public void service ()
throws ServletException, IOException
{
  try {
    // check if the called package is denied access
    try 
    { 
      //checkExclusions(this.pi.getItem(-1));
      checkExclusions((new String(this.pi.getItem(-1).getBytes("ISO8859_1"), "UTF-8"))); // v. 1.7.1 Drew
    }
    catch(JOPAException e)
    {
      if(e.getMessage().equals("Forbidden"))
       {
         respStatus(403,HTTP_FORBIDDEN);
         Object[] args = { this.pi.getItem(-1) };
         printString(this.response.getOutputStream(), MessageFormat.format(FORBIDDEN_MSG, args));
         return;
       }
       else
         throw e;
    }
    shell.log(3,"establishConnection try.");
    establishConnection();
    shell.log(3,"establishConnection passed.");
    setCGIEnv();
    shell.log(3,"setCGIEnv passed.");
    switch (m_iQueryMode) {
      case 1:
//        prepareParams(this.request.getQueryString());
        prepareParams(this.request);
        shell.log(3,"owa.service.prepareParams passed."); 
        doProc();
        shell.log(3,"owa.service.doProc passed.");
        break;
      case 2:
        doDownload();
        shell.log(3,"owa.service.doDownload passed.");
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
private static String fileRevision = "$Revision: 1.8 $";
} // class GET

//----------------------------------------------------------------------
