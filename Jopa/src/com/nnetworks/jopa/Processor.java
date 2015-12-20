//----------------------------------------------------------------------
// Processor.java
//
// $Id: Processor.java,v 1.82 2007/01/01 09:48:40 Drew Exp $
//
//----------------------------------------------------------------------

package com.nnetworks.jopa;

import com.nnetworks.shell.Logger;
import javax.servlet.*;
import javax.servlet.http.*;

import oracle.sql.*;
import oracle.jdbc.driver.*;

import java.net.URLEncoder;
import java.net.URLDecoder;
import java.sql.*;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.zip.*;

import oracle.jdbc.pool.OracleDataSource;

/**
 *  Serves as the base for various access methods implementation.
 *
 *  This class encapsulates common functionality for JOPA access methods,
 *  like WebDAV, OWA, etc. All actual methods extend this class and
 *  add actual request service implementation.
 *
 *  @version $Revision: 1.82 $
 *  @author  Sergey Ageshin, Vladimir Zakharychev, Andrew Toropov
 */
public class Processor
{

/** Streams buffer size, used for copying streams */
protected final static int BUFFER_SIZE = 8192;

protected JOPAShell           shell;
protected PathInfo            pi;
protected Properties          props;
protected HttpServletRequest  request;
protected HttpServletResponse response;
protected ServletContext      context;

// These things are created internally:
protected String        sUser, sPasw, sLink;
protected Connection    m_conn;
protected CharacterSet  m_cset;

protected String        requestCharset;
protected String        responseCharset;
protected String        serverCharset;
protected String        effCharset;
protected String        AcceptEncoding;
protected boolean	charsetForced = false;
protected boolean       downloadForced = false;
protected String        downloadFilename = "";
protected String        downloadFileType = "text/html";
protected static String FILE_TYPES   = "";
protected static String SCRIPT_TYPES = "";
protected static String SCRIPT_PREFIX = "";
protected static String MIME_TYPES = "";
protected static String getServletPath = "";

protected String        ARRAY_TYPE;
protected Vector        m_vecEnvName;
protected Vector        m_vecEnvData;

public final static int ENC_NOCOMPRESS = 0;
public final static int	ENC_GZIP       = 1;
public final static int	ENC_DEFLATE    = 2;

public final static int FLG_HAS_CONTENT_TYPE = 0x0001;
public final static int FLG_HAS_STATUS       = 0x0002;
public final static int FLG_REDIRECT         = 0x0004;
public final static int FLG_CAN_COMPRESS     = 0x0008;
public final static int FLG_IS_COMMITTED     = 0x0010;

protected int           m_flags              = FLG_CAN_COMPRESS;

public final static int HDR_OTHER          = 0;
public final static int HDR_CONTENT_LENGTH = 1;
public final static int HDR_LOCATION       = 2;
public final static int HDR_CONTENT_TYPE   = 3;
public final static int HDR_SET_COOKIE     = 4;
public final static int HDR_STATUS         = 5;

public final static String HTTP_OK = "OK";
public final static String HTTP_CREATED = "Created";
public final static String HTTP_NO_CONTENT = "No content";
public final static String HTTP_BAD_REQUEST = "Bad request";
public final static String HTTP_BAD_GATEWAY = "Bad gateway";
public final static String HTTP_BAD_METHOD = "Method not allowed";
public final static String HTTP_NOT_FOUND = "Not found";
public final static String HTTP_LOCKED = "Locked";
public final static String HTTP_INSUFFICIENT_SPACE = "Insufficient space";
public final static String HTTP_CONFLICT = "Conflict";
public final static String HTTP_PRECONDITION_FAILED = "Precondition failed";
public final static String HTTP_PARTIAL_CONTENT = "Partial content";
public final static String HTTP_MULTI_STATUS = "Multi-Status";
public final static String HTTP_NOT_MODIFIED = "Not modified";
public final static String HTTP_FORBIDDEN = "Forbidden";
public final static String HTTP_AUTHORIZATION_REQUIRED = "Authorization required";
public final static String HTTP_METHOD_FAILURE = "Method failure";



/**
 * Get default Oracle Array descriptor.
 *
 * @return	array descriptor of default type defined by {@link #ARRAY_TYPE ARRAY_TYPE}
 * @throws	java.sql.SQLException	when array descriptor cannot be created
 */
protected ArrayDescriptor getArrayDescriptor()
throws SQLException
{
  return getArrayDescriptor(ARRAY_TYPE);
}

/**
 * Creates an ArrayDescriptor for array type. Supported array types are SQL collections,
 * PL/SQL tables are not supported by the Thin JDBC driver, hence they are not supported
 * here either, SQLException will be thrown for such array types.
 *
 * @param	sArrayTypeName
 *		Oracle array type name (fully qualified or short)
 * @return	array descriptor for the specified array type
 * @throws	java.sql.SQLException	when array descriptor cannot be created
 */
protected ArrayDescriptor getArrayDescriptor(String sArrayTypeName)
throws SQLException
{
  try
  {
    return ArrayDescriptor.createDescriptor(sArrayTypeName.toUpperCase(), m_conn);
  }
  catch(SQLException e)
  {
    return ArrayDescriptor.createDescriptor(sArrayTypeName, m_conn);
  }
}


//----------------------------------------------------------------------

public void init
( JOPAShell           shell
, PathInfo            pi
, Properties          props
, HttpServletRequest  request
, HttpServletResponse response
, ServletContext      context
)
{
  this.shell    = shell;
  this.pi       = pi;
  this.props    = props;
  this.request  = request;
  this.response = response;
  this.context  = context;

  this.m_conn = null;
  this.m_cset = null;

  {
    String ct;
    int    start;
    int    end;
    if ((ct = this.request.getContentType()) != null)
    {
      if ((start = ct.toLowerCase().indexOf("charset=")) >= 0)
      {
        this.requestCharset = ct.substring(start+8);
        if ((end = this.requestCharset.indexOf(";")) >= 0)
          this.requestCharset = this.requestCharset.substring(0, end);
        this.requestCharset = strip2Q(this.requestCharset);
      }
    }
  }

  if (   (this.requestCharset == null || this.requestCharset.length() == 0) 
      && (this.requestCharset = this.request.getCharacterEncoding()) == null )
 // this.requestCharset = "ISO-8859-1";
  this.requestCharset = "UTF-8";
  this.responseCharset = this.response.getCharacterEncoding();
  if (this.responseCharset == null) this.responseCharset = this.requestCharset;
  this.serverCharset = this.requestCharset;
  this.effCharset = this.props.getProperty("charset", this.requestCharset);
  {
    String s = this.props.getProperty("charset");
    charsetForced = (s != null) && (s.length() > 0);
  }
  AcceptEncoding = this.request.getHeader("Accept-Encoding");
  if (AcceptEncoding == null)
    AcceptEncoding = this.request.getHeader("TE"); // TE may also specify acceptable transfer-codings

  this.m_vecEnvName = new Vector(64);
  this.m_vecEnvData = new Vector(64);

  this.ARRAY_TYPE = this.props.getProperty("array_type", "NN$VARCHAR_ARRAY");

  afterInit();
}

/**
 * Guess the most desired content encoding based on Accept-Encoding HTTP header.
 *
 * @return	name of preferred content encoding algorithm or <code>null</code> if none applicable
 */
protected int getDesiredContentEncoding()
{
 String[] ae;
 String   rv    = null;
 float    lastQ = 0;

 // if compression is disabled, act as if it wasn't requested
 // also check if it was requested and if it is desired ( we check it in respContentType() )
 if ( this.props.getProperty("compression_enabled", "0").equals("0") || 
      !isFlagSet(FLG_CAN_COMPRESS) || 
      AcceptEncoding == null)
   return ENC_NOCOMPRESS;

 ae = AcceptEncoding.split(","); // split up distinct encodings
 for (int i = 0; i < ae.length; i++)
 {
   String aeq[] = ae[i].toLowerCase().split(";"); // split the encoding into name and qvalue
   float q;
   if (aeq.length > 1) // was there qvalue?
     q = new Float(aeq[1].substring( aeq[1].indexOf("q=")+2 )).floatValue(); // get it
   else
     q = 1; // assume qvalue of 1.0
   if (q > lastQ) 
   // this encoding is more preferable than anything we saw before
   {
     rv = aeq[0].trim(); // remember it
     lastQ = q;          // and its qvalue
   }
 }
 return (rv == null) ? ENC_NOCOMPRESS :
   ( rv.equals("gzip") ? ENC_GZIP :
     ( rv.equals("deflate") ? ENC_DEFLATE : 0 )
   );
}

/**
 * additional initialization. Empty in this class, should be overridden
 * by descendants if they need to do additional initialization. Called
 * automatically by {@link #init init()} method.
 */
protected void afterInit ()
{
  
}

//----------------------------------------------------------------------

protected void initSession ()
{
  resetSession(this.m_conn);
  obtainCharset();
  this.serverCharset = getIANACharsetName();
  if (this.serverCharset == null) this.serverCharset = this.requestCharset;
  this.effCharset = this.props.getProperty("charset", this.serverCharset);
  {
    String s = this.props.getProperty("charset");
    charsetForced = (s != null) && (s.length() > 0);
  }
  shell.log(4,"Server charset: "+this.serverCharset);
  shell.log(4,"Request charset: "+this.requestCharset);
  shell.log(4,"Effective charset: "+this.effCharset+(charsetForced ? " (forced)" : ""));
}

//----------------------------------------------------------------------
// Connection support:
//----------------------------------------------------------------------

protected void establishConnection ()
throws JOPAException
{
  this.sUser = this.props.getProperty("username");
  if (this.sUser == null)
    throw (new JOPAException("establishConnection", "User name not specified"));

  this.sPasw = this.props.getProperty("password");
  if (this.sPasw == null)
    throw (new JOPAException("establishConnection", "Password not specified"));
  //---[15.04.2003 S.Ageshin added this:
  this.sPasw = Base64.decryptPasw(this.sPasw);
  //shell.log(2, "Pasw: ", this.sPasw);
  //---]

  String sDriver, sHost, sPort, sSID;
  sDriver = this.props.getProperty("driver", "thin");
  sHost = this.props.getProperty("host");
  if (sHost == null)
    throw (new JOPAException("establishConnection", "Host not specified"));
  sPort = this.props.getProperty("port", "1521");
  sSID  = this.props.getProperty("sid");
  if (sSID == null)
    throw (new JOPAException("establishConnection", "DB SID not specified"));
  this.sLink = ConnectionPool.makeDatabaseString(sDriver, sHost, sPort, sSID);

  try {
    //  ConnectionPool pool = shell.getConnectionPool(); // v.1.6.0
    //  m_conn = pool.getConnection(this.sUser, this.sPasw, this.sLink); // v.1.6.0
    //  m_conn.setAutoCommit(false); // ### USE EVERYWHERE! v.1.6.0
    
    // Start without pool v.1.7.1
     //   Driver m_Driver = (Driver)Class.forName("oracle.jdbc.driver.OracleDriver").newInstance();
    //    DriverManager.setLoginTimeout(1000);//  v.1.7.1
    //    m_conn          =  DriverManager.getConnection(this.sLink,this.sUser, this.sPasw);//  v.1.7.1     
    //    m_conn.setAutoCommit(true); // ### USE EVERYWHERE! v.1.7.1
    // end  without pool v.1.7.1

     // Start v.1.8.1
      try {          
         OracleDataSource ods = new OracleDataSource();
         ods.setUser(this.sUser);
         ods.setPassword(this.sPasw);
         ods.setURL(this.sLink);
         if (m_conn == null){shell.log(3,"not connected");}
         else {
         System.out.println("already connected");
         try     {
             while(!m_conn.isClosed()) 
                 try{m_conn.close();shell.log(3,"try to disconnect");}
                 catch (SQLException e){shell.log(3,"disconnected");}
                 catch (NullPointerException e){shell.log(3,"disconnected:NullPointerException");} 
                 }
         catch (NullPointerException e){shell.log(3,"disconnected:!m_conn.isClosed()");}
         }
// get Connection
         m_conn = ods.getConnection();
         m_conn.setAutoCommit(true);
     }
      catch (java.lang.NullPointerException ex){
            shell.log(3,"NullPointerException");
        }
      catch (SQLException e){
            e.printStackTrace(shell.getLogStream(0));
            throw (new JOPAException("establishConnection/SQL", e.getMessage()));
            //out.println("go.java:"+ i +":ORA-"+e.getErrorCode()+":"+e.getLocalizedMessage());
        }
    
             // end  v.1.8.1
    
    if (m_conn instanceof OracleConnection)
      if ( !((OracleConnection)m_conn).getImplicitCachingEnabled() && 
           !((OracleConnection)m_conn).isLogicalConnection() )
         ((OracleConnection)m_conn).setImplicitCachingEnabled(true);
    String sKey  = (String) ((OracleConnection)m_conn).getClientData("Key");
    String sStat = (String) ((OracleConnection)m_conn).getClientData("Stat");
    shell.log(2, sStat, " conn: ", sKey);
/*   //  v.1.7.1
    if (sStat.equals("New")) {
      try {
        java.sql.Driver drv = DriverManager.getDriver(this.sLink);
        if (drv != null) {
          shell.log(3, "JDBC driver version: ",
            Integer.toString(drv.getMajorVersion()) + '.' +
            Integer.toString(drv.getMinorVersion()));
        }
      }
      catch (SQLException ee) {}
    }
    */   //  v.1.7.1
    initSession();
  }
  //  v.1.7.1
  //catch (ClassNotFoundException e){ new JOPAException("oracle.jdbc.driver.OracleDriver",e.getMessage()); return;}
  //  v.1.7.1
 // catch (IllegalAccessException e){ new JOPAException("oracle.jdbc.driver.OracleDriver",e.getMessage()); return;}
  //  v.1.7.1
 // catch (InstantiationException e){ new JOPAException("oracle.jdbc.driver.OracleDriver",e.getMessage()); return;}
  catch (SQLException e) {
    e.printStackTrace(shell.getLogStream(0));
    throw (new JOPAException("establishConnection/SQL", e.getMessage()));
  }
}

//----------------------------------------------------------------------

protected Connection establishConnection2 ()
{
  try {
    ConnectionPool pool = shell.getConnectionPool();
    Connection conn = pool.getConnection(this.sUser, this.sPasw, this.sLink);
    conn.setAutoCommit(false); // ### USE EVERYWHERE!
    String sKey  = (String) ((OracleConnection)conn).getClientData("Key");
    String sStat = (String) ((OracleConnection)conn).getClientData("Stat");
    shell.log(2, sStat, " conn2: ", sKey);
    resetSession(conn);
    return conn;
  }
  catch (SQLException e) {
    e.printStackTrace(shell.getLogStream(0));
    shell.log(0, "establishConnection2/SQL", e.getMessage());
  }
  return null;
}

//----------------------------------------------------------------------

protected void releaseConnection ()
{
 shell.log(3, "try releaseConnection"); 
 try {if (m_conn!=null||!m_conn.isClosed()){m_conn.close();shell.log(3, "Connection closed");}
 } 
  catch (SQLException e) {shell.log(3, "releaseConnection",e.getMessage());}
  catch (NullPointerException e) {shell.log(3, "releaseConnection",e.getMessage());}
  shell.getConnectionPool().releaseConnection(m_conn);
}

//----------------------------------------------------------------------

protected void commit ()
{
  if (m_conn != null) {
    try { m_conn.commit(); }
    catch (SQLException e) {}
  }
}

//----------------------------------------------------------------------

protected void rollback ()
{
  if (m_conn != null) {
    try { m_conn.rollback(); }
    catch (SQLException e) {}
  }
}

//----------------------------------------------------------------------

protected void resetSession (Connection conn)
{
  OracleCallableStatement cs;
  try {
    cs = (OracleCallableStatement)conn.prepareCall(
      this.props.getProperty("reset_mode", "full").equalsIgnoreCase("fast") ?
      "begin DBMS_SESSION.MODIFY_PACKAGE_STATE(2); end;" :
      "begin DBMS_SESSION.RESET_PACKAGE; end;");
    cs.executeUpdate();
    cs.close();
  }
  catch (SQLException e) {
    try {
      cs = (OracleCallableStatement)conn.prepareCall(
        "begin DBMS_SESSION.RESET_PACKAGE; end;");
      cs.executeUpdate();
      cs.close();
    }
    catch(SQLException e1) {
      shell.log(0, "resetSession/SQL: ", e1.getMessage());
    }
  }
}

//----------------------------------------------------------------------
// Responses:
//----------------------------------------------------------------------

protected void respFatal (int iCode, String sMsg)
{
  try
  {
     respContentType("text/plain; charset=\"" + this.effCharset + '\"');
    this.response.sendError(iCode);
    setFlag(FLG_IS_COMMITTED);
  }
  catch(IOException ioe) {}
  shell.log(0, sMsg);
}


//----------------------------------------------------------------------

protected void respFatal (int iCode, Exception e)
{
    shell.log(0, "Logger.LogLevel: ",Logger.LogLevel+" ");
if (Logger.LogLevel>0) {
  StringWriter sw = new StringWriter();
  PrintWriter pw = new PrintWriter(sw);
   try
  {
    respContentType("text/plain; charset=\"" + this.effCharset + '\"');
    e.printStackTrace(pw);
    pw.close();
    this.response.sendError(iCode, "<pre>"+sw.toString()+"</pre>");
    setFlag(FLG_IS_COMMITTED);
  }
  catch(IOException eio) {}
  e.printStackTrace(shell.getLogStream(0));
}
}


//----------------------------------------------------------------------

protected void respStatus (int iCode, String sMsg)
{
  this.response.setStatus(iCode);
  shell.log(3, "<= " + this.request.getProtocol() + ' ',
               Integer.toString(iCode) + ' ', sMsg);
}

//----------------------------------------------------------------------

protected void respContentType (String sMimeType)
{
  String sValue = sMimeType.toLowerCase();
  if ( charsetForced && sValue.startsWith("text") )
  {
    // effective charset overridden through the DAD config, so we cut it off
    // the content-type string sent by the server and set the overridden charset
    shell.log(3,"Overriding charset to "+this.effCharset);
    String[] ctsplit = sValue.split(";");
    sValue = ctsplit[0] + "; charset=" + this.effCharset.toLowerCase();
  }
  this.response.setContentType(sValue);
  if (sValue.startsWith("image") ||
      sValue.indexOf("compress") > 0)
    unsetFlag(FLG_CAN_COMPRESS);

  shell.log(3, "<= Content-Type: ", sValue );
}

//----------------------------------------------------------------------

protected void respContentLength (int iLength)
{
  this.response.setContentLength(iLength);
  shell.log(3, "<= Content-Length: ", Integer.toString(iLength));
}

//----------------------------------------------------------------------

protected void respHeader (String sName, String sData)
{
  if (sName != null && sData != null) {
    this.response.setHeader(sName, sData);
    shell.log(3, "<= ", sName, ": ", sData);
  }
}

//----------------------------------------------------------------------

protected void respCookie (Cookie cookie)
{
  if (cookie != null) {
    this.response.addCookie(cookie);
    shell.log(3, "<= addCookie: ", cookie.getName(), "=", cookie.getValue()+":"+cookie.getMaxAge() );
  }
}

//----------------------------------------------------------------------

protected void respOWAHeader (String sName, String sData)
{
  if (!sName.equalsIgnoreCase("X-ORACLE-IGNORE"))shell.log(3, "<= respOWAHeader: ", sName, "=", sData);
  if (sName.equalsIgnoreCase("Status")) {
    int i = sData.indexOf(' ', 0);
    if (i > 0) {
      try {
        int j = Integer.parseInt(sData.substring(0, i));
        respStatus(j, sData.substring(i+1));
      }
      catch (NumberFormatException e) {}
    }
  }
  else if (sName.equalsIgnoreCase("X-ORACLE-IGNORE")) {}
  // AAT download russian's files
  else if (sName.equalsIgnoreCase("Content-Disposition")) {
    try
   {
      downloadForced = true;
      try {
      downloadFilename = sData.substring(sData.indexOf("filename=")+10);
      downloadFilename = downloadFilename.substring(0,downloadFilename.indexOf("\""));
      }
      catch (java.lang.StringIndexOutOfBoundsException si){
      downloadFilename = sData; 
      }
      // respHeader(sName, new String(sData.getBytes("Cp1251"),"ISO8859_1"));
        downloadFilename = new String(downloadFilename.getBytes("Cp1251"),"ISO8859_1");
        respHeader(sName, "attachment; filename=\""+ downloadFilename + "\"");      
   }
     catch (UnsupportedEncodingException e){respHeader(sName, sData);}
  }
  else {
    respHeader(sName, sData);
  }
}

/**
 *  Attempts to classify the HTTP response header.
 *
 *  @param	sHeader	HTTP header name.
 *
 *  @return	one of the HDR_ constants identifying the header.
 */
protected int getHeaderType(String sHeader)
{
  return sHeader.equalsIgnoreCase("Content-Type")   ? HDR_CONTENT_TYPE   :
         sHeader.equalsIgnoreCase("Set-Cookie")     ? HDR_SET_COOKIE     :
         sHeader.equalsIgnoreCase("Content-Length") ? HDR_CONTENT_LENGTH :
         sHeader.equalsIgnoreCase("Status")         ? HDR_STATUS         :
         sHeader.equalsIgnoreCase("Location")       ? HDR_LOCATION       :
         HDR_OTHER;
}


// flag-related methods
protected boolean isFlagSet(int flag)
{
  return (m_flags & flag) != 0;
}

protected void setFlag(int flag)
{
  this.m_flags |= flag;
}

protected void unsetFlag(int flag)
{
  this.m_flags &= ~flag;
}

protected void respHeaders (String sHeaders)
{
   shell.log(3, "Processor.respHeaders: \n---------------------\n", sHeaders +"\n---------------------\n"); 
  if (sHeaders == null)
    return;
  String s, sName, sValue;
  Cookie cookie;
  int j;
  int k = 0;
  int i = sHeaders.indexOf('\n', k);
  while (i > k) {
    s = sHeaders.substring(k, i);
    k = i + 1;
    j = s.indexOf(':', 0);
    if (j > 0)
    {
      sName  = s.substring(0, j).trim();
      sValue = s.substring(j+1).trim();
      // if (!sName.equalsIgnoreCase("X-ORACLE-IGNORE"))shell.log(3, "   respHeaders: -> ", sName, "=", sValue);   
      // shell.log(3, "   respHeaders: ->:", sName, "=", sValue);
      switch(getHeaderType(sName))
      {
        case HDR_CONTENT_TYPE:
          if (!isFlagSet(FLG_HAS_CONTENT_TYPE)){ ///AAT set CONTENT twice 
                          setFlag(FLG_HAS_CONTENT_TYPE);
                          respContentType(sValue);}
                          break;
        case HDR_SET_COOKIE:
                          respCookie(parseCookie(sValue));
                          break;
        case HDR_CONTENT_LENGTH:
                            if ( getDesiredContentEncoding() == ENC_NOCOMPRESS ) 
                            // only send content-length if we're not going to compress
                            respContentLength(Integer.parseInt(sValue));
                          break;
        case HDR_LOCATION:
                          setFlag(FLG_REDIRECT);
                          try
                          {
                            sValue = (new String(sValue.getBytes("Cp1251"), "ISO8859_1"));
                            shell.log(3, "HDR_LOCATION:"+sValue+"\n"); 
                            this.response.sendRedirect(sValue);
                            setFlag(FLG_IS_COMMITTED);
                            return;
                          }
                          catch(IOException e) {}
                          break;
        case HDR_STATUS:
                          setFlag(FLG_HAS_STATUS);
                          // fall through here, it's intended
        default:
                          respOWAHeader(sName, sValue);
      }
    }
    i = sHeaders.indexOf('\n', k);
  }
  if ( !isFlagSet(FLG_HAS_CONTENT_TYPE) )
  {
    // server didn't set content-type, set default
    respContentType("text/html;charset="+this.effCharset);
  }

  if ( !isFlagSet(FLG_HAS_STATUS) )
  {
    // server didn't set status and it wasn't redirect
    respStatus(200, HTTP_OK);
  }

  switch ( getDesiredContentEncoding() )
  {
    case ENC_GZIP:
        respHeader("Content-Encoding","gzip");
        break;
    case ENC_DEFLATE:
        respHeader("Content-Encoding","deflate");
  }
}

//----------------------------------------------------------------------

protected Cookie parseCookie (String s)
{
  int iPos, iLen, i;
  String sName, sData;
  Cookie cookie = null;
  iLen = s.length();
  iPos = 0;
  if (iPos < iLen) {
    i = s.indexOf('=', iPos);
    if (i > iPos) try {
      sName = s.substring(iPos, i).trim();
      iPos = i + 1;
      i = s.indexOf(';', iPos);
      if (i < iPos) i = iLen;
      sData = s.substring(iPos, i).trim();
      iPos = i + 1;
      cookie = new Cookie(sName, strip2Q(sData));
      while (iPos < iLen) {
        i = s.indexOf('=', iPos);
        if (i < 0) break;
        sName = s.substring(iPos, i).trim();
        iPos = i + 1;
        i = s.indexOf(';', iPos);
        if (i < iPos) i = iLen;
        sData = s.substring(iPos, i).trim();
        iPos = i + 1;
        if (sName.equalsIgnoreCase("Version")) {
          try { cookie.setVersion(Integer.parseInt(strip2Q(sData), 10)); }
          catch (NumberFormatException e1) {}
        }
        else if (sName.equalsIgnoreCase("Max-Age")) { //AAT equalsIgnoreCase
          try { cookie.setMaxAge(Integer.parseInt(strip2Q(sData), 10)); }
          catch (NumberFormatException e2) {}
        }
        else if (sName.equals("Path")) { //AAT equalsIgnoreCase
          cookie.setDomain(strip2Q(sData));
        }
        else if (sName.equals("Domain")) { //AAT equalsIgnoreCase
          cookie.setDomain(strip2Q(sData));
        }
      }
    }
    catch (IllegalArgumentException iae) {
      shell.log(0, "Bad cookie: ", s);
    }
  }
  return cookie;
}

//----------------------------------------------------------------------

protected String strip2Q (String s)
{
  if (s.startsWith("\"") && s.endsWith("\"") )
    return s.substring(1, s.length() - 1);
  else
    return s;
}

//----------------------------------------------------------------------

protected void respContent (BLOB blob)
{
  if (blob == null || isFlagSet(FLG_IS_COMMITTED)) return;
  byte[] buf = new byte[BUFFER_SIZE];
  int    n;
  try {

    InputStream is = blob.getBinaryStream();
    OutputStream sos = this.response.getOutputStream();

    sos.flush();

    switch( getDesiredContentEncoding() )
    {       
      case ENC_GZIP:
        sos =  new GZIPOutputStream(sos);
        shell.log(3,"Applying GZIP content encoding to the output...");
        break;
      case ENC_DEFLATE:
        sos = new DeflaterOutputStream(sos);
        shell.log(3,"Applying Deflate content encoding to the output...");
    }


    while ((n = is.read(buf)) != -1)
      sos.write(buf, 0, n);
    sos.flush();
    sos.close();
    is.close();
    freeTemporary(blob);
    shell.log(3,"done respContent(BLOB)");
  }
  catch(SQLException e) {
    shell.log(0, "respContent(BLOB)/SQLExeption: ", e.getMessage() + e);
  }
  catch(IOException e) {
    shell.log(0, "respContent(BLOB)/IOExeption: ", e.getMessage() + e);
  }
}

//----------------------------------------------------------------------

protected void respContent (CLOB clob)
{
  if (clob == null || isFlagSet(FLG_IS_COMMITTED)) return;
  byte[] buf = new byte[BUFFER_SIZE];
  int    n;
  try {

    InputStream is = clob.getAsciiStream();
    OutputStream sos = this.response.getOutputStream();

    sos.flush();

    switch( getDesiredContentEncoding() )
    {       
      case ENC_GZIP: 
        sos = new GZIPOutputStream(sos);
        shell.log(3,"Applying GZIP content encoding to the output...");
        break;
      case ENC_DEFLATE:
        sos =  new DeflaterOutputStream(sos);
        shell.log(3,"Applying Deflate content encoding to the output...");
    }

    while ((n = is.read(buf)) != -1)
      sos.write(buf, 0, n);

    sos.flush();
    sos.close();
    is.close();
    shell.log(3,"done respContent(CLOB)");
  }
  catch(SQLException e) {
    shell.log(0, "respContent(CLOB)/SQLExeption: ", e.getMessage());
  }
  catch(IOException e) {
    shell.log(0, "respContent(CLOB)/IOExeption: ", e.getMessage());
  }
  finally {
    freeTemporary(clob);
  }
}

//----------------------------------------------------------------------
// Request support:
//----------------------------------------------------------------------

protected String readContent ()
{
  StringBuffer sb = new StringBuffer(BUFFER_SIZE);
  try {
    ServletInputStream sin = this.request.getInputStream();
    byte[] buf = new byte[BUFFER_SIZE];
    int n = 0;
    while ( (n = sin.read(buf)) != -1 )
      sb.append(new String(buf, 0, n, this.requestCharset));
  }
  catch (IOException e) {}
  shell.log(4,"Request query string: "+sb.toString());
  return sb.toString();
}

//----------------------------------------------------------------------

protected BLOB readContentBlob ()
{
  try {
    BLOB blob = obtainBlobTemp();
    if (blob != null) {
      InputStream  is = this.request.getInputStream();
      OutputStream os = blob.getBinaryOutputStream();
      //OutputStream os = blob.setBinaryStream(0);
      byte[] buf = new byte[BUFFER_SIZE];
      int    n;
      while ( (n = is.read(buf)) != -1 )
        os.write(buf, 0, n);
      os.flush();
      os.close();
      is.close();
      return blob;
    }
  }
  catch (IOException e) {
    shell.log(0, "readContentBlob/IO: ", e.getMessage());
  }
  catch (SQLException e0) {
    shell.log(0, "readContentBlob/SQL: ", e0.getMessage());
  }
  return null;
}

//----------------------------------------------------------------------

protected CLOB readContentClob ()
{
  try {
    CLOB clob = obtainClobTemp();
    if (clob != null) {
      InputStream  is = this.request.getInputStream();
      //OutputStream os = clob.getAsciiOutputStream();
      OutputStream os = clob.setAsciiStream(0);
      byte[] buf = new byte[BUFFER_SIZE];
      int    n;
      while ( (n = is.read(buf)) != -1 )
         os.write(buf, 0, n);
      os.flush();
      os.close();
      is.close();
      return clob;
    }
  }
  catch (IOException e) {
    shell.log(0, "readContentClob/IO: ", e.getMessage());
  }
  catch (SQLException e0) {
    shell.log(0, "readContentBlob/SQL: ", e0.getMessage());
  }
  return null;
}

//----------------------------------------------------------------------
// Environment support:
//----------------------------------------------------------------------

protected void appEnv (String sName, String sData)
{
  if (sName == null) return;
  if (sData == null) sData = "";
  m_vecEnvName.addElement(sName);
  m_vecEnvData.addElement(encode(sData));
  shell.log(4, "Env: " + sName + "=" + encode(sData).stringValue());
}

//----------------------------------------------------------------------

protected void setEnvRequestCharsets()
{
  // we pass these being db server charsets, we'll handle conversion
  // later ourselves.
  appEnv("REQUEST_CHARSET",      getDBCharsetName());
  appEnv("REQUEST_IANA_CHARSET", this.serverCharset);
}

//----------------------------------------------------------------------

protected void prepareEnv()
{
  appEnv("GATEWAY_INTERFACE"  ,"Written by www.dynamicpsp.com (English) \n and www.hitmedia.ru (Russian) by Andrew. A. Toropov - andrew.toropov@gmail.com");
  // appEnv("GATEWAY_IVERSION"   ,"2"); // do not touch that is was at OWA package
  appEnv("SERVER_SOFTWARE",   shell.getVersion());
  appEnv("SERVER_NAME",       this.request.getServerName());
  appEnv("SERVER_PROTOCOL",   this.request.getProtocol());
  appEnv("SERVER_PORT",       String.valueOf(this.request.getServerPort()));
  appEnv("REMOTE_HOST",       this.request.getRemoteHost());
  appEnv("REMOTE_ADDR",       this.request.getRemoteAddr());
  appEnv("REMOTE_USER",       this.request.getRemoteUser());
  appEnv("REQUEST_PROTOCOL",  "HTTP");
  appEnv("REQUEST_METHOD",    this.request.getMethod());
  setEnvRequestCharsets();
  appEnv("REQUEST_URI",       this.request.getRequestURI());
  appEnv("SCRIPT_NAME",       this.getServletPath);
  appEnv("SCRIPT_FILENAME",   this.context.getRealPath(this.request.getServletPath())); //m_Request.getPathTranslated();
  appEnv("QUERY_STRING",      this.request.getQueryString());
  appEnv("CONTENT_TYPE",      this.request.getContentType());
  appEnv("CONTENT_LENGTH",    String.valueOf(this.request.getContentLength()));
  appEnv("PATH_TRANSLATED",   this.request.getPathTranslated());
  appEnv("AUTH_TYPE",         this.request.getAuthType());
  appEnv("DOCUMENT_ROOT",     this.context.getRealPath("/"));
  appEnv("DOCUMENT_TABLE",    this.props.getProperty("document_table", "NN$T_DOWNLOAD"));
  appEnv("DOC_ACCESS_PATH",   this.props.getProperty("document_path", "data"));
  appEnv("DAD_NAME",          this.pi.getItem(0));

  for (Enumeration en = this.request.getHeaderNames(); en.hasMoreElements();)
  {
    String sHdrName = (String)en.nextElement();
    String sEnvData = this.request.getHeader(sHdrName);
    String sEnvName = sHdrName.replace('-', '_').toUpperCase();
    appEnv("HTTP_" + sEnvName, sEnvData);
    if (sEnvName.equals("AUTHORIZATION")) appEnv(sEnvName, sEnvData);
  }

}

//----------------------------------------------------------------------

ArrayDescriptor         m_envArray = null;

protected void sendEnv()
throws JOPAException
{
  if (m_vecEnvName.size() == 0) return;
  try 
  {
    String stmt = shell.getCode("sendEnv");
    OracleCallableStatement sendEnvStmt =
        (OracleCallableStatement)m_conn.prepareCall(stmt);
    if (m_envArray == null)
      m_envArray = getArrayDescriptor();

    sendEnvStmt.setArray(1, new ARRAY(m_envArray, m_conn, m_vecEnvName.toArray()));
    sendEnvStmt.setArray(2, new ARRAY(m_envArray, m_conn, m_vecEnvData.toArray()));

    sendEnvStmt.execute();
    sendEnvStmt.close();
  }
  catch(SQLException e) {
    shell.log(0, "sendEnv/SQL: ORA-" + Integer.toString(e.getErrorCode())
                 + ": " + e.getMessage());
  }
}

//----------------------------------------------------------------------

protected void setCGIEnv()
throws JOPAException
{
  prepareEnv();
  sendEnv();
}

//----------------------------------------------------------------------
// BLOB support:
//----------------------------------------------------------------------

protected BLOB obtainBlobTemp ()
throws SQLException
{
  OracleCallableStatement cs =
    (OracleCallableStatement)m_conn.prepareCall(
      "begin dbms_lob.createTemporary(?, false, dbms_lob.call); end;");
  cs.registerOutParameter(1, OracleTypes.BLOB);
  cs.executeUpdate();
  BLOB blob = (BLOB)cs.getObject(1);
  cs.close();
  return blob;
}

//----------------------------------------------------------------------

protected void releaseBlobTemp(BLOB blob)
{
  if (blob != null) {
    try {
      if (blob.isTemporary()) {
        OracleCallableStatement cs =
          (OracleCallableStatement)m_conn.prepareCall(
            "begin dbms_lob.freeTemporary(?); end;");
        cs.setBLOB(1, blob);
        cs.executeUpdate();
        cs.close();
      }
    }
    catch (SQLException e) {}
    blob = null;
  }
}

//----------------------------------------------------------------------

protected void freeTemporary (BLOB blob)
{
  try
  {
    if (blob != null && blob.isTemporary() )
      blob.freeTemporary();
  }
  catch (SQLException e) {}
}

//----------------------------------------------------------------------

protected CLOB obtainClobTemp ()
throws SQLException
{
  OracleCallableStatement cs =
    (OracleCallableStatement)m_conn.prepareCall(
      "begin dbms_lob.createTemporary(?, false, dbms_lob.call); end;");
  cs.registerOutParameter(1, OracleTypes.CLOB);
  cs.executeUpdate();
  CLOB clob = (CLOB)cs.getObject(1);
  cs.close();
  return clob;
}

//----------------------------------------------------------------------

protected void releaseClobTemp(CLOB clob)
{
  if (clob != null) {
    try {
      if (clob.isTemporary()) {
        OracleCallableStatement cs =
          (OracleCallableStatement)m_conn.prepareCall(
            "begin dbms_lob.freeTemporary(?); end;");
        cs.setCLOB(1, clob);
        cs.executeUpdate();
        cs.close();
      }
    }
    catch (SQLException e) {}
    clob = null;
  }
}

//----------------------------------------------------------------------

protected void freeTemporary (CLOB clob)
{
  if (clob != null) {
    try { if (clob.isTemporary()) clob.freeTemporary(); }
    catch (SQLException e) {}
  }
}

//----------------------------------------------------------------------
// Encoding support:
//----------------------------------------------------------------------

protected void obtainCharset ()
{
  if (m_cset == null) {
    try {
      shell.log(4,"obtainCharset: getting DB charset...");
      m_cset = CharacterSet.make(((OracleConnection)m_conn).getDbCsId());
    }
    catch (SQLException e) {
      shell.log(4,"obtainCharset: creating default charset...");
      m_cset = CharacterSet.make(CharacterSet.DEFAULT_CHARSET);
    }
  }
}

//----------------------------------------------------------------------

protected String getDBCharsetName ()
{
  String s = "";
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(
        "begin ? := NLS_CHARSET_NAME(?); end;");
    cs.registerOutParameter(1, OracleTypes.VARCHAR);
    cs.setInt(2, ((OracleConnection)m_conn).getDbCsId());
    cs.executeUpdate();
    s = cs.getString(1);
    if (cs.wasNull()) s = "";
    cs.close();
  }
  catch (SQLException e) {}
  return s;
}

