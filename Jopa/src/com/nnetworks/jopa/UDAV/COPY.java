//----------------------------------------------------------------------
// UDAV.COPY.java
//
// $Id: COPY.java,v 1.9 2005/04/19 10:15:56 Bob Exp $
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

public class COPY extends UDAV
implements JOPAMethod
{

  private volatile boolean newFile;
  private volatile int     depth;
  private Vector   failedFiles;

public COPY()
{
  this.accessModeRequired = ACCESS_MODE_READWRITE;
}

//----------------------------------------------------------------------

public void service()
throws ServletException, IOException
{
  checkLocalBase();
  newFile = false;
  if (authenticate() > 0) {
    reqURI();
    reqDepth(0, Integer.MAX_VALUE);

    File file = locateResource(this.sReqPath);
    if (file == null) {
      respStatus(400, HTTP_BAD_REQUEST);
      return;
    }
    if (!file.exists()) {
      respStatus(404, HTTP_NOT_FOUND +": " + this.sReqPath);
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

    newFile = !fileDst.exists();

    if (file.equals(fileDst))
    {
      respStatus(403, HTTP_FORBIDDEN);
      return;
    }

    String owt = this.request.getHeader("Overwrite");
    owt = (owt == null) ? "T" : owt.toUpperCase();
    if( owt.startsWith("F") && !newFile )
    {
      respStatus(412, HTTP_PRECONDITION_FAILED);
      return;
    }

    if (isLocked(fileDst))
    {
      respStatus(423, HTTP_LOCKED);
      return;
    }

    failedFiles = new Vector();

    if (file.isDirectory())
    {
      if (!operCOPYDir(file, fileDst))
      {
        respStatus(207, HTTP_MULTI_STATUS);
        respDoc("multistatus");
        for (int i = 0; i < failedFiles.size(); i++)
        {
          Element rsp = appElem(this.xrespElem, "response", null, null);
          appElem(rsp, "status", null, "HTTP/1.1 507 Insufficient space");
          appElem(rsp, "href", null, makeRelHRef( ((File)failedFiles.elementAt(i)).getAbsolutePath().substring(m_localbase.length()+1),file));
        }
        respXMLContent("UTF-8");
        return;
      }
    }
    else
      if (!operCOPY(file, fileDst)) 
      {
        respStatus(507, HTTP_INSUFFICIENT_SPACE);
        return;
      }

    if (newFile)
      respStatus(201, HTTP_CREATED);
    else
      respStatus(204, HTTP_NO_CONTENT);
  }
}

//----------------------------------------------------------------------

private boolean operCOPY (File src, File dst)
{
  FileInputStream  fis = null;
  FileOutputStream fos = null;
  try {
    fis = new FileInputStream(src);
    fos = new FileOutputStream(dst);
    byte[] buf = new byte[BUFFER_SIZE];
    int n = fis.read(buf, 0, BUFFER_SIZE);
    while (n > 0) {
      fos.write(buf, 0, n);
      n = fis.read(buf, 0, BUFFER_SIZE);
    }
    try { fos.close(); } catch (IOException ioe1) {}
    try { fis.close(); } catch (IOException ioe1) {}
    return true;
  }
  catch (IOException ioe) {
    if (fos != null) try { fos.close(); } catch (IOException ioe1) {}
    if (fis != null) try { fis.close(); } catch (IOException ioe1) {}
    dst.delete();
  }
  return false;
}


private boolean operCOPYDir (File srcDir, File dstDir)
{
  File[] dir = srcDir.listFiles();
  boolean result = true;
  if (!dstDir.mkdir())
  {
    failedFiles.add(dstDir);
    return false;
  }
  for (int i = 0; i < dir.length; i++)
  {
    if (dir[i].isDirectory())
    {
      if (depth <= iDepth)
      {
        ++depth;
        result &= operCOPYDir(dir[i], new File(dstDir.getAbsolutePath(), dir[i].getName()));
      }
    }
    else
      if (!operCOPY(dir[i], new File(dstDir.getAbsolutePath(), dir[i].getName())))
      {
        result = false;
        failedFiles.add(dir[i]);
      }
        
  }
  return result;

}

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.9 $";
} // class COPY

//----------------------------------------------------------------------
