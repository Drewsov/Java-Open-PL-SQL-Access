//----------------------------------------------------------------------
// Processor_PSP.java
//
// $Id: Processor_PSP.java,v 1.82 2007/01/01 00:00:00 Drew Exp $
//
//----------------------------------------------------------------------

package com.nnetworks.jopa;

import javax.servlet.*;
import oracle.sql.*;
import oracle.jdbc.driver.*;
import java.sql.*;
import java.io.*;
import java.util.*;

//----------------------------------------------------------------------

public class Processor_PSP extends Processor
{

//----------------------------------------------------------------------

protected int      m_iQueryMode;
protected Vector   m_vecParName;
protected Vector   m_vecParData;

//----------------------------------------------------------------------

protected void afterInit ()
{
  super.afterInit();
  m_iQueryMode = getQueryMode();
  m_vecParName = new Vector(64);
  m_vecParData = new Vector(64);
}

//----------------------------------------------------------------------

protected void initSession ()
{
  super.initSession();

    //printHex(this.pi.getItem(-1), 64);

  for (int i = 1; i < this.pi.iCount; i++)
  {
    this.pi.asPathInfo[i] = unescape(this.pi.asPathInfo[i], this.effCharset);
  }

}
//----------------------------------------------------------------------
/*private String[] commaSeparatedStringToStringArray(String aString){
        String[] splittArray = null;
        if (aString != null || !aString.equalsIgnoreCase("")){
             splittArray = aString.split(";");
             System.out.println(aString + " " + splittArray);
        }
        return splittArray;
    }
    */
//----------------------------------------------------------------------

