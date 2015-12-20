//----------------------------------------------------------------------
// WEBDAV.PUT.java
//
// $Id: PUT.java,v 1.6 2005/04/19 12:19:53 Bob Exp $
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

public class PUT extends WEBDAV
implements JOPAMethod
{

//----------------------------------------------------------------------

private final byte[] ATTR_BEGIN =
  { '<', '%', '@', 'a', 't', 't', 'r', 'i', 'b', 'u', 't', 'e', 's',
    0x0D, 0x0A };
private final byte[] ATTR_END = { '%', '>', 0x0D, 0x0A };

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
      releaseConnection();
      return;
    }
    String sDisp, sType, sn;
    NUMBER nKey = locatePath(this.sReqPath);
    if (nKey == null) {
      sDisp = guessDisp(this.sReqName);
      if (sDisp.equals("DAT")) {
        sType = guessDataType(this.sReqName);
        if (sType.startsWith("text/")) {
          BLOB blob = obtainBlobTemp();
          if (blob != null) {
            Properties prop = new Properties();
            long lSize = loadUnitBLOB(prop, blob);
            if (lSize >= 0) {
              if (prop.size() == 0) {
                nKey = insertData(nDir, this.sReqName, blob, sType);
              }
              else {
                adjustAttributes(prop);
                nKey = insertUnitBLOB(nDir, this.sReqName, blob, prop);
              }
            }
            freeTemporary(blob);
          }
        }
        else {
          BLOB blob = readContentBlob();
          if (blob != null) {
            nKey = insertData(nDir, this.sReqName, blob, sType);
            freeTemporary(blob);
          }
        }
      }
      else if (sDisp.equals("PSP")) {
        BLOB blob = obtainBlobTemp();
        if (blob != null) {
          Properties prop = new Properties();
          long lSize = loadUnitBLOB(prop, blob);
          if (lSize >= 0) {
            adjustAttributes(prop);
            nKey = insertUnitBLOB(nDir, this.sReqName, blob, prop);
          }
          freeTemporary(blob);
        }
      }
      if (nKey != null) {
        respStatus(201, HTTP_CREATED);
      }
      else {
        respStatus(409, HTTP_CONFLICT);
      }
    }
    else {
      sDisp = getDisp(nKey, "DIR");
      if (sDisp.equals("DIR")) {
        respStatus(409, HTTP_CONFLICT);
      }
      else if (sDisp.equals("NUL")) {
        sDisp = guessDisp(this.sReqName);
        if (sDisp.equals("DAT")) {
          sType = guessDataType(this.sReqName);
          if (sType.startsWith("text/")) {
            BLOB blob = obtainBlobTemp();
            if (blob != null) {
              Properties prop = new Properties();
              long lSize = loadUnitBLOB(prop, blob);
              if (lSize >= 0) {
                if (prop.size() == 0) {
                  insertData(nDir, this.sReqName, blob, sType);
                }
                else {
                  adjustAttributes(prop);
                  insertUnitBLOB(nDir, this.sReqName, blob, prop);
                }
              }
              freeTemporary(blob);
            }
          }
          else {
            BLOB blob = readContentBlob();
            if (blob != null) {
              insertData(nDir, this.sReqName, blob, sType);
              freeTemporary(blob);
            }
          }
        }
        else if (sDisp.equals("PSP")) {
          BLOB blob = obtainBlobTemp();
          if (blob != null) {
            Properties prop = new Properties();
            long lSize = loadUnitBLOB(prop, blob);
            if (lSize >= 0) {
              adjustAttributes(prop);
              insertUnitBLOB(nDir, this.sReqName, blob, prop);
            }
            freeTemporary(blob);
          }
        }
        else {
          respStatus(409, HTTP_CONFLICT);
          return;
        }
        respStatus(200, HTTP_OK);
      }
      else {
        NUMBER nRef = getRef(nKey);
        if (nRef == null) {
          respStatus(409, HTTP_CONFLICT);
        }
        else {
          if (sDisp.equals("DAT")) {
            BLOB blob = readContentBlob();
            if (blob == null) {
              respStatus(409, HTTP_CONFLICT);
              return;
            }
            updateData(nRef, blob);
            freeTemporary(blob);
          }
          else if (sDisp.equals("PSP")) {
            Properties prop = new Properties();
            BLOB blob = obtainBlobTemp();
            long lSize = loadUnitBLOB(prop, blob);
            if (lSize < 0) {
              respStatus(409, HTTP_CONFLICT);
              return;
            }
            updateUnitBLOB(nRef, blob, prop);
            freeTemporary(blob);
          }
          respStatus(200, HTTP_OK);
        }
      }
    }
  }
  catch (JOPAException e) {
    respFatal(500, e);
  }
  catch (SQLException e) {
    respFatal(500, e);
  }
  finally {
    releaseConnection();
  }
}

