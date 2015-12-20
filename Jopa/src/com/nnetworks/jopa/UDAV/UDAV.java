//----------------------------------------------------------------------
// JOPA.UDAV.java
//
// $Id: UDAV.java,v 1.9 2006/12/01 10:00:00 Drew Exp $
//
//----------------------------------------------------------------------

package com.nnetworks.jopa.UDAV;

import com.nnetworks.jopa.UDAV.*;
import com.nnetworks.jopa.*;

import org.jdom.*;
import org.jdom.input.*;
import org.jdom.output.*;
import org.jdom.xpath.*;

import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import java.text.*;
import java.util.*;
import java.util.zip.*;
import java.net.URLEncoder;
import java.net.URLDecoder;

//----------------------------------------------------------------------

class UDAV extends Processor
{

//----------------------------------------------------------------------

protected static final String LOCK_TOKEN_SCHEMA = "opaquelocktoken:";

//----------------------------------------------------------------------

protected static final int BUFFER_SIZE = 8192;

//----------------------------------------------------------------------

// These things are received from the shell:
protected JOPAShell           shell;
protected PathInfo            pi;
protected Properties          props;
protected HttpServletRequest  request;
protected HttpServletResponse response;
protected ServletContext      context;

protected String       requestCharset;
protected String       responseCharset;
protected String       effCharset;

protected String       sReqBase;
protected String       sReqRelBase;
protected String       sReqPath;
protected String       sReqName;
protected int          iTop;
protected int          iDepth;

protected Document     xreqDoc;
protected Element      xreqElem;
protected List         xreqList;

protected Namespace    xrespNS;
protected Namespace    xrespNSb;
protected Document     xrespDoc;
protected Element      xrespElem;
protected int          iPref;

// LockTable:
protected LockTable    locks;

protected StringBuffer m_sb;

protected String       m_localbase;
protected IfHeader     m_ifheader;

protected String       AcceptEncoding;

final static int       ENC_GZIP    = 1;
final static int       ENC_DEFLATE = 2;

protected final static int AUTH_NO_CREDENTIALS      = -1;
protected final static int AUTH_WRONG_CREDENTIALS   = 0;
protected final static int AUTH_SUCCESS             = 1;

protected final static String ACCESS_MODE_READONLY  = "r";
protected final static String ACCESS_MODE_READWRITE = "rw";

/**
 *  Defines method access modes (read-only, read-write).
 *
 *  Should be overridden/initialized in descendants of this class.
 */
protected String       accessModeRequired;

protected boolean      canCompress = true;

public final static String HTTP_OK = "OK";
public final static String HTTP_CREATED = "Created";
public final static String HTTP_NO_CONTENT = "No content";
public final static String HTTP_BAD_REQUEST = "Bad request";
public final static String HTTP_BAD_METHOD = "Method not supported";
public final static String HTTP_BAD_GATEWAY = "Bad gateway";
public final static String HTTP_NOT_FOUND = "Not found";
public final static String HTTP_LOCKED = "Locked";
public final static String HTTP_INSUFFICIENT_SPACE = "Insufficient storage";
public final static String HTTP_CONFLICT = "Conflict";
public final static String HTTP_PRECONDITION_FAILED = "Precondition failed";
public final static String HTTP_PARTIAL_CONTENT = "Partial content";
public final static String HTTP_MULTI_STATUS = "Multi-Status";
public final static String HTTP_NOT_MODIFIED = "Not modified";
public final static String HTTP_FORBIDDEN = "Forbidden";
public final static String HTTP_AUTHORIZATION_REQUIRED = "Authorization required";
public final static String HTTP_NOT_SATISFIABLE = "Requested range not satisfiable";

//----------------------------------------------------------------------

/**
 * Initialize the method - common initialization
 *
 * @param	shell	JOPAShell instance (used for logging)
 * @param	pi	PathInfo instance
 * @param	props	Servlet Properties
 * @param	request	Request encapsulation object
 * @param	response	Response encapsulation object
 * @param	context	Servlet context
 *
 */
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

  this.requestCharset = this.request.getCharacterEncoding();
  if (this.requestCharset == null) this.requestCharset = "UTF-8";
  this.responseCharset = this.response.getCharacterEncoding();
  if (this.responseCharset == null) this.requestCharset = this.requestCharset;
  // ignory any charset rather then 
  this.effCharset = this.props.getProperty("charset", this.requestCharset);
  //this.effCharset = "UTF-8";

  this.iTop   = 0;
  this.iDepth = 0;
  this.iPref  = 0;

  m_sb = new StringBuffer(256);
  try
  {
    m_localbase = new File(this.props.getProperty("localbase")).getCanonicalPath();
  }
  catch(IOException e)
  {
    m_localbase = null;
  }

  shell.log(3,"localbase : "+m_localbase);
  AcceptEncoding = this.request.getHeader("Accept-Encoding");

  afterInit();
}

//----------------------------------------------------------------------

/**
 * Additional initialization - may be overridden in children to perform
 * additional initialization after main init routine.
 *
 * In case this method is overridden in descendant, the overriding implementation
 * MUST call super.afterInit().
 */
protected void afterInit ()
{
  for (int i = 1; i < this.pi.iCount; i++) {
    this.pi.asPathInfo[i] = unescape(this.pi.asPathInfo[i]);
  }
}
protected void checkLocalBase()
throws ServletException
{
  if(m_localbase == null)
    throw new ServletException("Local base directory is invalid or not specified.");
}

//----------------------------------------------------------------------
// Request support:
//----------------------------------------------------------------------

/**
 * Initializes certain request URIs.
 */
protected void reqURI ()
{
  //this.sReqRelBase = this.request.getServletPath()+"/"+this.pi.getItem(0);
   StringBuffer sb = new StringBuffer(256);
   sb.append("http://");
   sb.append(this.request.getHeader("Host"));
 //   int i = this.request.getRequestURI().indexOf(this.request.getServletPath());
   shell.log(4,"getRequestURI: "+this.request.getRequestURI());
   int i = this.request.getRequestURI().indexOf(getServletPath);
   if (i > 0)
     sb.append(this.request.getRequestURI().substring(0,i+getServletPath.length()));
   else
   sb.append(getServletPath);
   sb.append('/');
   sb.append(this.pi.getItem(0));
  
  this.sReqRelBase = sb.toString();
  this.sReqBase = this.sReqRelBase;
  
  //this.sReqBase = "http://"+this.request.getHeader("Host")+this.sReqRelBase;
  //
  this.sReqPath = this.pi.merge(1, -1);
  //
  this.sReqName = this.pi.getItem(this.pi.iLast);
  
  shell.log(3,"Request Base: "+sReqBase);
  shell.log(3,"Request Relative Base: "+sReqRelBase);
  shell.log(3,"Request Path: "+sReqPath);
  shell.log(3,"Request Name: "+sReqName);
}

