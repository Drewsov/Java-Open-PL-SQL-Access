//----------------------------------------------------------------------
// OWA.GET.java
//
// $Id: GET.java,v 1.81 2007/01/01 10:08:16 Drew Exp $
//
//----------------------------------------------------------------------

package com.nnetworks.jopa.PSP;
 
import com.nnetworks.jopa.*;
import javax.servlet.*;
import java.io.*;
import java.text.MessageFormat;

//----------------------------------------------------------------------

public class GET extends PSP
implements JOPAMethod
{

//----------------------------------------------------------------------

public void service ()
throws ServletException, IOException
{
  try {
      // check if the called package is denied access
     this.sReqPath = this.pi.merge(1, -1);
     try
     {
       m_localbase = new File(this.props.getProperty("localbase")).getCanonicalPath();
     }
     catch(IOException e)
     {
       m_localbase = null;
     }

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
    //shell.log(3,"getQueryMode try."); 
    reqURI();
    int m_iQueryMode = getQueryModeScripts();
    //shell.log(3,"=> getQueryMode:"+m_iQueryMode); 
    switch (m_iQueryMode) {
      case 1:
        shell.log(3,"establishConnection try.");
        establishConnection();
        shell.log(3,"establishConnection passed.");
        setCGIEnv();
        shell.log(3,"setCGIEnv passed.");
        prepareParams(this.request);
        shell.log(3,"PSP.service.prepareParams passed."); 
        PreparePage();
        doProc();
        shell.log(3,"PSP.service.doProc passed.");
        break;
      case 2:
        shell.log(3,"PSP.service.PrepareFile.");
            // looking for default page on any level --   Drew
            // start section 
             StringBuffer sb = new StringBuffer(128);
             sb.append(this.sReqURI);
             if (sb.charAt(sb.length()-1) == '/') {
             String def_page = props.getProperty("default_page");
             this.sReqPath = this.sReqPath+"/"+def_page;
             shell.log(4,"PSP.service:sReqURI: "+this.sReqPath);
            // end section
             }
        PrepareFile();
        //doDownload();
        shell.log(3,"PSP.service.PrepareFile passed.");
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
private static String fileRevision = "$Revision: 1.81 $";
} // class GET

//----------------------------------------------------------------------