//----------------------------------------------------------------------

/**
 * attempts to convert current Oracle database charset ID to IANA charset name
 * using UTL_GDK or, if unavailable, UTL_I18N packages.
 *
 * @return	Oracle charset name if mapping succeeded, <code>ianacs</code> if not
 */
protected String getIANACharsetName ()
{
  String s = null;
  try 
  {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(
        "begin ? := SYS.UTL_GDK.charset_map(NLS_CHARSET_NAME(?), SYS.UTL_GDK.ORACLE_TO_IANA); end;");
    cs.registerOutParameter(1, OracleTypes.VARCHAR);
    cs.setInt(2, ((OracleConnection)m_conn).getDbCsId());
    cs.executeUpdate();
    s = cs.getString(1);
    if (cs.wasNull()) s = null;
    cs.close();
  }
  catch (SQLException egdk) 
  {
    try 
    {
      // try 10g package if the above failed
      OracleCallableStatement cs =
        (OracleCallableStatement)m_conn.prepareCall(
          "begin ? := SYS.UTL_I18N.map_charset(NLS_CHARSET_NAME(?)); end;");
      cs.registerOutParameter(1, OracleTypes.VARCHAR);
      cs.setInt(2, ((OracleConnection)m_conn).getDbCsId());
      cs.executeUpdate();
      s = cs.getString(1);
      if (cs.wasNull()) s = null;
      cs.close();
    }
    catch (SQLException ei18n) { }
  }
  return s;
}