//----------------------------------------------------------------------

protected void adjustAttributes (Properties prop)
{
  String s = prop.getProperty("NAME");
  if (s == null ? true : s.length() == 0 ? true : false) {
    prop.setProperty("NAME", this.sReqName);
  }
  s = prop.getProperty("DOMAIN");
  if (s == null ? true : s.length() == 0 ? true : false) {
    prop.setProperty("DOMAIN",
          props.getProperty("username", "SYSTEM").toUpperCase());
  }
  s = prop.getProperty("CODE_TYPE");
  if (s == null ? true : s.length() == 0 ? true : false) {
    prop.setProperty("CODE_TYPE", guessCodeType(this.sReqName));
  }
  s = prop.getProperty("PROCESSOR");
  if (s == null ? true : s.length() == 0 ? true : false) {
    prop.setProperty("PROCESSOR",
      (prop.getProperty("CODE_TYPE", "PSP").equals("PSP") ? "DPSP" : "FLAT"));
  }
}

//----------------------------------------------------------------------

protected long loadUnitBLOB (Properties prop, BLOB blob)
{
  long nSize = 0;
  int  stat = 0;
  byte[] buf = new byte[16];
  try {
    OutputStream os = blob.getBinaryOutputStream();
    //OutputStream os = blob.setBinaryStream(0);
    InputStream is = this.request.getInputStream();
    int ch = is.read();
    while (ch >= 0) {
      if (((byte)ch) == ATTR_BEGIN[stat]) {
        buf[stat++] = (byte)ch;
        if (stat == 13) {
          stat = 0;
          parseAttr(prop, is);
          ch = is.read();
          while (ch == '\n' || ch == '\r') ch = is.read();
        }
        else {
          ch = is.read();
        }
      }
      else {
        if (stat > 0) { os.write(buf, 0, stat); nSize += stat; stat = 0; }
        os.write(ch);  nSize += 1;
        ch = is.read();
      }
    }
    is.close();
    os.flush();
  }
  catch(IOException e) {
    shell.log(0, "loadUnitBLOB/IO: ", e.getMessage());
    nSize = -1;
  }
  catch (SQLException e) {
    shell.log(0, "loadUnitBLOB/SQL: ", e.getMessage());
    nSize = -1;
  }
  return nSize;
}

//----------------------------------------------------------------------

private NUMBER insertUnitBLOB
( NUMBER     nDir
, String     sName
, BLOB       blob
, Properties prop
) throws JOPAException
{
  NUMBER nKey = null;

  // Determine reference ID:
  NUMBER nRef = getUnitIDAttr(prop);
  if (nRef == null) { // Try to resolve name:
    String sUnitName = prop.getProperty("NAME");
    if (sUnitName == null) sUnitName = sName;
    if (sUnitName != null) nRef = resolveUnitName(sUnitName);
    if (nRef == null) nRef = getFreeUnitID();  // Generate new ID:
  }

  String stmt = shell.getCode("insertUnitBlobDAV");
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(stmt);
    cs.setNUMBER(1, nDir);
    cs.setCHAR(2, encode(sName));
//    cs.setString(2, sName);
    cs.setBLOB(3, blob);
    cs.setCHAR(4, encode(mergeAttr(prop)));
//    cs.setString(4, mergeAttr(prop));
    cs.setNUMBER(5, nRef);
    cs.registerOutParameter(6, OracleTypes.NUMBER);
    cs.executeUpdate();
    nKey = cs.getNUMBER(6);  if (cs.wasNull()) nKey = null;
    cs.close();
  }
  catch(SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "insertUnitBLOB/SQL: ", e.getMessage());
  }
  return nKey;
}

//----------------------------------------------------------------------

private void updateUnitBLOB (NUMBER nRef, BLOB blob, Properties prop)
throws JOPAException
{
  String stmt = shell.getCode("updateUnitBlobDAV");
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(stmt);
    cs.setNUMBER(1, nRef);
    cs.setBLOB(2, blob);
    cs.setCHAR(3, encode(mergeAttr(prop)));
//    cs.setString(3, mergeAttr(prop));
    cs.executeUpdate();
    cs.close();
  }
  catch(SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "updateUnitBLOB/SQL: ", e.getMessage());
  }
}

//----------------------------------------------------------------------