//----------------------------------------------------------------------

/**
 * Parses request destination argument.
 */
protected PathInfo reqDst ()
{
  String sDst = this.request.getHeader("Destination");
  if (sDst == null) return null;
  PathInfo dpi = new PathInfo(sDst, this.request.getServletPath());
  for (int i = 1; i < dpi.iCount; i++) {
    dpi.asPathInfo[i] = unescape(dpi.asPathInfo[i]);
  }
  return dpi;
}

//----------------------------------------------------------------------

/**
 * Parses request Depth argument.
 *
 * @param	iTopDef		default top-level depth
 * @param	iDepthDef	default depth
 *
 */
protected void reqDepth (int iTopDef, int iDepthDef)
{
  String sDepth = this.request.getHeader("Depth");
  if (sDepth == null)
    { this.iTop = iTopDef;  this.iDepth = iDepthDef; }
  else if (sDepth.equals("0"))
    { this.iTop = 0;  this.iDepth = 0; }
  else if (sDepth.equals("1"))
    { this.iTop = 0;  this.iDepth = 1; }
  else if (sDepth.equalsIgnoreCase("1,noroot"))
    { this.iTop = 1;  this.iDepth = 1; }
  else if (sDepth.equalsIgnoreCase("infinity"))
    { this.iTop = 0;  this.iDepth = Integer.MAX_VALUE; }
  else
    { this.iTop = iTopDef;  this.iDepth = iDepthDef; }
}

//----------------------------------------------------------------------

/**
 * Parses request Lock-Token. If Lock-Token is not present, attempts
 * to parse If header and find the corresponding lock.
 *
 */
protected String reqLockToken ()
{
  String sLockToken = this.request.getHeader("Lock-Token");
  if (sLockToken == null || sLockToken.length() == 0)
  {

    String sIfh = this.request.getHeader("If");
    if (sIfh == null)
      return null;
    else
    {
      if (m_ifheader == null) 
        m_ifheader = new IfHeader();
      else
        m_ifheader.clear();
      m_ifheader.parse(sIfh);
 
      Vector _locks = locks.enumLocks(null, null, true); // all active locks
      if (_locks != null)
        for (int i = 0; i < _locks.size(); i++)
        {
          LockEntry _lock = (LockEntry)_locks.elementAt(i);
          if ( m_ifheader.match(null, _lock.sToken, null) ) // we're only interested in finding the token
          {
            sLockToken = _lock.sToken;
            break;
          }
        }
      // did we manage to find the lock from the If header?
      if (sLockToken == null)
        return null;
    }

  }
  if (sLockToken.startsWith("<") && sLockToken.endsWith(">"))
  {
    sLockToken = sLockToken.substring(1, sLockToken.length() - 1);
  }
  int i = sLockToken.indexOf(':');
  if (i >= 0) {
    sLockToken = sLockToken.substring(i+1);
  }
  return sLockToken;
}

//----------------------------------------------------------------------

/**
 * Checks if the file is locked.
 *
 * @param	file	file to check for locks
 *
 * @return	true if file is locked, false otherwise. The file is locked
 *		when there are locks on the file itself that are not included
 *              in the If header, or there are locks on any of its parent
 *              collections that are not included in the If header.
 */
protected boolean isLocked (File file)
{
  LockEntry lock;
  int i, j;

  shell.log(4,"Checking locks...");
    return false; // forget about locks
  // Are there locks?
  /*
  openLocks();
  String sSchema = this.pi.getItem(0);
  Vector _locks = locks.enumLocks(sSchema, calcETag(file), true);
  int _lockslen = _locks.size();

  if (_locks == null || _lockslen == 0) 
  {
    shell.log(4, "No locks defined, check complete.");
    return false;
  }
  else
    shell.log(4, _lockslen+" locks defined, evaluating...");

  // We got some locks, what about If header?
  String sIfh = this.request.getHeader("If");
  if (sIfh == null || sIfh.length() == 0) 
  {
    shell.log(4, "There are locks and no If header passed, assume resource is locked.");
    return true; // there is some lock and it's apparently not ours
  }


  // now we will check if at least one lock exists on this resource or its parents
  // that matches some lock in If header 

  if (m_ifheader == null) 
    m_ifheader = new IfHeader();
  else
    m_ifheader.clear();

  m_ifheader.parse(sIfh);
  //m_ifheader.spy();

  // Match to If-header:
  String sResource = this.sReqPath;
  for (i = 0; i < _lockslen; i++) {
    lock = (LockEntry) _locks.elementAt(i);
    if (!m_ifheader.match(sResource, lock.sToken, lock.sETag)) 
    {
      shell.log(4, "Foreign lock found, resource is locked.");
      return true; // foreign lock found, we didn't place it
    }
  }

  // Scan upward:
  File f = file;
  for (j = this.pi.iLast; j > 1; j--) {
    // Has parent?
    f = f.getParentFile();
    if (f == null) break;
    if (!f.isDirectory()) break;
    // Are there locks?
    _locks = locks.enumLocks(sSchema, calcETag(f), true);
    if (_locks != null) {
      int n = _locks.size();
      if (n > 0) {
        // Match to If-header:
        sResource = this.pi.merge(1, j);
        for (i = 0; i < n; i++) {
          lock = (LockEntry) _locks.elementAt(i);
          if (lock.cDepth == 'I') {
            if (!m_ifheader.match(sResource, lock.sToken, lock.sETag)) 
            {
               shell.log(4, "Foreign lock found, resource is locked.");
               return true; // foreign lock found
            }
          }
        }
      }
    }
  }

  shell.log(4, "No foreign locks found, resource is not locked.");
  return false;*/
}

//----------------------------------------------------------------------