/**
 * attempts to convert IANA charset name to Oracle charset name
 * using UTL_GDK or, if unavailable, UTL_I18N packages.
 *
 * @param	ianacs	IANA charset name
 * @return	Oracle charset name if mapping succeeded, <code>ianacs</code> if not
 */
protected String getOracleCharsetName ( String ianacs )
{
  String s = null;
  try 
  {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(
        "begin ? := SYS.UTL_GDK.charset_map(?, SYS.UTL_GDK.IANA_TO_ORACLE); end;");
    cs.registerOutParameter(1, OracleTypes.VARCHAR);
    cs.setString(2, ianacs);
    cs.executeUpdate();
    s = cs.getString(1);
    if (cs.wasNull()) s = null;
    cs.close();
  }
  catch (SQLException egdk) 
  {
    try 
    {
      // try 10g package if the above failed
      OracleCallableStatement cs =
        (OracleCallableStatement)m_conn.prepareCall(
          "begin ? := SYS.UTL_I18N.map_charset(?, SYS.UTL_I18N.GENERIC_CONTEXT, SYS.UTL_I18N.IANA_TO_ORACLE); end;");
      cs.registerOutParameter(1, OracleTypes.VARCHAR);
      cs.setString(2, ianacs);
      cs.executeUpdate();
      s = cs.getString(1);
      if (cs.wasNull()) s = null;
      cs.close();
    }
    catch (SQLException ei18n) { }
  }
  return s;
}

