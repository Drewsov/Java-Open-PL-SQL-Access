//----------------------------------------------------------------------
// UDAV.PUT.java
//
// $Id: PUT.java,v 1.10 2005/04/26 15:27:36 Bob Exp $
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

public class PUT extends UDAV
implements JOPAMethod
{

  private volatile boolean newFile;

//----------------------------------------------------------------------

public PUT()
{
  this.accessModeRequired = ACCESS_MODE_READWRITE;
}

public void service()
throws ServletException, IOException
{
  checkLocalBase();
  newFile = false;
  if (authenticate() > 0) {
    reqURI();
    File file = locateResource(this.sReqPath);
    if (file == null) {
      respStatus(400, HTTP_BAD_REQUEST);
      return;
    }
    //-----\
    if (isLocked(file)) {
      respStatus(423, HTTP_LOCKED);
      return;
    }
    try
    {
      File parent = file.getParentFile();
      while (parent != null && parent.getCanonicalPath().startsWith(m_localbase)) // don't escape the local directory
      {
         if(isLocked(parent))
         {
           if (!file.isDirectory())
           {
             respStatus(423, HTTP_LOCKED);
             return;
           }
           else
           { // collection operations require multi-status responses
             respStatus(207, HTTP_MULTI_STATUS);
             respDoc("multistatus");
             Element rsp = appElem(this.xrespElem, "response",null,null);
             appElem(rsp, "status", null, "HTTP/1.1 423 Locked");
             appElem(rsp, "href", null, makeRelHRef(parent.getAbsolutePath().substring(m_localbase.length()+1),file));
             respXMLContent("UTF-8");
             return;
           }
         }
         // walk up the hierarchy and check that no parent collection is locked already
         parent = parent.getParentFile();
      }
    }
    catch(IOException e)
    {
      // could not evaluate the canonical path, the path was probably invalid
      shell.log(4, "Could not get canonical path: "+e.toString());
      respStatus(400, HTTP_BAD_REQUEST);
      return;
    }
    //-----/
    if (!operPUT(file)) {
      return;
    }
    if (newFile)
      respStatus(201, HTTP_CREATED);
    else
      respStatus(204, HTTP_NO_CONTENT);
  }
}

//----------------------------------------------------------------------

private boolean operPUT (File file)
{
  InputStream is = null;
  FileOutputStream fos = null;
  newFile = !file.exists();
  if (!file.getParentFile().exists())
  {
    respStatus(409, HTTP_PRECONDITION_FAILED);
    return false;
  }
  try {
    is = this.request.getInputStream();
    fos = new FileOutputStream(file);
    byte[] buf = new byte[BUFFER_SIZE];
    int n = is.read(buf, 0, BUFFER_SIZE);
    while (n > 0) {
      fos.write(buf, 0, n);
      n = is.read(buf, 0, BUFFER_SIZE);
    }
    try 
    { 
       fos.close(); 
    } 
    catch (IOException ioe1) 
    {
       shell.log(2, "PUT.operPUT(): "+ioe1.toString() ); 
    }
    return true;
  }
  catch (IOException ioe) {
    shell.log(2, "PUT.operPUT(): "+ioe.toString() ); 
    respStatus(500, ioe.getMessage());
    if (fos != null) 
      try 
      { 
         fos.close(); 
      }
      catch (IOException ioe1) 
      { 
         shell.log(2, "PUT.operPUT(): "+ioe1.toString() ); 
      }
  }
  return false;
}

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.10 $";
} // class PUT

//----------------------------------------------------------------------