/**
 *  Checks if any children of the file (which is assumed to be a collection)
 *  are locked.
 *
 *  @param 	file	collection to check
 *  @param	sResource	the resouce name
 *  @param	dn	check depth, currently not used
 *
 *  @return	true if there are locked children of this collection, false otherwise.
 */
protected boolean lockedAnyChild (File file, String sResource, int dn)
{
  int i, j, n;
  Vector _locks;
  File f;
  String sSchema = this.pi.getItem(0);
  File[] af = file.listFiles();
  for (i = 0; i < af.length; i++) {
    f = af[i];
    _locks = locks.enumLocks(sSchema, calcETag(f), true);
    if (_locks != null) {
      n = _locks.size();
      if (n > 0) {
        if (dn > 0) {
          for (j = 0; j < n; j++) {
            LockEntry lock = (LockEntry) _locks.elementAt(j);
            if (!m_ifheader.match(sResource + '/' + f.getName(), lock.sToken, lock.sETag)) return true;
          }
        }
        else {
          return true;
        }
      }
    }
  }
  for (i = 0; i < af.length; i++) {
    f = af[i];
    if (f.isDirectory()) {
      if (lockedAnyChild(f, sResource + '/' + f.getName(), dn)) return true;
    }
  }
  return false;
}

/**
 * Checks if the file is a directory and has any locked children.
 * Automatically responds with multistatus message if so.
 * 
 * @param	file	directory to check for locked children
 *
 * @return	true if there were any locked children (note that the
 *              multistatus response was already created if so.)
 *              false if no children are locked or the file is not a directory.
 *
 */
protected boolean reportLockedChildren(File file)
{
    if (file.isDirectory())
    {
      if (lockedAnyChild(file, this.sReqPath, 0)) 
      {
        // report all locked objects under this directory via multistatus
        respStatus(207, HTTP_MULTI_STATUS);
        respDoc("multistatus");
        String sSchema = this.pi.getItem(0);
        Element elem;
        Vector _locks = locks.enumLocks(sSchema,null,true);
        shell.log(3,"Found "+_locks.size()+" locks, evaluating");
        for (int i = 0; i < _locks.size(); i++)
        if ( ((LockEntry)_locks.elementAt(i)).href.startsWith(this.sReqPath) )
          {
            shell.log(3,((LockEntry)_locks.elementAt(i)).href+" is under "+this.sReqPath+" and is locked.");
            elem = appElem(this.xrespElem, "response", null, null);
            appElem(elem, "href", null, makeRelHRef(((LockEntry)_locks.elementAt(i)).href,file));
            appElem(elem, "status", null, "HTTP/1.1 423 Locked");
          }

        respXMLContent("UTF-8");
        return true;
      }
    }
    return false;
}

//----------------------------------------------------------------------
/**
 *  Parses request XML content.
 *
 *  @return	true if successfully parsed, false otherwise.
 *
 */
protected boolean reqXMLContent ()
throws IOException
{
  SAXBuilder builder = new SAXBuilder();
  builder.setValidation(false);
  builder.setIgnoringElementContentWhitespace(true);
  try {
    this.xreqDoc = builder.build(this.request.getInputStream());
    this.xreqElem = this.xreqDoc.getRootElement();
    /* */  spyXMLContent(4, "=> (XML):", this.xreqDoc, "ISO-8859-5");
  }
  catch (JDOMException e) {
    shell.log(0, "reqXMLContent/XML: ", e.getMessage());
    return false;
  }
  return true;
}

//----------------------------------------------------------------------
// Guess:
//----------------------------------------------------------------------

/**
 * Attempts to guess content type of the file by its name.
 *
 * @param	sName	file name
 *
 * @return	guessed content type
 */
protected String guessContentType (String sName)
{
  String sExt = sName.toLowerCase();
  if (sExt.endsWith(".gif"))  return "image/gif";
  if (sExt.endsWith(".jpg"))  return "image/pjpeg";
  if (sExt.endsWith(".png"))  return "image/png";
  if (sExt.endsWith(".bmp"))  return "image/bmp";
  if (sExt.endsWith(".ico"))  return "image/x-icon";
  if (sExt.endsWith(".zip"))  return "application/x-zip-compressed";
  if (sExt.endsWith(".jar"))  return "application/x-zip-compressed";
  if (sExt.endsWith(".rar"))  return "application/x-rar-compressed";
  if (sExt.endsWith(".dpsp")) return "text/plain";
  if (sExt.endsWith(".html")) return "text/html";
  if (sExt.endsWith(".shtml")) return "text/html";
  if (sExt.endsWith(".htm"))  return "text/html";
  if (sExt.endsWith(".wml"))  return "text/vnd.vap.wml";
  if (sExt.endsWith(".css"))  return "text/css";
  if (sExt.endsWith(".js"))   return "text/javascript";
  if (sExt.endsWith(".txt"))  return "text/plain";
  if (sExt.endsWith(".data")) return "text/plain";
  if (sExt.endsWith(".jsp"))  return "text/jsp";
  if (sExt.endsWith(".asp"))  return "text/asp";
  return "application/octet-stream";
}

/**
 * Synonym to {@link guessContentType}.
 *
 */
protected String guessDataType (String sName)
{
  return guessContentType(sName);
}

//----------------------------------------------------------------------
// Responses:
//----------------------------------------------------------------------

protected void respFatal (int iCode, String sMsg)
{
  try
  {
    this.response.sendError(iCode, sMsg);
  }
  catch(IOException ioe) {}
  shell.log(0, sMsg);
}

//----------------------------------------------------------------------

protected void respFatal (int iCode, Exception e)
{
  try
  {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    e.printStackTrace(pw);
    pw.close();
    this.response.sendError(iCode, sw.toString());
  }
  catch( IOException ioe ) {}
  e.printStackTrace(shell.getLogStream(0));
}

//----------------------------------------------------------------------

protected void respStatus (int iCode, String sMsg)
{
  this.response.setStatus(iCode);
  m_sb.setLength(0);
  m_sb.append(this.request.getProtocol());
  m_sb.append(' ');
  m_sb.append(Integer.toString(iCode));
  m_sb.append(' ');
  m_sb.append(sMsg);
  shell.log(3, "<= ", m_sb.toString());
}

//----------------------------------------------------------------------

