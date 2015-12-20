//----------------------------------------------------------------------
// UDAV.GET.java
//
// $Id: GET.java,v 1.10 2005/04/19 10:15:56 Bob Exp $
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

public class GET extends UDAV
implements JOPAMethod
{

public GET()
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
  respContentType("text/html;charset="+this.effCharset);

  PrintWriter out = this.response.getWriter();
  out.println("<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">");
  out.println("<HTML><HEAD>");
  out.print(" <TITLE>Index of "); out.print(path == null || path.equals("") ? "/" : path); out.println("</TITLE>");
  out.println("</HEAD><BODY>");
  out.print("<H1>Index of "); out.print(path == null || path.equals("") ? "/" : path); out.println("</H1>");
  out.println("<TABLE width=\"100%\">");
  out.println("<TR>");
  out.println("  <TD width=\"5%\"><B>&nbsp;</B><HR></TD>");
  out.println("  <TD width=\"50%\"><B>Name</B><HR></TD>");
  out.println("  <TD width=\"15%\"><B>Size</B><HR></TD>");
  out.println("  <TD width=\"30%\"><B>Last modified</B><HR></TD>");
  out.println("</TR>");

  //String sPrefix = this.request.getServletPath() + '/' + this.pi.getItem(0) + '/';
  String sPrefix = sReqBase; //this.sReqBase;
  if (this.pi.iCount > 1) {
    out.println("<TR>");
    out.println("  <TD width=\"5%\"><P>DIR</P></TD>");
    out.print("  <TD width=\"50%\"><A HREF=\"");
      out.print(sPrefix);
      out.print(this.pi.merge(1, this.pi.iLast));
    out.println("\"><B> . . </B></A></TD>");
    out.println("  <TD width=\"15%\"><P></P></TD>");
    out.println("  <TD width=\"30%\"><P></P></TD>");
    out.println("</TR>");
  }

  int i;
  File f;
  String s;
  File[] files = file.listFiles();
  if (files != null) {
    for (i = 0; i < files.length; i++) {
      f = files[i];
      if (f.isDirectory()) {
        s = f.getName();
        out.println("<TR>");
        out.println("  <TD width=\"5%\"><P>DIR</P></TD>");
        out.print("  <TD width=\"50%\"><A HREF=\"");
          out.print(sPrefix);
          out.print(path);
          out.print("/");
          out.print(s);
        out.print("\"><B>");
          out.print(s);
        out.println("</B></A></TD>");
        out.println("  <TD width=\"15%\"><P></P></TD>");
        out.print("  <TD width=\"30%\"><P>");
          out.print(fmtDFile(f.lastModified()));
        out.println("</P></TD>");
        out.println("</TR>");
      }
    }
    for (i = 0; i < files.length; i++) {
      f = files[i];
      if (!f.isDirectory()) {
        s = f.getName();
        out.println("<TR>");
        out.print("  <TD width=\"5%\"><P>");
          out.print("File");
        out.println("</P></TD>");
        out.print("  <TD width=\"50%\"><A HREF=\"");
          out.print(sPrefix);
          out.print(path);
          out.print("/");
          out.print(s);
        out.print("\"><B>");
          out.print(s);
        out.println("</B></A></TD>");
        out.print("  <TD width=\"15%\"><P>");
          out.print(Long.toString(f.length()));
        out.println("</P></TD>");
        out.print("  <TD width=\"30%\"><P>");
          out.print(fmtDFile(f.lastModified()));
        out.println("</P></TD>");
        out.println("</TR>");
      }
    }
  }

  out.println("</TABLE>");
  out.println("</BODY></HTML>");
}

//----------------------------------------------------------------------

private void respFullFile(File file)
 throws ServletException, IOException
{
  long len = file.length();
  respStatus(200, HTTP_OK);
  respWebDAV(3);
  respHeader("Accept-Ranges","bytes");
  respHeader("Last-Modified",fmtGMT(file.lastModified()));
  respHeader("ETag", calcETag(file));
  respContentType(guessContentType(file.getName()));
  if (len > 0L) 
    respFileContent(file);
  else
    respContentLength((int)len);

}

private void respFile (File file, String path)
 throws ServletException, IOException
{

  if (checkIfNoneMatch(file))  
  // client asks to return the resource only if none of supplied e-tags match current e-tag,
  // otherwise we should respond with "Not modified" code.
  {
    respStatus(304, HTTP_NOT_MODIFIED);
    return;
  }

  if (checkModifiedSince(file) || checkNotModifiedSince(file) || checkIfMatch(file))
    return;

  String range  = this.request.getHeader("Range");
  long len      = file.length();

  if (range == null)
  {
    respFullFile(file);
    return;
  }
  shell.log(4, "About to serve partial request for "+range);
  if (range.indexOf("bytes=") < 0)
  {
     respFullFile(file);
     return;
  }
  // check if we should satisfy the range request based on If-Range e-tag
  // we should only satisfy the request if If-Range header exists and the
  // resource matches it, otherwise we should send the full entity.
  if (!checkIfRange(file))
  {
    respFullFile(file);
    return;
  }
  range = range.substring(range.indexOf("bytes=")+6);
  long start = 0;
  long stop = len-1;
  String[] rangeParts = range.split("-");
  stop  = (rangeParts.length == 1 || rangeParts[1] == null || rangeParts[1].length() == 0) ? len-1 : Integer.parseInt(rangeParts[1]);
  start = (rangeParts[0] == null || rangeParts[0].length() == 0) ? len-stop : Integer.parseInt(rangeParts[0]);
  // suffix request?
  if (rangeParts[0] == null || rangeParts[0].length() == 0)
    stop = len-1;
  shell.log(4, "Decoded range: "+start+"-"+stop);
  if (start > len-1 || stop >= len)
  {
    respStatus(416, HTTP_NOT_SATISFIABLE);
    return;
  }
  if (stop < start)
  {
    respFullFile(file);
    return;
  }
  respStatus(206, HTTP_PARTIAL_CONTENT);
  respWebDAV(3);
  respHeader("Accept-Ranges","bytes");
  respHeader("Last-Modified",fmtGMT(file.lastModified()));
  respHeader("ETag", calcETag(file));
  respContentType(guessContentType(file.getName()));
  respHeader("Content-Range","bytes "+start+"-"+stop+"/"+len);
  respContentLength((int)(stop-start+1));
  respFileContent(file, start, stop);
}

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.10 $";
} // class GET

//----------------------------------------------------------------------