protected NUMBER resolveUnitName (String sName)
{
  NUMBER nRef = null;
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(
        "begin ? := NN$PSP_UNIT.resolveName(?, USER); end;");
    cs.registerOutParameter(1, OracleTypes.NUMBER);
    cs.setCHAR(2, encode(sName));
//    cs.setString(2, sName);
    cs.execute();
    nRef = cs.getNUMBER(1);
    if (cs.wasNull()) nRef = null;
    cs.close();
  }
  catch (SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "resolveUnitName/SQL: ", e.getMessage());
  }
  return nRef;
}

//----------------------------------------------------------------------

protected NUMBER getUnitIDAttr(Properties prop)
{
  String sRef = prop.getProperty("ID");
  if (sRef != null) {
    try { return new NUMBER(sRef, 0); }
    catch(SQLException e1) {}
    catch(NumberFormatException e2) {}
  }
  return null;
}

//----------------------------------------------------------------------

private String mergeAttr (Properties prop)
{
  String sName, sData;
  int i, iLen;
  char ch;
  StringBuffer sb = new StringBuffer(4096);
  Enumeration en = prop.propertyNames();
  while (en.hasMoreElements()) {
    sName = (String) en.nextElement();
    sData = prop.getProperty(sName);
    if (sData == null) { sData = ""; }
    sb.append(' ');
    sb.append(sName);
    sb.append("=\"");
    iLen = sData.length();
    for (i = 0; i < iLen; i++) {
      ch = sData.charAt(i);
      if (ch == '\"') sb.append('\\');
      sb.append(ch);
    }
    //sb.append(sData);
    sb.append("\"\r\n");
  }
  return sb.toString();
}

//----------------------------------------------------------------------

protected NUMBER getFreeUnitID()
{
  NUMBER nRef = null;
  try {
    OracleCallableStatement cs =
        (OracleCallableStatement)m_conn.prepareCall(
      "begin ? := NN$PSP_UNIT.getFreeUnitID; end;");
    cs.registerOutParameter(1, OracleTypes.NUMBER);
    cs.execute();
    nRef = cs.getNUMBER(1);
    if (cs.wasNull()) nRef = null;
    cs.close();
  }
  catch (SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "getFreeUnitID/SQL: ", e.getMessage());
  }
  return nRef;
}

//----------------------------------------------------------------------

private NUMBER insertData
( NUMBER nDir
, String sName
, BLOB   blob
, String sType
) throws JOPAException
{
  NUMBER nKey = null;
  String stmt = shell.getCode("insertDataDAV",
    "{T}", this.props.getProperty("document_table", "NN$T_DOWNLOAD"));
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(stmt);
    cs.setNUMBER(1, nDir);
    cs.setCHAR(2, encode(sName));
//    cs.setString(2, sName);
    cs.setBLOB(3, blob);
    cs.setString(4, sType);
    cs.registerOutParameter(5, OracleTypes.NUMBER);
    cs.executeUpdate();
    nKey = cs.getNUMBER(5);  if (cs.wasNull()) nKey = null;
    cs.close();
  }
  catch(SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "insertData/SQL: ", e.getMessage());
  }
  return nKey;
}

//----------------------------------------------------------------------

private void updateData (NUMBER nRef, BLOB blob)
throws JOPAException
{
  String stmt = shell.getCode("updateDataDAV",
    "{T}", this.props.getProperty("document_table", "NN$T_DOWNLOAD"));
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(stmt);
    cs.setBLOB(1, blob);
    cs.setNUMBER(2, nRef);
    cs.executeUpdate();
    cs.close();
  }
  catch(SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    shell.log(0, "updateData: SQL: ", e.getMessage());
  }
}