protected void respContentType (String sMimeType)
{
  String sValue = sMimeType.toLowerCase();
  this.response.setContentType(sMimeType);
  shell.log(3, "<= Content-Type: ", sMimeType);
  canCompress = !(sValue.startsWith("image") ||
                  sValue.indexOf("compress") > 0);
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
    shell.log(3, "<= set-cookie: ", cookie.getName(), "=", cookie.getValue());
  }
}

//----------------------------------------------------------------------

protected void respWebDAV (int i)
{
  if (i > 0) respHeader("Server", shell.getVersion());
  if (i > 1) respHeader("MS-Author-Via", "DAV");
  if (i > 2) respHeader("Allow",
    "GET,HEAD,OPTIONS,DELETE,PUT,COPY,MOVE,MKCOL,PROPFIND,LOCK,UNLOCK");
  if (i > 3) respHeader("DAV", "1,2");
}

//----------------------------------------------------------------------

protected void respAuthenticate (String sMsg, String sRealm)
{
  respStatus(401, sMsg);
  respHeader("WWW-Authenticate", "Basic realm=\"" + sRealm + '\"');
}

/** ****************************************************************************
 * Send full file content.
 *
 * @param	file	content source
 */
protected void respFileContent (File file)
{
  try {

    OutputStream out;
    switch (getDesiredContentEncoding())
    {
      case ENC_GZIP:
         respHeader("Content-Encoding","gzip");
         out = new GZIPOutputStream(this.response.getOutputStream());
         shell.log(3, "Applying GZIP content encoding to the output...");
         break;
      case ENC_DEFLATE:
         respHeader("Content-Encoding","deflate");
         out = new DeflaterOutputStream(this.response.getOutputStream());
         shell.log(3, "Applying Deflate content encoding to the output...");
         break;
      default:
         respContentLength((int)file.length());
         out = this.response.getOutputStream();
    }
    FileInputStream in = new FileInputStream(file);
    byte[] buf = new byte[8192];
    int rd;
    while ((rd = in.read(buf)) > 0)
      out.write(buf, 0, rd);
    in.close();
    out.close();
  }
  catch (IOException e) {
    shell.log(0, "respContent/IO: ", e.getMessage());
  }
}

/** ****************************************************************************
 * Send partial file content.
 *
 * @param	file	content source
 * @param	start	start byte index (0-based)
 * @param	stop	stop byte index (0-based)
 *
 */
protected void respFileContent (File file, long start, long stop)
{
  try {
    OutputStream out = this.response.getOutputStream();
    FileInputStream in = new FileInputStream(file);
    in.skip(start);
    long len = stop-start+1;
    byte[] buf = new byte[8192];
    int rd;
    while ( ( rd = in.read(buf) ) > 0 && len > 0 ) 
    {
      out.write(buf, 0, (int)((rd <= len) ? rd : len));
      len -= rd;
    }
    in.close();
    out.close();
  }
  catch (IOException e) {
    shell.log(0, "respContent/IO: ", e.getMessage());
  }
}



/** ****************************************************************************
 * Streams current response XML document to the output.
 *
 * @param	enc	content charset
 *
 */
protected void respXMLContent (String enc)
{
  respContentType("text/xml; charset=\"" + enc + '\"');
  try {
    OutputStream out;
    switch (getDesiredContentEncoding())
    {
      case ENC_GZIP:
         respHeader("Content-Encoding","gzip");
         out = new GZIPOutputStream(this.response.getOutputStream());
         shell.log(3, "Applying GZIP content encoding to the output...");
         break;
      case ENC_DEFLATE:
         respHeader("Content-Encoding","deflate");
         out = new DeflaterOutputStream(this.response.getOutputStream());
         shell.log(3, "Applying Deflate content encoding to the output...");
         break;
      default:
         out = this.response.getOutputStream();
    }
    org.jdom.output.Format frm = org.jdom.output.Format.getPrettyFormat().setEncoding(enc);
    XMLOutputter xmlout = new XMLOutputter(frm);
    xmlout.output(this.xrespDoc, out);
    out.close();
    /* */ spyXMLContent(4, "<= (XML):", this.xrespDoc, enc);
  }
  catch (IOException e) {
    shell.log(0, "respXMLContent/IO: ", e.getMessage());
  }
}

/** ****************************************************************************
 *  Creates new response XML document. The "DAV:" namespace is automatically
 *  assigned for the new document.
 *
 *  @param	sTag0	root tag for the new document
 */
protected void respDoc (String sTag0)
{
  this.xrespNS  = Namespace.getNamespace("DAV:");
  this.xrespNSb = Namespace.getNamespace("b",
                      "urn:uuid:c2f41010-65b3-11d1-a29f-00aa00c14882");
  this.xrespElem = new Element(sTag0, this.xrespNS);
  this.xrespDoc = new Document(this.xrespElem);
  this.xrespElem.addNamespaceDeclaration(this.xrespNSb);
}

/** ****************************************************************************
 * Appends new XML element to the parent element.
 *
 * @param	parent	parent element
 * @param	sName	new element name
 * @param	ns	new element namespace (if null, assumed current document namespace)
 * @param	sText	new element inner text
 *
 * @return	created element
 */
protected Element appElem
( Element   parent
, String    sName
, Namespace ns
, String    sText
)
{
  if (ns == null) {
    ns = this.xrespNS;
  }
  Element elem = new Element(sName, ns);
  parent.addContent(elem);
  if (sText != null) {
    elem.setText(sText);
  }
  return elem;
}

/** ****************************************************************************
 * Sets an attribute for the element
 *
 * @param	elem	element to attach the attribute to
 * @param	sName	attribute name
 * @param	ns	attribute namespace
 * @param	sText	attribute value
 */
protected void appAttr
( Element   elem
, String    sName
, Namespace ns
, String    sText
)
{
  elem.setAttribute(sName, sText, ns);
}

//----------------------------------------------------------------------

/** ****************************************************************************
 * Borrows namespace from the element. 
 *
 * @param	elem	element to borrow namespace from
 *
 * @return 	If the element namespace is "DAV:" then null, otherwise
 *              element namespace. 
 */
protected Namespace borrowNS
( Element elem
)
{
  return elem == null ? null :
         elem.getNamespaceURI().equals("DAV:") ? null :
         elem.getNamespace();
}

