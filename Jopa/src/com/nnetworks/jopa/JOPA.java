//----------------------------------------------------------------------
// JOPA.java  v.3.12.2016

//   
// $Id: JOPA.java,v v.3.12.2016 2016/01/01 00:00:00 Drew Exp $
//
//----------------------------------------------------------------------
/**
 *                                  Tuota pastori kummissaan kuuntelee,
 *                                  jopa jalkojansa vitkalleen nostelee.
 *                                  Jopa liperitkin rytmistae kiinni saa,
 *                                  iso mahakin jo jenkassa heilahtaa.
 *
 *                                        Kari Rydman, "Kanttorin jenkka"
 *
 *
 * Она предназначена прежде всего для доступа к Dynamic PSP(tm) из Интернети.
 * Слово "jopa" по-фински значит "уже", "даже", "тем более".
 *
 * Возможны несколько режимов (модусов): Native, OWA, WebDAV, Admin.
 * Каждый модус реализуется в отдельном пакете классов
 * (см. соотв.: NATIVE, OWA, WEBDAV, ADMIN).
 * Можно добавить ещё модусы.
 *
 *    
 */
//----------------------------------------------------------------------

package com.nnetworks.jopa;

import com.nnetworks.shell.Logger;
import com.nnetworks.resources.ResourcePool;

import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import java.util.*;
import java.net.URL;

//----------------------------------------------------------------------