//----------------------------------------------------------------------
/*
private void parseAttr (Properties prop, Reader rdr)
throws IOException
{
  StringBuffer sbName = new StringBuffer(64);
  StringBuffer sbData = new StringBuffer(4096);
  int stat = 0;
  int ch   = 32;
  int ch1  = rdr.read();
  while (ch >= 0 && stat >= 0) {
    switch(stat) {
      case 0:  // space before NAME
        if (sbData.length() > 0) {
          if (sbName.length() > 0) {
            prop.setProperty(sbName.toString(), sbData.toString());
            shell.log(1, sbName.toString(), "=", sbData.toString());
            sbName.setLength(0);
          }
          sbData.setLength(0);
        }
             if (ch == '%' && ch1 == '>') { stat = -1; }
        else if (ch >= 0 && ch <= 32) {}
        else if (ch == '=') { stat = 3; }
        else if (ch == '\"') { stat = 4; }
        else { stat = 1; sbName.setLength(0); sbName.append((char)ch); }
        break;

      case 1:  // NAME
             if (ch == '%' && ch1 == '>') { stat = -1; }
        else if (ch >= 0 && ch <= 32) { stat = 2; }
        else if (ch == '=') { stat = 3; }
        else if (ch == '\"') { stat = 4; }
        else { sbName.append((char)ch);  }
        break;

      case 2:  // space before '='
             if (ch == '%' && ch1 == '>') { stat = -1; }
        else if (ch >= 0 && ch <= 32) {}
        else if (ch == '=') { stat = 3; }
        else if (ch == '\"') { stat = 4; }
        else { stat = 5; sbData.append((char)ch); }
        break;

      case 3:  // space before DATA
             if (ch == '%' && ch1 == '>') { stat = -1; }
        else if (ch >= 0 && ch <= 32) {}
        else if (ch == '=') {}
        else if (ch == '\"') { stat = 4; }
        else { stat = 5; sbData.append((char)ch); }
        break;

      case 4:  // "DATA"
             if (ch == '\"') { stat = 0; }
        else if (ch == '\\' && ch1 > 32)
          { sbData.append((char)ch1);  ch1 = rdr.read(); }
        else { sbData.append((char)ch); }
        break;

      case 5:  // DATA
        sbb.append(ch); shell.log(1, "{", sbb.toString()); sbb.setLength(0);
             if (ch == '%' && ch1 == '>') { stat = -1; }
        else if (ch >= 0 && ch <= 32) { stat = 0; }
        else { sbData.append((char)ch); }
        break;
    }
    // move one step forward:
    ch  = ch1;
    ch1 = rdr.read();
  }
  if (sbData.length() > 0 && sbName.length() > 0) {
    prop.setProperty(sbName.toString(), sbData.toString());
  }
}
*/
//----------------------------------------------------------------------

private void parseAttr (Properties prop, InputStream is)
throws IOException
{
  StringBuffer sbName = new StringBuffer(64);
  StringBuffer sbData = new StringBuffer(4096);
  int stat = 0;
  int ch   = 32;
  int ch1  = is.read();
  while (ch >= 0 && stat >= 0) {
    switch(stat) {
      case 0:  // space before NAME
        if (sbData.length() > 0) {
          if (sbName.length() > 0) {
            prop.setProperty(sbName.toString(), sbData.toString());
            sbName.setLength(0);
          }
          sbData.setLength(0);
        }
              if (ch == '%' && ch1 == '>') { stat = -1; }
        else if (ch >= 0 && ch <= 32) {}
        else if (ch == '=') { stat = 3; }
        else if (ch == '\"') { stat = 4; }
        else { stat = 1; sbName.setLength(0); sbName.append((char)ch); }
        break;

      case 1:  // NAME
             if (ch == '%' && ch1 == '>') { stat = -1; }
        else if (ch >= 0 && ch <= 32) { stat = 2; }
        else if (ch == '=') { stat = 3; }
        else if (ch == '\"') { stat = 4; }
        else { sbName.append((char)ch);  }
        break;

      case 2:  // space before '='
             if (ch == '%' && ch1 == '>') { stat = -1; }
        else if (ch >= 0 && ch <= 32) {}
        else if (ch == '=') { stat = 3; }
        else if (ch == '\"') { stat = 4; }
        else { stat = 5; sbData.append((char)ch); }
        break;

      case 3:  // space before DATA
             if (ch == '%' && ch1 == '>') { stat = -1; }
        else if (ch >= 0 && ch <= 32) {}
        else if (ch == '=') {}
        else if (ch == '\"') { stat = 4; }
        else { stat = 5; sbData.append((char)ch); }
        break;

      case 4:  // "DATA"
        //sbb.append(ch); shell.log(1, "|", sbb.toString()); sbb.setLength(0);
             if (ch == '\"') { stat = 0; }
        else if (ch == '\\' && ch1 > 32)
          { sbData.append((char)ch1);  ch1 = is.read(); }
        else { sbData.append((char)ch); }
        break;

      case 5:  // DATA
             if (ch == '%' && ch1 == '>') { stat = -1; }
        else if (ch >= 0 && ch <= 32) { stat = 0; }
        else { sbData.append((char)ch); }
        break;
    }
    // move one step forward:
    ch  = ch1;
    ch1 = is.read();
  }
  if (sbData.length() > 0 && sbName.length() > 0) {
    prop.setProperty(sbName.toString(), sbData.toString());
  }
}

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.6 $";
} // class PUT

//----------------------------------------------------------------------