//----------------------------------------------------------------------
/*
<D:lockdiscovery>
   <D:activelock>
      <D:locktype><D:write/></D:locktype>
      <D:lockscope><D:exclusive/></D:lockscope>
      <D:depth>Infinity</D:depth>
      <D:owner>
         <D:href>http://www.ics.uci.edu/~ejw/contact.html</D:href>
      </D:owner>
      <D:timeout>Second-604800</D:timeout>
      <D:locktoken>
         <D:href>opaquelocktoken:e71d4fae-5dec-22d6-fea5-00a0c91e6be4</D:href>
      </D:locktoken>
   </D:activelock>
</D:lockdiscovery>
*/

/** ****************************************************************************
 * Generates activelock element and attaches it to the parent element.
 *
 * @param	parent	parent element
 * @param	ns	namespace
 * @param	lock	LockEntry object for which the activelock information
 *                      is to be generated.
 *
 */
protected void respActiveLock
( Element   parent
, Namespace ns
, LockEntry lock
)
{
  Element activelock, elem;

  if (lock.sToken != null) 
  {
    activelock = appElem(parent, "activelock", ns, null);

    elem = appElem(activelock, "locktype", ns, null);
            appElem(elem, "write",    ns, null);

    elem = appElem(activelock, "lockscope", ns, null);
            appElem(elem, lock.sScope, ns, null);

    if (lock.sOwner != null && lock.sOwner.startsWith("href::"))
    {
      elem = appElem(activelock, "owner", ns, null);
              appElem(elem, "href",  ns, lock.sOwner.substring(6));
    }
    else
      appElem(activelock, "owner", ns, lock.sOwner);


    elem = appElem(activelock, "locktoken", ns, null);
            appElem(elem, "href",      ns, LOCK_TOKEN_SCHEMA + lock.sToken);
    elem = appElem(activelock, "depth", ns, (lock.cDepth == '0' ? "0" : "Infinity"));
    elem = appElem(activelock, "timeout", ns, (lock.iTimeout < 0 ? "Infinite" : "Second-" + Integer.toString(lock.iTimeout)));

  }
}

/** ****************************************************************************
 * Generates lockdiscovery element for all active locks on specified resource
 * and attaches it to the parent element.
 *
 * @param	parent	parent element
 * @param	ns	namespace
 * @param	file	resource for which to generate lockdiscovery response.
 */
protected void respLockDiscovery
( Element   parent
, Namespace ns
, File      file
)
{
  Element elem1 = appElem(parent, "lockdiscovery", ns, null);
  openLocks();
  Vector _locks = this.locks.enumLocks
    ( this.pi.getItem(0)
    , calcETag(file)
    , true
    );
  int n = _locks.size();
  if (n > 0) {
    for (int i = 0; i < n; i++) {
      respActiveLock(elem1, ns, (LockEntry) _locks.elementAt(i));
    }
    _locks.removeAllElements();
  }
  _locks = null;
}

//----------------------------------------------------------------------
// LockTable support:
//----------------------------------------------------------------------

/** ****************************************************************************
 * Opens the lock file and loads the lock table
 *
 */
protected void openLocks ()
{
  if (this.locks == null) {
    String sName = "Locks_" + this.pi.getItem(0);
    this.locks = (LockTable) this.shell.getFromShelf(sName);
    if (this.locks == null) {
      this.locks = new LockTable(this.shell,
                         this.props.getProperty("lock_table", "jopa.lck"));
      this.shell.putOnShelf(sName, this.locks);
    }
  }
}

/** ****************************************************************************
 * Cleans up the lock table of any expired locks
 */
protected void cleanLocks ()
{
  if (this.locks != null) {
    this.locks.clean(0);
  }
}

//----------------------------------------------------------------------
// Auxiliary support:
//----------------------------------------------------------------------

protected int getInt (String s, int iDef)
{
  if (s != null) {
    try { return Integer.parseInt(s, 10); }
    catch (NumberFormatException e) {}
  }
  return iDef;
}

//----------------------------------------------------------------------
// Authentication support:
//----------------------------------------------------------------------

protected int authorize ()
{
  // Obtain authorization header:
  String sAuth = this.request.getHeader("Authorization");
  if (sAuth == null) sAuth = this.request.getHeader("HTTP_AUTHORIZATION");
  if (sAuth == null) sAuth = this.request.getHeader("AUTHORIZATION");
  if (sAuth == null) sAuth = this.request.getHeader("authorization");
  if (sAuth == null) sAuth = this.request.getHeader("http_authorization");
  if (sAuth == null) sAuth = this.request.getHeader("Http_Authorization");
  if (sAuth == null) return -1;

  // Parse the authorization data:
  int i = sAuth.indexOf("Basic");

  shell.log(3, "authorize: ",sAuth+" : "+this.requestCharset);

  if (i < 0) return AUTH_NO_CREDENTIALS;
  String sCode = Base64.decodeToString(sAuth.substring(i+6), this.requestCharset);
  if (sCode == null) sCode= Base64.decodeToString(sAuth.substring(i+6));
  
  shell.log(3, "authorize: ",sCode);
  
  if (sCode == null) return AUTH_NO_CREDENTIALS;

  i = sCode.indexOf(":");
  if (i <= 0) return AUTH_NO_CREDENTIALS;

  String sUser = sCode.substring(0, i).trim();
  String sPasw = sCode.substring(i+1).trim();

  String sUsername = this.props.getProperty("username", "*");
  String sPassword = Base64.decryptPasw(this.props.getProperty("password", "*"));

  shell.log(3, "Auth: ", sUser, "/", sPasw );
  shell.log(3, "DAD:  ", sUsername, "/", sPassword);

  // Check username/password;
  if (sUser.equalsIgnoreCase(sUsername) && sPasw.equals(sPassword)) {
    return AUTH_SUCCESS;
  }

  return AUTH_WRONG_CREDENTIALS;
}

/** ****************************************************************************
 *  attempts to perform authorization against XML-formatted user
 *  database specified by <code>user_db</code> DAD parameter. Note that
 *  this method adds dependencies on SAXPath and Jaxen as it uses XPath
 *  to match against the user database.
 *
 *  @return	AUTH_NO_CREDENTIALS if authorization failed due to error or missing credentials<br/>
 *              AUTH_WRONG_CREDENTIALS if supplied credentials didn't match<br/>
 *              AUTH_SUCCESS if authorization passed
 */