 private boolean checkExtention(String sExt, String aRule){
         String[] splittArray = null;
         try {
         if (!aRule.equalsIgnoreCase("")){
              splittArray = aRule.split(";");
            //  shell.log(3,"=> Processor_PSP.checkExtention: "+aRule+":"+splittArray.length);
         }
         int i = 0;
         while(i < splittArray.length)
         {
          //  shell.log(3,"=> Processor_PSP.splittArray["+i+"]: "+splittArray[i]);
          if(sExt.indexOf(splittArray[i])>0) return true;
          i++;
         }
         return false;
         }
         catch (NullPointerException e){return false;}
     }
 //----------------------------------------------------------------------

protected int getQueryMode()
{
  if (this.pi.iCount > 1) {
    String s = this.props.getProperty("document_path", "data");
    //shell.log(3,"=> Processor_PSP.getQueryMode: "+pi.iCount+":"+this.pi.getItem(pi.iCount));
      if (s.equalsIgnoreCase(this.pi.getItem(1))) {
       return 2;
      }
    // Andrew A. Toropov
    // please return image when we are using extentions like see below in web.xml
     // shell.log(3,"=> FILE_TYPES: "+ FILE_TYPES);
     String sExt = this.pi.getItem(pi.iCount).toLowerCase();
     if (checkExtention(sExt,FILE_TYPES))  return 2;
    return 1;
  }
  return 1;
}


protected int getQueryModeScripts()
    {
      if (this.pi.iCount > 1) {
          String s = this.props.getProperty("document_path", "data");
          shell.log(3,"=> Processor_PSP.getQueryMode: "+pi.iCount+":"+this.pi.getItem(pi.iCount)+":"+this.pi.getItem(this.pi.iLast));
          if (this.pi.getItem(this.pi.iLast).equalsIgnoreCase(Processor.SCRIPT_PREFIX)) return 1;
          if (s.equalsIgnoreCase(this.pi.getItem(1))) {
           return 2;
          }
        // Andrew A. Toropov
        // please return image when we are using extentions like see below in web.xml
         // shell.log(3,"=> FILE_TYPES: "+ FILE_TYPES);
         String sExt = this.pi.getItem(pi.iCount).toLowerCase();
         if (!checkExtention(sExt,SCRIPT_TYPES))  return 2;
        return 1;
      }
      return 1;
}

//----------------------------------------------------------------------

protected String preparePath ()
{
   shell.log(3,"=> Processor_PSP.preparePath: "+ (this.pi.iCount == 2 && this.pi.cMode == '!' &&
      this.pi.getItem(-1).equalsIgnoreCase(SCRIPT_PREFIX)||(this.pi.iCount == 2  &&
      this.pi.getItem(-1).equalsIgnoreCase(SCRIPT_PREFIX)) ?
      null : this.pi.merge(1, -1)));
  /*return
    ( this.pi.iCount == 2 && this.pi.cMode == '!' &&
      this.pi.getItem(-1).equalsIgnoreCase(SCRIPT_PREFIX) ?
      null : this.pi.merge(1, -1)
    );*/
   return (this.pi.iCount == 2 && this.pi.cMode == '!' &&
      this.pi.getItem(-1).equalsIgnoreCase(SCRIPT_PREFIX)||(this.pi.iCount == 2  &&
      this.pi.getItem(-1).equalsIgnoreCase(SCRIPT_PREFIX)) ?
      null : this.pi.merge(1, -1));
}

//----------------------------------------------------------------------
// CGI Environment support:
//----------------------------------------------------------------------

protected void prepareEnv()
{
  super.prepareEnv();
  // AAT
  //appEnv("PATH_INFO",  this.pi.merge(m_iQueryMode, -1));
   if (this.pi.iCount > 1) {
     String s = this.props.getProperty("document_path", "data");
     String pinfo =  this.pi.merge(m_iQueryMode, -1);
         if (s.equalsIgnoreCase(this.pi.getItem(1))) {
         try {pinfo = new String(this.pi.merge(m_iQueryMode, -1).getBytes("Cp1251"),"UTF8");shell.log(3, "<= PATH_INFO:"+ pinfo);}
         catch (UnsupportedEncodingException e) {}
         appEnv("PATH_INFO",pinfo);
         return;
         }
         else 
         appEnv("PATH_INFO",  this.pi.merge(1, -1));
     }
    else 
    appEnv("PATH_INFO",  this.pi.merge(1, -1));
}

//----------------------------------------------------------------------
// Parameters support:
//----------------------------------------------------------------------

protected void appPar (String sName, String sData)
{
  if (sName == null || sData == null) return;
  shell.log(3, "=> Processor_PSP.appPar: ", sName, "=", encode(sData).stringValue());
  m_vecParName.addElement(sName);
  m_vecParData.addElement(encode(sData));
//  printHex(sData, 80);
//  printHex(encode(sData).stringValue(), 80);
}


//----------------------------------------------------------------------

public String getPar (String sName)
{
  return getPar(sName, null);
}

protected String getPar (String sName, String sDef)
{
  int n = m_vecParName.size();
  for (int i = 0; i < n; i++) {
    if (sName.equalsIgnoreCase((String)m_vecParName.elementAt(i))) {
      if (m_vecParData.elementAt(i) instanceof CHAR)
        return ((CHAR) m_vecParData.elementAt(i)).stringValue();
      else
        return (String)m_vecParData.elementAt(i);
    }
  }
  return sDef;
}

protected void prepareParams( ServletRequest sr, String sPath )
{
  String sLogname = null;
  TreeMap params = new TreeMap();
  // sort the names - it appears that we have to do it for some silly
  // applications like WebRecruiter, which uses arguments positions to
  // determine their meaning
  shell.log(3,"=> prepareParams: "+sPath);
  for ( Enumeration e = sr.getParameterNames(); e.hasMoreElements(); )
    params.put(e.nextElement(), "");

  for ( Iterator keys = params.keySet().iterator(); keys.hasNext(); )
  {
    String sKey = (String)keys.next();
    String[] asVal = sr.getParameterValues(sKey);
    shell.log(3,"=> sKey: "+sKey);      
    if (asVal != null) 
    {
      if ((sKey.equalsIgnoreCase("ln")||sKey.equalsIgnoreCase("id_")) && sPath != null)
      {
        for (int i = 0; i < asVal.length; i++)
        {
//           sLogname = (asVal[i].startsWith("/") ? sPath + asVal[i] : sPath + '/' + asVal[i]);
           sLogname = (asVal[i].startsWith("/") ? asVal[i] :  asVal[i]);
           shell.log(3,"=> sLogname: "+sLogname);
           appPar(sKey, unescape(sLogname, this.effCharset));
        }
      }
      else 
      {
        for (int i = 0; i < asVal.length; i++)
          appPar(sKey, unescape(asVal[i], this.effCharset));
          shell.log(3,"=> 2sKey: "+sKey);  
      }
    }
  }
  if (sPath != null && sLogname == null){
    if(pi.iCount < 3) sPath = this.pi.getItem(pi.iCount);
    shell.log(3,"=> prepareParams: ln="+sPath);
    appPar("ln", sPath);
    }

}

protected void prepareParams( ServletRequest sr )
{
  prepareParams(sr, null);
}
public  void parseHeader (byte[] auBuf, int iOfs, int iLen)
{
  int i;
  String sPair, sName, sData;
  try {
    StringTokenizer st = new StringTokenizer(new String(auBuf, iOfs, iLen, effCharset), ";");
    if (st.hasMoreTokens()) {
      sPair = st.nextToken().trim();
      i = sPair.indexOf(':');
      if (i > 0) {
        sName = sPair.substring(0, i).trim();
        sData = sPair.substring(i+2).trim();
        partHeader(sName, sData);
        while (st.hasMoreTokens()) {
          sPair = st.nextToken().trim();
          i = sPair.indexOf('=');
          sName = (i > 0 ? sPair.substring(0, i).trim() : "");
          sData = sPair.substring(i+1).trim();
          i = sData.length();
          if (sData.charAt(0) == '\"') sData = sData.substring(1, i-1);
          partSubheader(sName, sData);
          /*
                  if (sName.equalsIgnoreCase("Content-Disposition")){}
                  else if (sName.equalsIgnoreCase("Content-Type")){}
                  else prepareAnyParams(sName,sData);*/
        }
      }
    }
  }
  catch (UnsupportedEncodingException e) {}
}

//----------------------------------------------------------------------
// Responses:
//----------------------------------------------------------------------

protected void respErrorPage
( int     iStatus
, String  sErr
)
{
  shell.log(0, sErr);
  try
  {
    StringWriter sw = new StringWriter();
    PrintWriter out = new PrintWriter(sw);
    out.println("<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">");
    out.println("<html><head><title>Error Page</title></head>");
    out.println("<body bgcolor=\"#ffffff\" text=\"#c00000\">");
    out.print("<b><pre>");
    out.print(sErr);
    out.println("</pre></b>");
    out.println("</body></html>");
    out.close();
    this.response.sendError(iStatus, sw.toString());
  }
  catch (IOException e) {}
}

//----------------------------------------------------------------------
// Multipart support:
//----------------------------------------------------------------------

protected int doMultipart (String sContentType)
throws ServletException, IOException, JOPAException
{
  final int iDONE = -1, iIDLE = 0, iDATA = 1, iHEAD = 2;

  final byte[] CRLF = { (byte)'\r', (byte)'\n' };
  final byte[] HYHY = { (byte)'-',  (byte)'-' };

  shell.log(4, "doMultipart: Content-Type: "+sContentType);

  byte[] auBoundary = extractBoundary(sContentType);
  if (auBoundary == null) { throw new JOPAException("doMultipart", "No Boundary"); }
  int iBoundary = java.lang.reflect.Array.getLength(auBoundary);

  int iContentLength = this.request.getContentLength();
  shell.log(4, "doMultipart: Content-Length: ", Integer.toString(iContentLength));

  int iCount = 0;
  int iLimit = 0;
  try { iLimit = Integer.parseInt(this.props.getProperty("upload_limit", "0"), 10); }
  catch (NumberFormatException e) { iLimit = 0; }

  ServletInputStream sis = this.request.getInputStream();
  byte[]  auBuf = new byte[BUFFER_SIZE];
  int     iBuf = BUFFER_SIZE;
  int     iLen = 2;
  int     iStat = iIDLE;
  int     iOfs = 0;
  boolean bChange = true;
  int     i, n, iCnt;

  auBuf[0] = CRLF[0];  //(byte)'\r';
  auBuf[1] = CRLF[1];  //(byte)'\n';

  while (bChange && (iStat >= 0)) {
    bChange = false;

    if ((iContentLength > 0) && (iLen < iBuf)) {
      n = sis.read(auBuf, iLen, (iBuf - iLen));
      if (n > 0) {
        iLen += n;
        iContentLength -= n;
        bChange = true;
      }
    }

    if (iStat == iIDLE || iStat == iDATA) {
      i = findPattern(auBuf, iOfs, iLen, auBoundary, iBoundary);
      if (i < iOfs) i = iLen;
      if ((i > iOfs) && (iStat == iDATA)) {
        iCnt = i - iOfs;
        if (m_iPartType != PT_STRING) {
          iCount += iCnt;
          if (iLimit > 0 && iCount > iLimit) {
            try { sis.close(); } catch (IOException e) {}
            partDataEnd(0);
            respErrorPage(200, "Sorry, upload limit " +
                               Integer.toString(iLimit) +
                               " exhausted.");
            return 0;
          }
        }
        partData(auBuf, iOfs, iCnt);
      }
      iOfs = i;
      if ((iOfs + iBoundary + 2) <= iLen) {
        if (iStat == iDATA) partDataEnd(1);
        partBoundary();
        iCount = 0;
        iStat = iHEAD;
        iOfs += iBoundary;
        if (checkPattern(auBuf, iOfs, iLen, HYHY, 2)) {
          iOfs += 2;
          iStat = iDONE;
          break;
        }
        else if (checkPattern(auBuf, iOfs, iLen, CRLF, 2)) {
          iOfs += 2;
        }
      }
    }

    if (iStat == iHEAD) {
      i = findPattern(auBuf, iOfs, iLen, CRLF, 2);
      if (i == iOfs) {
        partDataStart();
        iOfs += 2;
        iStat = iDATA;
      }
      else if (i > 0) {
        parseHeader(auBuf, iOfs, (i-iOfs));
        iOfs = i + 2;
      }
    }

    if (iOfs > 0) {
      if (iOfs < iLen) {
        n = iLen - iOfs;
        System.arraycopy(auBuf, iOfs, auBuf, 0, n);
        iLen = n;
      }
      else {
        iLen = 0;
      }
      iOfs = 0;
      bChange = true;
    }

  } // while

  sis.close();
  return 1;
}

//----------------------------------------------------------------------

protected boolean doUpload(String sFName, BLOB content, String mimetype)
throws ServletException, IOException, JOPAException
{
  boolean b = true;
  String stmt = shell.getCode("doUpload",
    "{T}", this.props.getProperty("document_table", "NN$T_DOWNLOAD"));
  try {
    OracleCallableStatement cs = (OracleCallableStatement)m_conn.prepareCall(stmt);
    cs.setString(1, sFName);
    cs.setString(2, mimetype);
    cs.setBLOB(3,   content);
    cs.execute();
    cs.close();
  }
  catch(SQLException e) {
    b = false;
    shell.log(0, "doUpload/SQL: ", e.getMessage());
  }
  shell.log(4, "Uploaded: ", sFName);
  return b;
}

//----------------------------------------------------------------------

private String genFName (String sName)
{
  String sPrefix = this.props.getProperty("document_prefix","*");
  if (sPrefix.equals("*"))
    try {
      OracleCallableStatement cs =
        (OracleCallableStatement)m_conn.prepareCall(
          "begin ? := 'H' || rawtohex(sys_guid); end;");
      cs.registerOutParameter(1, OracleTypes.VARCHAR);
      cs.execute();
      sPrefix = cs.getString(1);
      cs.close();
    }
    catch(SQLException e) {
      shell.log(0, "genFName/SQLExeption: ", e.getMessage());
    }
  return sPrefix + '/' + sName;
}

//----------------------------------------------------------------------

private void partHeader (byte[] auBuf, int iOfs, int iLen)
{
  int i;
  String sPair, sName, sData;
  try {
    StringTokenizer st = new StringTokenizer(new String(auBuf, iOfs, iLen, this.effCharset), ";");
    if (st.hasMoreTokens()) {
      sPair = st.nextToken().trim();
      i = sPair.indexOf(':');
      if (i > 0) {
        sName = sPair.substring(0, i).trim();
        sData = sPair.substring(i+2).trim();
        partHeader(sName, sData);
        while (st.hasMoreTokens()) {
          sPair = st.nextToken().trim();
          i = sPair.indexOf('=');
          sName = (i > 0 ? sPair.substring(0, i).trim() : "");
          sData = sPair.substring(i+1).trim();
          i = sData.length();
          if (sData.charAt(0) == '\"') sData = sData.substring(1, i-1);
          partSubheader(sName, sData);
        }
      }
    }
  }
  catch (UnsupportedEncodingException e) {}
}

//----------------------------------------------------------------------

private String       m_sLastHeader;
private String       m_sParName;
private int          m_iPartType;
private String       m_sFilename;
private String       m_sMimeType;
private BLOB         m_content;
private OutputStream m_bos;

//----------------------------------------------------------------------

private void partBoundary ()
{
  m_sLastHeader = "";
  m_sParName    = "";
  m_iPartType   = PT_STRING;
  m_sFilename   = "";
  m_sMimeType   = "application/octet-stream";
  m_content     = null;
  m_bos         = null;
}

//----------------------------------------------------------------------

private void partHeader (String sName, String sData)
{
  //shell.log(3, "<= ", sName, ": ", sData);
  m_sLastHeader = sName;
  if (m_sLastHeader.equalsIgnoreCase("Content-Disposition")) {
    return;
  }
  if (m_sLastHeader.equalsIgnoreCase("Content-Type")) {
    m_sMimeType = sData;
    return;
  }
}

//----------------------------------------------------------------------

private void partSubheader (String sName, String sData)
{
  if (m_sLastHeader.equalsIgnoreCase("Content-Disposition")) {
    if (sName.equalsIgnoreCase("name")) {
      m_sParName = sData;
    }
    else if (sName.equalsIgnoreCase("filename")) {
      m_sFilename = sData;
    }
  }
}

//----------------------------------------------------------------------

private void partDataStart ()
{
  if (m_sLastHeader.equalsIgnoreCase("Content-Type") &&
      m_sFilename.length() > 0) {
    try {
      m_content = obtainBlobTemp();
      if (m_content != null) {
        m_bos = m_content.getBinaryOutputStream();
        //m_bos = m_content.setBinaryStream(0);
        if (m_bos != null) {
          m_iPartType = (m_sMimeType.startsWith("text") ? PT_TEXT : PT_BINARY);
        }
      }
    }
    catch (SQLException e) {
      shell.log(0, "partDataStart/SQL: ", e.getMessage());
    }
  }
}

//----------------------------------------------------------------------

private void partData (byte[] auBuf, int iOfs, int iLen)
throws JOPAException
{
  if (m_iPartType == PT_STRING) {
    try { appPar(m_sParName, new String(auBuf, iOfs, iLen, this.effCharset)); }
    catch (UnsupportedEncodingException e) {}
  }
  else {
    try {
      m_bos.write(auBuf, iOfs, iLen);
    }
    catch (IOException ioe) {
      shell.log(0, "partData/IO: ", ioe.getMessage());
    }
  }
}

//----------------------------------------------------------------------

private void partDataEnd (int i)
throws ServletException, IOException, JOPAException
{
  if (m_iPartType != PT_STRING) {
    try { m_bos.flush(); m_bos.close(); } catch (IOException e) {}
    if (i > 0) {
      File file = new File(m_sFilename);
      String sFName = genFName(file.getName());
      if (doUpload(sFName, m_content, m_sMimeType)) {
        appPar(m_sParName, sFName);
      }
    }
    if (m_content != null) releaseBlobTemp(m_content);
    m_content  = null;
    m_bos      = null;
  }
}


//----------------------------------------------------------------------

private void uploadBinaryChunk (byte[] auBuf, int iOfs, int iLen)
{
  if (iLen <= 32) {
    printHex(auBuf, iOfs, iLen);
  }
  else {
    printHex(auBuf, iOfs, 32);
  }
}

//----------------------------------------------------------------------

private static int findPattern(byte[] auBuf, int iOfs, int iBuf,
                               byte[] auPat, int iPat)
{
  int i, n;
  if ((iBuf <= 0) || (auBuf == null) ||
      (iPat <= 0) || (auPat == null)) return -1;
  for (; iOfs < iBuf; iOfs++) {
    if (auBuf[iOfs] == auPat[0]) {
      n = iBuf - iOfs;
      if (n > iPat) n = iPat;
      for (i = 1; i < n; i++) {
        if (auBuf[iOfs + i] != auPat[i]) break;
      }
      if (i == n) return iOfs;
    }
  }
  return -1;
}

/**
 *  Checks whether the pattern auPat[iPat] exists in auBuf[iBuf] at iOfs
 *
 *  @param auBuf	buffer to search
 *  @param iOfs		offset from the start of auBuf
 *  @param iBuf         buffer length
 *  @param auPat        pattern
 *  @param iPat         pattern length
 *
 *  @return <code>true</code> if the pattern matched, <code>false</code> otherwise
 */
private static boolean checkPattern(byte[] auBuf, int iOfs, int iBuf,
                                    byte[] auPat, int iPat)
{
  if ((iBuf <= 0) || (auBuf == null) ||
      (iPat <= 0) || (auPat == null)) return false;
  if ((iOfs + iPat) > iBuf) return false;
  for (int i = 0; i < iPat; i++) {
    if (auBuf[iOfs + i] != auPat[i]) return false;
  }
  return true;
}

//----------------------------------------------------------------------

private byte[] extractBoundary (String sContentType)
{
  int bloc = sContentType.indexOf("boundary=");
  if (bloc < 0) { return null; }
  int bend = sContentType.indexOf(' ', bloc);
  bloc += 9;
  String boundary = "\r\n--" +
   (bend < 0 ? sContentType.substring(bloc) : sContentType.substring(bloc, bend));
  try { return boundary.getBytes(this.effCharset); }
  catch (UnsupportedEncodingException e) {}
  return null;
}

final static int PT_STRING = 0;
final static int PT_TEXT   = 1;
final static int PT_BINARY = 2;

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.18 $";
} // class Processor_PSP

//----------------------------------------------------------------------