public class JOPA extends HttpServlet
implements JOPAShell
{

//----------------------------------------------------------------------

public final static String version =
  "Dynamic PSP/HTMLDB/OWA/JSP/WEBDAV Gateway Java Servlet v.3.12.2016/XE/10g/9i/Tomcat";

//----------------------------------------------------------------------
// Variables:
//----------------------------------------------------------------------

private ConnectionPool  connpool;   // Connection pool
private ResourcePool    cfg;        // DAD pool
private ResourcePool    codes;      // SQL-code pool
private Logger          logger;     // Logger
private File            fileCfg;    // Config file
private Hashtable       shelf;      // Object shelf
private HttpServletResponse res;

//----------------------------------------------------------------------
// Servlet information:
//----------------------------------------------------------------------

public String getServletInfo ()
{
  return getVersion();
}

//----------------------------------------------------------------------
// Servlet initialization:
//----------------------------------------------------------------------

/** ***************************************************************************
 * Standard servlet initialization method, as per Servlet 2.0 API
 *
 * @param 	config	ServletConfig with current configuration information
 *
 */
public void init (ServletConfig config)
throws ServletException
{
  super.init(config);

  // Init Logger:
  this.logger = new Logger();
  this.logger.setPrefix(null);
  this.logger.setErrPrefix("**");
  int i = -1; 
  String s = getInitParameter("logfile");
  if (s != null) {
    this.logger.setLogPath(s, true);
    i = getInt(getInitParameter("loglevel"), 0);
    logger.LogLevel = getInt(getInitParameter("loglevel"),0);
  }
  this.logger.setLogLevel(i);
  i = getInt(getInitParameter("debuglevel"), -1);
  this.logger.setSysLevel(i);
  // Init configuration pool:
  s = getInitParameter("cfgfile");
  if (s == null) s = "jopa.cfg";
  this.fileCfg = new File(s);
  i = loadCfg();
  if (i == 0) { 
    try { 
    PrintWriter out = res.getWriter();}
    catch (java.lang.NullPointerException e){}
    catch (java.io.IOException e){}
    throw new ServletException("Config file does not exist:"+fileCfg.getAbsolutePath()+":"+fileCfg.getAbsolutePath());
  }
  else if (i < 0) {
    throw new ServletException("Failed to load config file "+fileCfg.getAbsolutePath());
  }

  // Configure parameters:
  configParameters();

  // Init SQL-code depot:
  URL urlCfg =
    this.getClass().getClassLoader().getResource("com/nnetworks/jopa/sqlcodes.properties");
  if ( urlCfg == null ) {
    log(0, "Failed to load resources");
    throw new ServletException("Failed to load resources");
  }
  this.codes = new ResourcePool();
  this.codes.setSkipChars("\r\0");
  try {
    this.codes.consultTextPool(urlCfg);
  }
  catch(IOException e) {
    log(0, "Failed to load resources");
    throw new ServletException("Failed to load resources");
  }

  // Init Connection pool:
  this.connpool = new ConnectionPool(16, 64, 256);

  // Init shelf:
  this.shelf = new Hashtable(16);

  // Say we started:
  this.logger.log(-1, null);
  this.logger.log(0, "JOPA Started under "+config.getServletContext().getServerInfo());
}

//----------------------------------------------------------------------
// Servlet termination:
//----------------------------------------------------------------------

/** ***************************************************************************
 * Standard servlet destructor
 *
 */
public void destroy ()
{
  if (this.logger != null) { this.logger.log(0, "JOPA Stopped"); }
  if (this.shelf != null) { closeShelf();  this.shelf = null; }
  if (this.connpool != null) { this.connpool.done();  this.connpool = null; }
  if (this.cfg != null)    { this.cfg = null; }
  if (this.codes  != null) { this.codes = null; }
  if (this.logger != null) { this.logger.closeLog();  this.logger = null; }
}

//----------------------------------------------------------------------
// Servlet service routine:
//----------------------------------------------------------------------
// HTTP Status 405 - HTTP method GET is not supported by this URL
// 
public void doGet(HttpServletRequest request, HttpServletResponse response)
	throws ServletException, IOException{
    // 	 
      service(request,response);
}
public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException,IOException{
    //
       service(request,response);          
}       
/** ***************************************************************************
 * Standard servlet request processing method.
 *
 * @param	request	request encapsulation object
 * @param	response	response encapsulation object
 *
 * @throws	ServletException
 *		when an internal (unexpected) error happens
 * @throws	IOException
 *		when I/O problem happens
 */
public void service
( HttpServletRequest  request
, HttpServletResponse response
) throws ServletException, IOException
{
  String sReqURI = request.getRequestURI();
  log(3,"----- JOPA.service -------------------------------------------");
  log(3,"request.getRequestURI: "+ sReqURI);
  String sMethod = request.getMethod();
  log(3,"request.getMethod(): "+sMethod);
  sReqURI = (new String(sReqURI.getBytes("ISO8859_1"),"UTF-8"));
  //sReqURI = Base64.decodeToString(sReqURI, this.requestCharset);
  log(3,"-> request.getRequestURI.getBytes: "+ (sReqURI));
  Processor.getServletPath = request.getServletPath();
  //log(3,"request.getRequestURL: "+ request.getRequestURL());
  log(3,"request.getServletPath: "+ Processor.getServletPath);
  res = response;
  getServletContext();
  
  // Parse request URI:
  PathInfo pi = new PathInfo(sReqURI, Processor.getServletPath);
  if (pi.iCount < 1) {
    respFatal(response, 502, "No DAD specified");
    return;
  }

  // Obtain DAD parameters:
  Properties props = this.cfg.getProperties(pi.getItem(0));
  try {
  if (props == null) {
        Processor.getServletPath = "/dpsp";
        sReqURI = replace(sReqURI,"/jpls","/jpls"+Processor.getServletPath);  
        log(3,"setup request.getRequestURI: "+ sReqURI);
        //setServletPath
        pi = new PathInfo(sReqURI, "/dpsp");
        props = this.cfg.getProperties(pi.getItem(0));
    }
  if (props == null) {
    respFatal(response, 502, "Unknown DAD: " + pi.getItem(0));
    return;
  }
  }
  catch (Exception e){respFatal(response, 502, "Unknown DAD ");}

  StringBuffer sb = new StringBuffer(128);
  // Obtain mode & method:
  String sMode = props.getProperty("mode", "native");

  Processor.FILE_TYPES    =  getInitParameter("file types");
  Processor.SCRIPT_TYPES  =  getInitParameter("script types");
  Processor.SCRIPT_PREFIX =  getInitParameter("script prefix");
  Processor.MIME_TYPES    =  getInitParameter("mime mapping file");
  
  
  if((sMode.toUpperCase().equals("UDAV"))||(sMode.toUpperCase().equals("WEBDAV"))){
     sReqURI = replace(sReqURI,"/index.html","");
          log(3,sMode.toUpperCase()+".sReqURI: "+sReqURI);
          pi = new PathInfo(sReqURI, Processor.getServletPath);
    }   
 
  
  log(3,"sReqURI: "+sReqURI);
  
  if (this.logger.getMaxLevel() >= 3) {
    spyRequest(sb, sMode, sMethod, sReqURI, request);
  }

  // Default page:
  if (pi.iCount == 1) 
  {
    if (sMethod.equals("GET") && 
        sMode.toUpperCase().indexOf("DAV") < 0  // allow access to the root folder for DAV DADs
       ) 
    {
      sb.append(sReqURI);
      if (sb.charAt(sb.length()-1) != '/') sb.append('/');
      sb.append(props.getProperty("default_page", "!"+Processor.SCRIPT_PREFIX+"?id_=0"));
      String sLocation = sb.toString();
      log(1,"Redirected to default page: " + sLocation);
      response.sendRedirect(sLocation);
      return;
    }
  }
  // Load method:
  String sMethodClass = "com.nnetworks.jopa."+sMode.toUpperCase()+"."+sMethod;
  log(3, "Using Method Class :" + sMethodClass);
  JOPAMethod method = null;
  try {
    method = (JOPAMethod)Class.forName(sMethodClass).newInstance();
  }
  catch (ClassNotFoundException e) {
    log(0, "Class ", sMethodClass, " not found");
  }
  catch (InstantiationException e) {
    log(0, "Instantiation failed");
  }
  catch (IllegalAccessException e) {
    log(0, "Illegal access");
  }
  if (method == null) {
    response.setStatus(405);
    return;
  }

  // Let the method to execute the request:
  method.init(this, pi, props, request, response, this.getServletContext());
  method.service();
}

//----------------------------------------------------------------------
// Responses:
//----------------------------------------------------------------------

/** ***************************************************************************
 *  Responds with fatal-class error message.
 *
 * @param	response	HTTP response encapsulator to use for sending error message
 * @param	iCode		HTTP error code
 * @param	sMsg		Message to output
 *
 */
private void respFatal
( HttpServletResponse response
, int       iCode
, String    sMsg
)
{
  log(0, sMsg);
  try
  {
    //response.sendError(iCode, sMsg);
    response.sendError(iCode, ViewFile.getErrorFile(getInitParameter(iCode+".html"),sMsg));
  }
  catch(IOException ioe) {}
}

//----------------------------------------------------------------------
// Configuration support:
//----------------------------------------------------------------------

/** ***************************************************************************
 *  Load custom configuration from the configuration file.
 *
 * @return	<code>1</code> if loaded successfully<br/>
 *              <code>0</code> if the config file wasn't found<br/>
 *              <code>-1</code> if there was an I/O error while reading the config
 *
 */
public int loadCfg ()
{
  if (!this.fileCfg.exists()) {
    log(0, "Config file does not exists: ",this.fileCfg.getAbsolutePath() +":"+ this.fileCfg.getPath());
    return 0;
  }
  ResourcePool cfg = new ResourcePool();
  cfg.setBaseProperties("Default");
  try {
    cfg.consultPropertiesPool(this.fileCfg);
  }
  catch (IOException e) {
    log(0, "Failed to load config file", e.getMessage());
    return -1;
  }
  this.cfg = cfg;
  return 1;
}

/** ***************************************************************************
 * Saves current configuration to the configuration file
 *
 * @return	<code>1</code> if save succeeded<br/>
 *		<code>-1</code> if there was an I/O error while saving the config
 */
public int saveCfg ()
{
  try {
    this.cfg.storePool(this.fileCfg);
    return 1;
  }
  catch (IOException e) {
    log(0, "IO: ", e.getMessage());
  }
  return -1;
}


/** ***************************************************************************
 *  Loads global configuration parameters from the configuration file
 *
 */
public void configParameters ()
{
  Properties props = this.cfg.getProperties("Config");
  if (props == null) {
    log(0, "No [Config] section");
    return;
  }

  this.logger.setPrefix
    ( props.getProperty("log_prefix", "")
    );

  this.logger.setErrPrefix
    ( props.getProperty("err_prefix", this.logger.getErrPrefix())
    );

  this.logger.setLogPath
    ( props.getProperty("logfile")
    , true
    );

  this.logger.setLogLevel
    ( getInt(props.getProperty("loglevel"), 0)
    );

  this.logger.setSysLevel
    ( getInt(props.getProperty("debuglevel"), 0)
    );
}

//----------------------------------------------------------------------


/** ***************************************************************************
 * Attempts to parse the input string as integer, returns parsed value or
 * default if parsing fails.
 *
 * @param	s	string to parse
 * @param	iDef	default value
 *
 * @return	parsed integer value or iDef if parsing fails.
 */
private int getInt (String s, int iDef)
{
  if (s != null) {
    try { return Integer.parseInt(s, 10); }
    catch (NumberFormatException e) {}
  }
  return iDef;
}

//----------------------------------------------------------------------
// JOPAShell implementation:
//----------------------------------------------------------------------

public final String getVersion ()
{
  return this.version;
}

//----------------------------------------------------------------------

public final File getFileCfg ()
{
  return this.fileCfg;
}

public ResourcePool getCfg ()
{
  return this.cfg;
}

//----------------------------------------------------------------------

public void log (int lev, String msg)
{
  this.logger.log(lev, msg);
}

public void log (int lev, String msg1, String msg2)
{
  this.logger.log(lev, msg1, msg2);
}

public void log (int lev, String msg1, String msg2, String msg3)
{
  this.logger.log(lev, msg1, msg2, msg3);
}

public void log (int lev, String msg1, String msg2, String msg3, String msg4)
{
  this.logger.log(lev, msg1, msg2, msg3, msg4);
}

public PrintStream getLogStream (int lev)
{
  return this.logger.getLogStream(lev);
}

public PrintStream getDebugStream (int lev)
{
  return this.logger.getSysStream(lev);
}

//----------------------------------------------------------------------

public final ConnectionPool getConnectionPool ()
{
  return this.connpool;
}

//----------------------------------------------------------------------

public String getCode
( String sName
) throws JOPAException
{
  String s = this.codes.getText(sName);
  if (s == null)
    throw (new JOPAException("JOPA.getCode", "No such code: " + sName));
  return s;
}

public String getCode
( String sName
, String sPar1, String sVal1
) throws JOPAException
{
  String s = this.codes.getTextWithSubst(sName, sPar1, sVal1);
  if (s == null)
    throw (new JOPAException("JOPA.getCode", "No such code: " + sName));
  return s;
}

public String getCode
( String sName
, String sPar1, String sVal1
, String sPar2, String sVal2
) throws JOPAException
{
  String s = this.codes.getTextWithSubst(sName, sPar1, sVal1, sPar2, sVal2);
  if (s == null)
    throw (new JOPAException("JOPA.getCode", "No such code: " + sName));
  return s;
}

public String getCode
( String sName
, String sPar1, String sVal1
, String sPar2, String sVal2
, String sPar3, String sVal3
) throws JOPAException
{
  String s = this.codes.getTextWithSubst(sName, sPar1, sVal1, sPar2, sVal2, sPar3, sVal3);
  if (s == null)
    throw (new JOPAException("JOPA.getCode", "No such code: " + sName));
  return s;
}

public StringBuffer getCodeBuffer
( String sName
) throws JOPAException
{
  StringBuffer sb = this.codes.getTextBuffer(sName);
  if (sb == null)
    throw (new JOPAException("JOPA.getCodeBuffer", "No such code: " + sName));
  return sb;
}

public void substCode
( StringBuffer sb
, String sPar, String sVal
)
{
  this.codes.subst(sb, sPar, sVal);
}

//----------------------------------------------------------------------
// Shelf support:
//----------------------------------------------------------------------

public void putOnShelf (String sKey, Object obj)
{
  this.shelf.put(sKey, obj);
}

//----------------------------------------------------------------------

public Object getFromShelf (String sKey)
{
  return this.shelf.get(sKey);
}

//----------------------------------------------------------------------

protected void closeShelf ()
{
  log(3, "closeShelf");
  this.shelf.clear();
}

//----------------------------------------------------------------------
// Debugging:
//----------------------------------------------------------------------

protected void spyRequest
( StringBuffer sb
, String  sMode
, String  sMethod
, String  sReqURI
, HttpServletRequest request
)
{
  sb.setLength(0);
  sb.append("----- JOPA.spyRequest ------------------------------------------- ");
  sb.append(sMode);
  log(3, sb.toString());

  sb.setLength(0);
  sb.append("=> ");
  sb.append(sMethod);
  sb.append(' ');
  sb.append(sReqURI);
  sb.append(" HTTP/1.1");
  log(3, sb.toString());

  String sName;
  Enumeration en;
  en = request.getHeaderNames();
  while (en.hasMoreElements()) 
  {
    sName = (String)en.nextElement();
    sb.setLength(0);
    sb.append("=> ");
    sb.append(sName);
    sb.append(": ");
    sb.append(request.getHeader(sName));
    log(3, sb.toString());
  }

  sb.setLength(0);
}

    /**
      * If Java 1.4 is unavailable, the following technique may be used.
      *
      * @param aInput is the original String which may contain substring aOldPattern
      * @param aOldPattern is the non-empty substring which is to be replaced
      * @param aNewPattern is the replacement for aOldPattern
      */
    public static String replace(
        final String aInput,
        final String aOldPattern,
        final String aNewPattern
      ){
         if ( aOldPattern.equals("") ) {
            throw new IllegalArgumentException("Old pattern must have content.");
         }

         final StringBuffer result = new StringBuffer();
         //startIdx and idxOld delimit various chunks of aInput; these
         //chunks always end where aOldPattern begins
         int startIdx = 0;
         int idxOld = 0;
         while ((idxOld = aInput.indexOf(aOldPattern, startIdx)) >= 0) {
           //grab a part of aInput which does not include aOldPattern
           result.append( aInput.substring(startIdx, idxOld) );
           //add aNewPattern to take place of aOldPattern
           result.append( aNewPattern );

           //reset the startIdx to just after the current match, to see
           //if there are any further matches
           startIdx = idxOld + aOldPattern.length();
         }
         //the final chunk will go to the end of aInput
         result.append( aInput.substring(startIdx) );
         return result.toString();
      }

//----------------------------------------------------------------------

private void respErrorPage
( HttpServletResponse response
, String              sErr
)
{
  log(0, sErr);
  response.setContentType("text/html");
  try {
    PrintWriter out = response.getWriter();
    out.println("<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">");
    out.println("<html><head><title>Error Page</title></head>");
    out.println("<body bgcolor=\"#ffffff\" text=\"#c00000\">");
    out.print("<b><pre>");
    out.print(sErr);
    out.println("</pre></b>");
    out.println("</body></html>");
    out.close();
  }
  catch (IOException e) {}
}

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.84 12.2015 Andrew A.Toropov $";
} // class JOPA

//----------------------------------------------------------------------