protected int authorizeFromDB ( String accessMode )
{

  String sAuthDB = this.props.getProperty("user_db");
  if (sAuthDB == null)
    return AUTH_NO_CREDENTIALS;

  Document authdb;
  try
  {
    authdb = (new SAXBuilder()).build(new File(sAuthDB));
  }
  catch(JDOMException dome) { return AUTH_NO_CREDENTIALS; }
  catch(IOException ioe) { return AUTH_NO_CREDENTIALS; }

  // Obtain authorization header:
  String sAuth = this.request.getHeader("Authorization");
  if (sAuth == null) sAuth = this.request.getHeader("HTTP_AUTHORIZATION");
  if (sAuth == null) sAuth = this.request.getHeader("AUTHORIZATION");
  if (sAuth == null) sAuth = this.request.getHeader("authorization");
  if (sAuth == null) sAuth = this.request.getHeader("http_authorization");
  if (sAuth == null) sAuth = this.request.getHeader("Http_Authorization");
  if (sAuth == null) return AUTH_NO_CREDENTIALS;

  // Parse the authorization data:
  int i = sAuth.indexOf("Basic");
  if (i < 0) return AUTH_NO_CREDENTIALS;

  String sCode = Base64.decodeToString(sAuth.substring(i+6), this.requestCharset);
  if (sCode == null) return AUTH_NO_CREDENTIALS;

  i = sCode.indexOf(":");
  if (i <= 0) return AUTH_NO_CREDENTIALS;

  String sUser = sCode.substring(0, i).trim();
  String sPasw = sCode.substring(i+1).trim();

  shell.log(3, "Auth: ", sUser, "/", sPasw );
  try
  {
    List nodes = XPath.selectNodes(authdb, 
                   "/udav-users/user[upper-case(@name) = \'"+sUser.toUpperCase()+
                   "\' and @password = \'"+sPasw+"\']");
    if (nodes.size() != 1)
      return AUTH_NO_CREDENTIALS; // allow to try again
    Element user = (Element)nodes.get(0);
    String mode = user.getAttributeValue("access-rights");
    // assume read-only access for users without explicit access mode set
    if (mode == null)
      mode = ACCESS_MODE_READONLY;
    shell.log(3,"Required access rights: "+accessModeRequired+", user has: "+mode);
    if (mode.toLowerCase().indexOf(accessModeRequired) < 0)
    {
      shell.log(3,"NOT Authorized.");
      return AUTH_WRONG_CREDENTIALS;
    }
    else
    {
      shell.log(3,"Authorized.");
      return AUTH_SUCCESS;
    } 
  }
  catch(Exception e) {}
  return AUTH_WRONG_CREDENTIALS;

}

/** ****************************************************************************
 *  authenticate user from Authorization HTTP header. The only supported
 *  authorization method is Basic.
 *
 *  @return	AUTH_NO_CREDENTIALS if authentication failed due to error or missing credentials, in addition 401 Authorization Required is sent automatically<br/>
 *              AUTH_WRONG_CREDENTIALS if authentication failed due to credentials mismatch, in addition 403 Forbidden response is sent automatically<br/>
 *              AUTH_SUCCESS if authentication was successful
 */
protected int authenticate()
{

  int i;
  try
  {
    i = authorizeFromDB(accessModeRequired);
  }
  catch(Exception e) 
  {
    shell.log(3,"authenticateFromDB() thrown exception:\r\n"+e.getMessage());
    i = AUTH_NO_CREDENTIALS; 
  }

  // try authorizing from the DAD username/password
  if (i <= AUTH_WRONG_CREDENTIALS)
    i = authorize();
  if (i < AUTH_WRONG_CREDENTIALS) 
  {
    // either no credentials were given or they didn't pass - give the user another chance
    respAuthenticate
    ( "Introduce yourself for " + this.pi.getItem(0)
    , this.pi.getItem(0)
    );
  }
  else if (i == AUTH_WRONG_CREDENTIALS) {
    respFatal(403, "<H1>403 Forbidden</H1>Your current credentials do now allow you to perform the requested operation.");
  }
  return i;
}

//----------------------------------------------------------------------
// XML support:
//----------------------------------------------------------------------

protected void spyXMLContent (int lev, String s, Document xdoc, String enc)
{
  if (xdoc != null) {
    XMLOutputter xmlout;
    org.jdom.output.Format frm = org.jdom.output.Format.getPrettyFormat().setEncoding("ISO-8859-5");
    PrintStream out = shell.getLogStream(lev);
    if (out != null) {
      out.println(s);
      try {
        xmlout = new XMLOutputter(frm.setEncoding(enc));
        xmlout.output(xdoc, out);
      }
      catch (IOException e) {}
    }
    out = shell.getDebugStream(4);
    if (out != null) {
      out.println(s);
      try {
        xmlout = new XMLOutputter(frm);
        xmlout.output(xdoc, out);
      }
      catch (IOException e) {}
    }
  }
}

//----------------------------------------------------------------------
// PropTable:
//----------------------------------------------------------------------

protected static final int
  PROP_UNKNOWN              =  0,
  PROP_getcontentlength     =  1,
  PROP_getcontentlanguage   =  2,
  PROP_getcontenttype       =  3,
  PROP_getetag              =  4,
  PROP_resourcetype         =  5,
  PROP_displayname          =  6,
  PROP_lockdiscovery        =  7,
  PROP_getlastmodified      =  8,
  PROP_creationdate         =  9,
  PROP_name                 = 10,
  PROP_parentname           = 11,
  PROP_href                 = 12,
  PROP_contentclass         = 13,
  PROP_lastaccessed         = 14,
  PROP_defaultdocument      = 15,
  PROP_ishidden             = 16,
  PROP_iscollection         = 17,
  PROP_isreadonly           = 18,
  PROP_isroot               = 19,
  PROP_isstructureddocument = 20,
  PROP_supportedlock        = 21;

//----------------------------------------------------------------------

private static Hashtable  s_hashProps;

//----------------------------------------------------------------------

