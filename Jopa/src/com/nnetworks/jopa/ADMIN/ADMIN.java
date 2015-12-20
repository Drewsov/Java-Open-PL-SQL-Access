//----------------------------------------------------------------------
// ADMIN.java
//
// $Id: ADMIN.java,v 1.7 2005/02/11 18:39:54 Bob Exp $
// 
//----------------------------------------------------------------------
   
package com.nnetworks.jopa.ADMIN; 

import com.nnetworks.jopa.ADMIN.*;
import com.nnetworks.jopa.*;
import com.nnetworks.resources.ResourcePool;

import javax.servlet.*;
import javax.servlet.http.*;

import java.sql.*;
import java.io.*;
import java.text.*;
import java.util.*;
import java.net.URL;    
   
//----------------------------------------------------------------------

public class ADMIN extends Processor_PSP 
implements JOSPServer
{

//----------------------------------------------------------------------
// Entry point:
//----------------------------------------------------------------------

protected void doProc()
throws ServletException, IOException, JOPAException
{
  ensureCodes();
  if (!authorize()) {
    respUnauthorized("Please introduce yourself to JOPA Admin");
    return;
  }
  String sPage = this.pi.getItem(1);
  shell.log(2, "Page: ", sPage);
  StringBuffer sbRes = new StringBuffer(4096);
  if (JOSP.processPage(sbRes, this, sPage) < 0) {
    sayErrorPage(sbRes, "JOPA Gateway Servlet Live Administration", "Failed to process page " + sPage);
  }
  respPage(sbRes);
}

//----------------------------------------------------------------------
// Codes:
//----------------------------------------------------------------------

protected static ResourcePool m_codes = null;

//----------------------------------------------------------------------

protected void ensureCodes ()
throws ServletException
{
  if (m_codes == null) {
    loadCodes();
    if (m_codes == null) {
      shell.log(0, "Failed to load resources");
      throw new ServletException("Failed to load resources");
    }
  }
}

//----------------------------------------------------------------------

protected void loadCodes ()
{
  URL url =
    this.getClass().getClassLoader().getResource("com/nnetworks/jopa/ADMIN/htmlcodes.properties");
  if ( url == null ) {
    shell.log(0, "Failed to load resources");
    return;
  }
  ResourcePool pool = new ResourcePool();
  pool.setSkipChars("\0");
  try { pool.consultTextPool(url); }
  catch(IOException e) {
    shell.log(0, "Failed to load resources");
    return;
  }
  m_codes = pool;
}

//----------------------------------------------------------------------
// Authorization:
//----------------------------------------------------------------------

protected boolean authorize ()
{
  // Obtain authorization header:
  String sAuth = this.request.getHeader("Authorization");
  if (sAuth == null) sAuth = this.request.getHeader("HTTP_AUTHORIZATION");
  if (sAuth == null) return false;

  // Parse the authorization data:
  int i = sAuth.indexOf("Basic");
  if (i < 0) return false;
  String sCode = Base64.decodeToString(sAuth.substring(i+6), this.requestCharset);
  if (sCode == null) return false;
  i = sCode.indexOf(":");
  if (i <= 0) return false;
  String sUsername = sCode.substring(0, i).trim();
  String sPassword = sCode.substring(i+1).trim();

  // Check username/password;
  String sUserProp = this.props.getProperty("username");
  if (sUserProp == null) return true;
  if (!sUserProp.equalsIgnoreCase(sUsername)) return false;
  //---[15.04.2003 S.Ageshin changed this:
  //String sPaswProp = this.props.getProperty("password");
  String sPaswProp = Base64.decryptPasw(this.props.getProperty("password"));
  //---]
  if (sPaswProp == null) return true;
  if (!sPaswProp.equalsIgnoreCase(sPassword)) return false;
  return true;
}

//----------------------------------------------------------------------
// Responses:
//----------------------------------------------------------------------

protected void respUnauthorized (String sMsg)
{
  respStatus(401, sMsg);
  respHeader("WWW-Authenticate",
             "Basic realm=\"JOPA Administration Interface\"");
  try
  {
    PrintWriter wtr = this.response.getWriter();
    wtr.print("<html><head><title>Please introduce yourself</title></head><body><h1>401 Unauthorized</h1>You are not allowed to access this resource without proper authentication.</body></html>");
    wtr.close();
  }
  catch(IOException ioe)
  {
    shell.log(0, "respUnauthorized/IO: ", ioe.toString());
  }
}

//----------------------------------------------------------------------

protected void respPage (StringBuffer sbRes)
{
  if (sbRes == null) return;
  int n = sbRes.length();
  if (n == 0) return;
  try
  {
    this.response.setStatus(200);
    this.response.setContentLength(n);
    this.response.setContentType("text/html; charset=utf-8");
    PrintWriter wtr = this.response.getWriter();
    wtr.print(sbRes.toString());
    wtr.close();
  }
  catch (IOException e)
  {
    shell.log(0, "respPage/IO: ", e.toString());
    try
    {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      e.printStackTrace(pw);
      pw.close();
      this.response.sendError(500, sw.toString());
    }
    catch(IOException ioe) {}
  }
}

//----------------------------------------------------------------------
// JOSPServer implementation:
//----------------------------------------------------------------------

private String sParName = "";
private String sParData = null;

//----------------------------------------------------------------------

public String getPar (String sName)
{
  if (!this.sParName.equalsIgnoreCase(sName)) {
    this.sParName = sName;
    this.sParData = super.getPar(sName);
  }
  return this.sParData;
}

//----------------------------------------------------------------------

public String getEnv (String sName)
{
  return "";
}

//----------------------------------------------------------------------

public String getCode (String sName)
{
  return m_codes.getText(sName);
}

//----------------------------------------------------------------------
// "JOPA Admin" page:
//----------------------------------------------------------------------

public String doVersion (String sParam, String sText)
{
  return shell.getVersion();
}

//----------------------------------------------------------------------

public String doAdminSubmit (String sParam, String sText)
{
  int i;
  String sData = getPar(sParam);
  if (sData != null) {
    if (sData.equalsIgnoreCase("save")) {
      i = shell.saveCfg();
      if (i < 0) return "Failed to save in file.";
    }
    if (sData.equalsIgnoreCase("reload")) {
      i = shell.loadCfg();
      if (i < 0)  return "Failed to load config file.";
      if (i == 0) return "Could not find config file.";
      shell.configParameters();
    }
  }
  return sText;
}

//----------------------------------------------------------------------
// "DAD Entries" page:
//----------------------------------------------------------------------

public String doDADEntries (String sParam, String sText)
{
  StringBuffer sb = new StringBuffer(1024);
  StringBuffer sb1 = new StringBuffer(80);
  ResourcePool cfg = shell.getCfg();

  String sDAD0 = cfg.getBasePropertiesName();
  if (sDAD0 == null) {
    sDAD0 = "Config";
  }
  else {
    Properties props = cfg.getProperties(sDAD0);
    if (props != null) {
      sb1.append(sText);
      m_codes.subst(sb1, "{P}", sDAD0);
      m_codes.subst(sb1, "{M}", props.getProperty("mode", "Native"));
      m_codes.subst(sb1, "{X}", props.getProperty("username", ""));
      m_codes.subst(sb1, "{Y}", props.getProperty("host", ""));
      m_codes.subst(sb1, "{Z}", props.getProperty("sid", ""));
      sb.append(sb1);
    }
  }

  Enumeration en = cfg.enumSections();
  while (en.hasMoreElements()) {
    String sDAD = (String) en.nextElement();
         if (sDAD.equalsIgnoreCase(sDAD0)) {}
    else if (sDAD.equalsIgnoreCase("Config")) {}
    else {
      props = cfg.getProperties(sDAD);
      sb1.setLength(0);
      sb1.append(sText);
      m_codes.subst(sb1, "{P}", sDAD);
      m_codes.subst(sb1, "{M}", props.getProperty("mode", "Native"));
      m_codes.subst(sb1, "{X}", props.getProperty("username", ""));
      m_codes.subst(sb1, "{Y}", props.getProperty("host", ""));
      m_codes.subst(sb1, "{Z}", props.getProperty("sid", ""));
      sb.append(sb1);
    }
  }

  return sb.toString();
}

//----------------------------------------------------------------------

public String doDADDelete (String sParam, String sText)
{
  String sDAD = getPar("dad");
  if (sDAD != null) {
    ResourcePool cfg = shell.getCfg();
    cfg.forget(sDAD);
    shell.log(2, "Deleted: ", sDAD);
  }
  return sText;
}

//----------------------------------------------------------------------
// "DAD Entry" page:
//----------------------------------------------------------------------

public String doDADPropSubmit (String sParam, String sText)
{
  doPropSubmit(getPar("dad"), sParam);
  return "";
}

//----------------------------------------------------------------------

public String doDADProp (String sParam, String sText)
{
  return doProp(getPar("dad"), sParam, sText);
}

//----------------------------------------------------------------------

public String doDADTextProp (String sParam, String sText)
{
  return doTextProp(getPar("dad"), sParam, sText);
}

//----------------------------------------------------------------------

public String doDADListProp (String sParam, String sText)
{
  return doListProp(getPar("dad"), sParam, sText);
}

//----------------------------------------------------------------------
// "JOPA Config" page:
//----------------------------------------------------------------------

public String doCfgPropSubmit (String sParam, String sText)
{
  doPropSubmit("Config", sParam);
  return "";
}

//----------------------------------------------------------------------

public String doCfgTextProp (String sParam, String sText)
{
  return doTextProp("Config", sParam, sText);
}

//----------------------------------------------------------------------

public String doCfgListProp (String sParam, String sText)
{
  return doListProp("Config", sParam, sText);
}

//----------------------------------------------------------------------

public String doCfgApply (String sParam, String sText)
{
  shell.configParameters();
  return "";
}

//----------------------------------------------------------------------
// "JOPA Connection Pool" page:
//----------------------------------------------------------------------

public String doPoolSubmit (String sParam, String sText)
{
  if (getPar("setlimit") != null) {
    String sLimit = getPar("limit");
    try {
      int iLimit = Integer.parseInt(sLimit, 10);
      shell.getConnectionPool().setLimit(iLimit);
      return "Limit changed";
    }
    catch (NumberFormatException e) {}
  }
  else if (getPar("clearpool") != null) {
    shell.getConnectionPool().clearPool();
    return "Pool flushed";
  }
  return "";
}

//----------------------------------------------------------------------

public String doPoolLimit (String sParam, String sText)
{
  return Integer.toString(shell.getConnectionPool().getLimit());
}

//----------------------------------------------------------------------

public String doPoolTotal (String sParam, String sText)
{
  return Integer.toString(shell.getConnectionPool().countTotal());
}

//----------------------------------------------------------------------
// Support:
//----------------------------------------------------------------------

protected void doPropSubmit (String sSect, String sName)
{
  if (sSect != null && sName != null) {
    saveProp(ensureProps(sSect), sName);
  }
}

//----------------------------------------------------------------------

protected Properties ensureProps (String sSect)
{
  ResourcePool cfg = shell.getCfg();
  Properties props = cfg.getProperties(sSect);
  if (props == null) {
    String sSect0 = cfg.getBasePropertiesName();
    props = new Properties(sSect0 == null ? null :
                           sSect.equalsIgnoreCase(sSect0) ? null :
                           cfg.getProperties(sSect0));
    cfg.putObject(sSect, props);
  }
  return props;
}

//----------------------------------------------------------------------

protected int saveProp (Properties props, String sName)
{
  String sData = getPar(sName);
  int n = 0;
  if (sData == null) {
    if (props.remove(sName) != null) n++;
  }
  else if (sData.length() == 0) {
    if (props.remove(sName) != null) n++;
  }
  else {
    //---[15.04.2003 S.Ageshin added this:
    if (sName.equals("password")) {
      sData = Base64.encryptPasw(sData);
    }
    //---]
    String sData0 = props.getProperty(sName);
    if (sData0 == null) {
      props.setProperty(sName, sData);
      n++;
    }
    else if (!sData.equals(sData0)) {
      props.setProperty(sName, sData);
      n++;
    }
  }
  return n;
}

//----------------------------------------------------------------------

protected String doTextProp (String sSect, String sName, String sText)
{
  Properties props = null;
  if (sSect != null) {
    ResourcePool cfg = shell.getCfg();
    props = cfg.getProperties(sSect);
  }
  String sPatt = "textprop";
  String sData = (props == null ? "" : props.getProperty(sName, ""));
  //---[15.04.2003 S.Ageshin added this:
  if (sName.equals("password")) {
    sPatt = "paswprop";
    sData = Base64.decryptPasw(sData);
  }
  //---]
  StringBuffer sb = m_codes.getTextBuffer(sPatt, sText);
  m_codes.subst(sb, "{N}", sName);
  m_codes.subst(sb, "{V}", sData);
  m_codes.subst(sb, "{C}", props == null ? "blue" :
                           (props.containsKey(sName) ? "black" : "blue"));
  return sb.toString();
}

//----------------------------------------------------------------------

protected String doListProp (String sSect, String sName, String sText)
{
  Properties props = null;
  if (sSect != null) {
    ResourcePool cfg = shell.getCfg();
    props = cfg.getProperties(sSect);
  }
  StringBuffer sb = m_codes.getTextBuffer("listprop", "");
  m_codes.subst(sb, "{N}", sName);
  m_codes.subst(sb, "{C}", props == null ? "blue" :
                           (props.containsKey(sName) ? "black" : "blue"));
  String sData = props == null ? "" : props.getProperty(sName, "");
  StringBuffer sb1 = new StringBuffer(1024);
  StringTokenizer st = new StringTokenizer(sText, ",");
  int i;
  String s, sVal;
  while (st.hasMoreTokens()) {
    s = st.nextToken().trim();
    if ((i = s.indexOf('=')) > 0) {
      sVal = s.substring(0, i).trim();
      s = s.substring(i+1).trim();
    }
    else if ((i = s.indexOf(':')) > 0) {
      sVal = s.substring(0, i).trim();
    }
    else if ((i = s.indexOf('-')) > 0) {
      sVal = s.substring(0, i).trim();
    }
    else {
      sVal = s;
    }
    sb1.append("<OPTION value=\"");
    sb1.append(sVal);
    sb1.append('\"');
    if (sVal.equalsIgnoreCase(sData)) sb1.append(" selected");
    sb1.append(" > ");
    sb1.append(s);
    sb1.append(" </OPTION>\n");
  }
  m_codes.subst(sb, "{V}", sb1.toString());
  return sb.toString();
}

//----------------------------------------------------------------------

protected String doProp (String sSect, String sName, String sText)
{
  if (sSect != null) {
    ResourcePool cfg = shell.getCfg();
    Properties props = cfg.getProperties(sSect);
    if (props != null) {
      return props.getProperty(sName, sText);
    }
  }
  return sText;
}

//----------------------------------------------------------------------

protected void sayError (StringBuffer sbRes, String sMsg)
{
  sbRes.append("<B><PRE style=\"color:#C00000;\">");
  sbRes.append(sMsg);
  sbRes.append("</PRE></B><BR/>\n");
}

//----------------------------------------------------------------------

protected void sayErrorPage (StringBuffer sbRes, String sTitle, String sMsg)
{
  sbRes.append("<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 4.01 Transitional//EN\">\n");
  sbRes.append("<HTML><HEAD><TITLE>");
  sbRes.append(sTitle);
  sbRes.append("</TITLE></HEAD>\n");
  sbRes.append("<BODY bgcolor=\"#ffffff\" text=\"#000000\">\n");
  sayError(sbRes, sMsg);
  sbRes.append("</BODY></HTML>\n");
}

//----------------------------------------------------------------------

protected void doDownload()
throws ServletException, IOException, JOPAException
{
  /*
  String stmt = shell.getCode("doDownload",
    "{P}", this.props.getProperty("document_proc", "NN$PSP_RSP.download"));
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(stmt);

    cs.registerOutParameter(1, OracleTypes.VARCHAR);
    cs.registerOutParameter(2, OracleTypes.BLOB);

    cs.execute();

    respHeaders(cs.getString(1));
    respContent(cs.getBLOB(2));

    cs.close();
  }
  catch(SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    throw (new JOPAException("doDownload", e.getMessage()));
  }
  */
}

//----------------------------------------------------------------------

private static String fileRevision = "$Revision: 1.7 $";

} // class ADMIN

//----------------------------------------------------------------------