//----------------------------------------------------------------------

protected CHAR encode (String z)
{
  int n = z.length();
  byte[] au = new byte[n];
  int c;
  for (int i = 0; i < n; i++) 
  {
    c = (int)z.charAt(i);
    if ( c > 0xFF )
      // possibly Unicode string, need not be converted
      try
      {
        return new CHAR(z, this.m_cset);
      }
      catch(SQLException e)
      {
        au[i] = (byte)(c & 0xFF);
      }
    else
      au[i] = (byte)(c & 0xFF);
  }
  return new CHAR(au, this.m_cset);
}

/*
protected CHAR encode(String z)
{
  try
  {
    return new CHAR(unescape(z, this.effCharset), this.m_cset);
  }
  catch(SQLException e)
  {
    try
    {
      return new CHAR(z.getBytes(this.effCharset), this.m_cset);
    }
    catch(UnsupportedEncodingException uee)
    {
      return new CHAR(z.getBytes(), this.m_cset);
    }
  } 
}
*/

//----------------------------------------------------------------------

protected String escape (String s)
{
  return escape(s, this.effCharset);
}

protected String escape (String s, String enc)
{
  if (s == null) return "";
  if (s.length() > 0) {
    try { return URLEncoder.encode(s, enc); }
    catch (UnsupportedEncodingException uee) {
      shell.log(0, "escape: ", uee.getMessage());
    }
  }
  return s;
}