protected static void initProps()
{
  s_hashProps = new Hashtable(16);
  s_hashProps.put("getcontentlength",   new Integer(PROP_getcontentlength)  );
  s_hashProps.put("getcontentlanguage", new Integer(PROP_getcontentlanguage));
  s_hashProps.put("getcontenttype",     new Integer(PROP_getcontenttype)    );
  s_hashProps.put("getetag",            new Integer(PROP_getetag)           );
  s_hashProps.put("resourcetype",       new Integer(PROP_resourcetype)      );
  s_hashProps.put("displayname",        new Integer(PROP_displayname)       );
  s_hashProps.put("lockdiscovery",      new Integer(PROP_lockdiscovery)     );
  s_hashProps.put("getlastmodified",    new Integer(PROP_getlastmodified)   );
  s_hashProps.put("creationdate",       new Integer(PROP_creationdate)      );
  s_hashProps.put("name",               new Integer(PROP_name)              );
  s_hashProps.put("href",               new Integer(PROP_href)              );
  s_hashProps.put("contentclass",       new Integer(PROP_contentclass)      );
  s_hashProps.put("lastaccessed",       new Integer(PROP_lastaccessed)      );
  s_hashProps.put("ishidden",           new Integer(PROP_ishidden)          );
  s_hashProps.put("iscollection",       new Integer(PROP_iscollection)      );
  s_hashProps.put("isreadonly",         new Integer(PROP_isreadonly)        );
  s_hashProps.put("isroot",             new Integer(PROP_isroot)            );
  s_hashProps.put("supportedlock",      new Integer(PROP_supportedlock)     );
  s_hashProps.put("parentname",         new Integer(PROP_parentname)        );
}

protected static int mapProp (String sProp)
{
  if (s_hashProps == null) initProps();
  Object o = s_hashProps.get(sProp);
  if (o != null) return ((Integer)o).intValue();
  return PROP_UNKNOWN;
}

protected static Enumeration enumPropNames ()
{
  if (s_hashProps == null) initProps();
  return s_hashProps.keys();
}

protected static Enumeration enumPropCodes ()
{
  if (s_hashProps == null) initProps();
  return s_hashProps.elements();
}

//----------------------------------------------------------------------
// Formatting:
//----------------------------------------------------------------------

protected static SimpleDateFormat dfGMT = null;

protected String fmtGMT (long date)
{
  return fmtGMT(new Date(date));
}

protected String fmtGMT (Date date)
{
  if (date != null) {
    if (dfGMT == null) {
      dfGMT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.UK);
      dfGMT.setTimeZone(TimeZone.getTimeZone("GMT"));
    }
//    return dfGMT.format(date)+" GMT";
      return dfGMT.format(date);
  }
  return null;
}

//----------------------------------------------------------------------

protected static SimpleDateFormat dfISO = null;

protected String fmtISO8601 (long date)
{
  return fmtISO8601(new Date(date));
}

protected String fmtISO8601 (Date date)
{
  if (date != null) {
    if (dfISO == null) {
      dfISO = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.UK);
      dfISO.setTimeZone(TimeZone.getTimeZone("GMT"));
    }
    return dfISO.format(date).replace('_', 'T')+"Z";
  }
  return null;
}

//----------------------------------------------------------------------

protected static SimpleDateFormat dfDFile = null;

protected String fmtDFile (long date)
{
  return fmtDFile(new Date(date));
}

protected String fmtDFile (Date date)
{
  if (date != null) {
    if (dfDFile == null) {
      dfDFile = new SimpleDateFormat("dd.MM.yy HH:mm:ss", Locale.UK);
      dfDFile.setTimeZone(TimeZone.getTimeZone("GMT"));
    }
    return dfDFile.format(date);
  }
  return null;
}

protected static SimpleDateFormat dfpGMT = null;

protected static Date parseGMTDate(String date)
{
  if (date != null)
  {
    if (dfpGMT == null)
      dfpGMT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.UK);
    try
    {
      return dfpGMT.parse(date);
    }
    catch(ParseException pe)
    {
      return null;
    }
  }
  return null;
}

//----------------------------------------------------------------------
// Encodind/decoding:
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
    catch (UnsupportedEncodingException uee) {
      shell.log(0, "unescape: ", uee.getMessage());
    }
  }
  return s;
}

//----------------------------------------------------------------------
// File access:
//----------------------------------------------------------------------

protected File locateResource (String sPath)
{
  if (m_localbase == null)
    return null;
  File rsrc = new File(m_localbase, sPath);
  // make sure we did not escape our local base
  try
  {
    return !rsrc.getCanonicalPath().toLowerCase().startsWith(m_localbase.toLowerCase()) ? null : rsrc;
  }
  catch(IOException e)
  {
    return null;
  }

}

/** ****************************************************************************
 *  Calculates e-tag for specified file or directory
 *
 *  @param	file	File or directory for which to calculate e-tag
 *
 *  @return	calculated e-tag
 */
protected String calcETag (File file)
{
  return ("\""+(file.isDirectory() ? "d" : "f")+file.hashCode()+Integer.toHexString(file.hashCode())+"-"+Long.toHexString(file.lastModified())+"\"").toLowerCase();
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
 if ( this.props.getProperty("compression_enabled", "0").equals("0") ||
      !canCompress ||
      AcceptEncoding == null )
   return 0;

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
 return (rv == null) ? 0 :
   ( rv.equals("gzip") ? ENC_GZIP :
     ( rv.equals("deflate") ? ENC_DEFLATE : 0 )
   );
}


/**
 *  Creates full absolute URI for specified path in current effective encoding
 *
 *  @param	sPath	path from which to create the full relative URI
 *
 *  @return	full absolute URI
 *
 */
protected String makeHRef (String sPath)
{
  return makeHRef(sPath, this.effCharset);
}

/**
 *  Creates full absolute URI for specified path in specified encoding
 *
 *  @param	sPath	path from which to create the full relative URI
 *  @param	enc	required encoding
 *
 *  @return	full absolute URI
 *
 */
protected String makeHRef (String sPath, String enc)
{
  StringBuffer sb = new StringBuffer(256);
  sb.append(this.sReqBase);
  if (!sPath.startsWith("/")) sb.append('/');
  int n = sPath.length();
  int i = 0;
  int j = 0;
  while (i < n) {
    j = sPath.indexOf('/', i);
    if (j < 0) break;
    if (j > i) sb.append( escape(sPath.substring(i, j), enc) );
    sb.append('/');
    i = j + 1;
  }
  if (i < n) sb.append( escape(sPath.substring(i), enc) );
  //
  i = sb.indexOf("+", 0);
  while (i >= 0) {
    sb.replace(i, i+1, "%20");
    i = sb.indexOf("+", i+3);
  }
  return sb.toString();
}

