//----------------------------------------------------------------------
// OWA.POST.java
//
// $Id: POST.java,v 1.8 2007/01/01 10:08:16 Drew Exp $
//
//----------------------------------------------------------------------

package com.nnetworks.jopa.PSP;

import com.nnetworks.jopa.*;
import javax.servlet.*;
import java.io.*;
import java.text.MessageFormat;

//----------------------------------------------------------------------

public class POST extends PSP
implements JOPAMethod
{

//----------------------------------------------------------------------

public void service ()
throws ServletException, IOException
{
  try {
    // check if the called package is denied access
    try {
      checkExclusions(this.pi.getItem(-1));
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
    establishConnection();
    setCGIEnv();
    switch (m_iQueryMode) {
      case 1:
        String sContentType = this.request.getContentType();
        if (sContentType.regionMatches(true, 0, "multipart/form-data;", 0, 20)) {
          if (doMultipart(sContentType) > 0) doProc();
          break;
        }
//        prepareParams(readContent());
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
private static String fileRevision = "$Revision: 1.5 $";
} // class POST

//----------------------------------------------------------------------