//----------------------------------------------------------------------

protected String unescape (String s)
{
  return unescape(s, this.effCharset);
}

protected String unescape (String s, String enc)
{
  if (s == null) return "";
  if (s.length() > 0) {
    try { return URLDecoder.decode(s, enc); }
    catch (UnsupportedEncodingException uee) 
    {
      shell.log(0, "unescape: ", uee.getMessage());
    }
    catch (IllegalArgumentException iae)
    {
      shell.log(0, "unescape: ", iae.getMessage());
    }
  }
  return s;
}

//----------------------------------------------------------------------
// Debug support:
//----------------------------------------------------------------------

protected void printHex (byte[] auBuf, int iOfs, int iLen)
{
  int u;
  StringBuffer sb = new StringBuffer(80);
  int k = 0;
  for (int i = iOfs; i < iLen; i++) {
    sb.append(' ');
    u = ((int)(auBuf[i])) & 0x0FF;
    if (u < 0x10) sb.append('0');
    sb.append(Integer.toHexString(u));
    k = (k + 1) & 0x0F;
    if (k == 0) {
      shell.log(4, sb.toString().toUpperCase());
      sb.setLength(0);
    }
  }
  if (k != 0) shell.log(4, sb.toString().toUpperCase());
}

//----------------------------------------------------------------------

protected void printHex (String s, int m)
{
  int u;
  StringBuffer sb = new StringBuffer(80);
  int n = s.length();
  if (n > m) n = m;
  shell.log(4, s.substring(0, n));
  int k = 0;
  for (int i = 0; i < n; i++) {
    sb.append(' ');
    u = (int)s.charAt(i);
    if (u < 0x10) sb.append('0');
    sb.append(Integer.toHexString(u));
    k = (k + 1) & 0x0F;
    if (k == 0) {
      shell.log(4, sb.toString().toUpperCase());
      sb.setLength(0);
    }
  }
  if (k != 0) shell.log(4, sb.toString().toUpperCase());
}

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 2.01 11.2015$";
} // class Processor

//----------------------------------------------------------------------