/**
 *  Creates full relative URI for specified path in current effective encoding
 *
 *  @param	sPath	path from which to create the full relative URI
 *
 *  @return	full relative URI
 *
 */
protected String makeRelHRef (String sPath, File file)
{
  return makeRelHRef(sPath, this.effCharset, file);
}

/**
 *  Creates full relative URI for specified path in specified encoding
 *
 *  @param	sPath	path from which to create the full relative URI
 *  @param	enc	required encoding
 *
 *  @return	full relative URI
 *
 */
protected String makeRelHRef (String sPath, String enc, File file)
{
  StringBuffer sb = new StringBuffer(256);
  sb.append(this.sReqRelBase);
  if (!sPath.startsWith("/")) sb.append('/');
  int n = sPath.length();
  int i = 0;
  int j = 0;
  while (i < n) {
    j = sPath.indexOf('/', i);
    if (j < 0) break;
    if (j > i) sb.append( escape(sPath.substring(i, j), enc) );
    sb.append('/');
    i = j + 1;
  }
  if (i < n) sb.append( escape(sPath.substring(i), enc) );
  //
  i = sb.indexOf("+", 0);
  while (i >= 0) {
    sb.replace(i, i+1, "%20");
    i = sb.indexOf("+", i+3);
  }
    String makePath = sb.toString();
  if(!makePath.endsWith("/")&&file.isDirectory())sb.append('/');
  return sb.toString();
}

protected boolean checkModifiedSince(File file)
{
  String sIMS   = this.request.getHeader("If-Modified-Since");
  if ( sIMS != null)
  {
    Date modsince;
    if (sIMS.indexOf(";") > 0)
      modsince = parseGMTDate(sIMS.substring(0, sIMS.indexOf(";")));
    else
      modsince = parseGMTDate(sIMS);

    Date filetime = new Date((file.lastModified()/1000)*1000);
    if ( modsince.after(filetime) || modsince.equals(filetime) )
    {
      respStatus(304, "Not modified");
      return true;
    }
  }
  return false;
}


protected boolean checkNotModifiedSince(File file)
{
  String sIMS   = this.request.getHeader("If-Unmodified-Since");
  if ( sIMS != null)
  {
    Date modsince;
    if (sIMS.indexOf(";") > 0)
      modsince = parseGMTDate(sIMS.substring(0, sIMS.indexOf(";")));
    else
      modsince = parseGMTDate(sIMS);

    Date filetime = new Date((file.lastModified()/1000)*1000);

    if ( filetime.after(modsince) )
    {
      respStatus(412, "Precondifion failed");
      return true;
    }
  }
  return false;
}



protected boolean checkIfMatch(File file)
{
  String sifm = this.request.getHeader("If-Match");
  if (sifm != null && !sifm.equals("*"))
  {
    String myetag = calcETag(file);
    String[] tags = sifm.split(",");
    for (int i = 0; i < tags.length ; i++)
      if (tags[i].startsWith("W/"))
        tags[i] = tags[i].substring(2);     // we will always do strong comparison
    for (int i = 0; i < tags.length ; i++)
    {
      if (myetag.equals(tags[i].trim()))
        return false;
    }
    respStatus(412, "Precondifion failed");
    return true;
  }
  return false;
}


/**
 *  Checks if If-Range value satisfies either of these conditions:<br/>
 *   - it is an HTTP-Date and is after or equals to the file modification time (that is,
 *     the file is unchanged);<br/>
 *   - it is a list of e-tags and one of them matches the file e-tag;<br/>
 *   - no If-Range header present or the header value is *<br/>
 *
 *  If either of these conditions are satisfied, the function returns true,
 *  otherwise it returns false.
 *
 */
protected boolean checkIfRange(File file)
{
  String sifr = this.request.getHeader("If-Range");
  if (sifr != null && !sifr.equals("*"))
  {

    Date modsince;
    if (sifr.indexOf(";") > 0)
      modsince = parseGMTDate(sifr.substring(0, sifr.indexOf(";")));
    else
      modsince = parseGMTDate(sifr);

    if ( modsince != null)
    {
      Date filetime = new Date((file.lastModified()/1000)*1000);
      return ( modsince.after(filetime) || modsince.equals(filetime) );
    }
    else
    {
      // If-Range probably contains e-tags
      String myetag = calcETag(file);
      String[] tags = sifr.split(",");
      for (int i = 0; i < tags.length ; i++)
        if (tags[i].startsWith("W/"))
          tags[i] = tags[i].substring(2);     // we will always do strong comparison
      for (int i = 0; i < tags.length ; i++)
      {
        if (myetag.equals(tags[i].trim()))
          return true;
      }
      return false;
    }
  }
  return true;
}

/**
 * Checks if none of supplied e-tags in If-None-Match header match
 * the e-tag of the entity.
 *
 * @param	file	resource to check against.
 *
 * @return	true if and only if If-None-Match header contains * special
 *		character or one of the supplied e-tags match the resource
 *		e-tag.
 */

protected boolean checkIfNoneMatch(File file)
{
  String sifnm = this.request.getHeader("If-None-Match");
  if (sifnm != null)
  {
    if (sifnm.equals("*"))
      return true;
    String myetag = calcETag(file);
    String[] tags = sifnm.split(",");
    for (int i = 0; i < tags.length ; i++)
      if (tags[i].startsWith("W/"))
        tags[i] = tags[i].substring(2);    // we sill always do strong comparison
    for (int i = 0; i < tags.length ; i++)
    {
      if (myetag.equals(tags[i].trim()))
        return true;
    }
  }
  return false;
}



protected boolean deleteRecursively(File file)
{
   if (!file.isDirectory())
     return file.delete();
   File[] dir = file.listFiles();
   boolean status = true;
   // recursively delete all files and directories under this directory
   for (int i = 0; i < dir.length; i++)
   {
     status &= deleteRecursively(dir[i]);
   }
   // delete the directory itself
   return status &= file.delete();
}

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.29 $";
} // class UDAV

//----------------------------------------------------------------------
