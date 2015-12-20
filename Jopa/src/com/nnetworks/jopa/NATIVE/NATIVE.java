//----------------------------------------------------------------------
// NATIVE.java
//
// Native Dynamic PSP access
//
// $Id: NATIVE.java,v 1.17 2005/02/13 10:13:48 Bob Exp $
//----------------------------------------------------------------------

package com.nnetworks.jopa.NATIVE;

import com.nnetworks.jopa.*;

import javax.servlet.*;
import javax.servlet.http.*;

import oracle.sql.*;
import oracle.jdbc.driver.*;

import java.sql.*;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.zip.*;

//----------------------------------------------------------------------

class NATIVE extends Processor_PSP
implements Runnable
{

//----------------------------------------------------------------------

protected String  sPipename;

//----------------------------------------------------------------------
// This works in a separate thread:
//----------------------------------------------------------------------

public void run ()
{
  shell.log(3, "Polling progressive output...");

  Thread myself = Thread.currentThread();

  // Obtain another connection:
  Connection conn2 = establishConnection2();
  if (conn2 == null) return;

  try {
    OracleCallableStatement cs;

    // Read header lines:
    try {
      String s, sName, sValue;
      int j;
      cs =
        (OracleCallableStatement)conn2.prepareCall(
           "begin NN$PSP_RSP.getProgressiveHeader(?,?); end;");
      cs.setString(1, this.sPipename);
      cs.registerOutParameter(2, OracleTypes.VARCHAR);
      while (pollingThread == myself) {
        cs.execute();
        s = cs.getString(2);    if (cs.wasNull()) break;
        if (s.length() == 0) break;
        j = s.indexOf(':', 0);
        if (j > 0) 
        {
          sName  = s.substring(0,j).trim();
          sValue = s.substring(j+1).trim();
          if( sName.equalsIgnoreCase("Content-Type") )
            // allow for charset override and conversion
            respContentType(sValue);
          else 
            respOWAHeader(sName, sValue);
        }
      }
      cs.close();
    }
    catch (SQLException e) {
      shell.log(0, "getProgressiveHeader/SQL: ", e.getMessage());
      e.printStackTrace(shell.getLogStream(0));
    }

    // just die if the parent process wants us to
    if (pollingThread != myself)
    {
      shell.log(4, "polling thread commiting suicide...");
      return;
    }

    // just die if the parent process wants us to
    if (pollingThread != myself)
    {
      shell.log(4, "polling thread commiting suicide...");
      return;
    }

    // Read content chunk-by-chunk:
    OutputStream sos = null;
    try {
      RAW  chunk;
      long chunkLength;

      switch(getDesiredContentEncoding())
      {
        case ENC_GZIP:
          respHeader("Content-Encoding","gzip");
          sos = new GZIPOutputStream(this.response.getOutputStream());
          shell.log(3,"Applying GZIP content encoding to the output...");
          break;
        case ENC_DEFLATE:
          respHeader("Content-Encoding","deflate");
          sos = new DeflaterOutputStream(this.response.getOutputStream());
          shell.log(3,"Applying Deflate content encoding to the output...");
          break;
        default:
        sos = this.response.getOutputStream();
      }

      if (pollingThread != myself)
      {
        shell.log(4, "polling thread commiting suicide...");
        return;   
      }

      sos.flush(); // send the headers - this triggers chunked encoding automatically if
                   // content length is not set (and it will not be with compression)

      cs =
        (OracleCallableStatement)conn2.prepareCall(
         "begin NN$PSP_RSP.getProgressiveContent(?,?); end;");
      cs.setString(1, this.sPipename);
      cs.registerOutParameter(2, OracleTypes.RAW);
      while (pollingThread == myself)
      // poll for progressive output in a loop. Not really the most efficient method of
      // getting the output, but we don't have other option but put up a TCP listener
      // here and send data through UTL_TCP from Oracle, in which case we will not constantly
      // re-execute the polling procedure and will be in fact called back when there's something
      // ready for us. Another option could be AQ, but it's a bit cumbersome to implement and
      // is a separately licensed Oracle option.
      {
        cs.execute();
        chunk = cs.getRAW(2);   if (cs.wasNull()) break;
        if (chunk.getLength() <= 0) 
          break;
        sos.write(chunk.getBytes()); // send the chuck itself
        // flush pending output immediately - we're being chatty, but very responsive.
        sos.flush(); 
      }
      if (pollingThread != myself)
      {
        shell.log(4, "polling thread commiting suicide...");
        return;
      }
      cs.close();
    }
    catch (SQLException e) {
      shell.log(0, "getProgressiveContent/SQL:com.nnetworks.jopa.NATIVE", e.getMessage());
      e.printStackTrace(shell.getLogStream(0)); 
      try {sos.close();}  catch(IOException  e2) {}
      try {conn2.close();} catch(SQLException e2) {}       
    }
    catch(IOException e1) {
      shell.log(0, "getProgressiveContent/IO:com.nnetworks.jopa.NATIVE", e1.getMessage());
      e1.printStackTrace(shell.getLogStream(0));
      try {sos.close();}  catch(IOException  e2) {}
      try {conn2.wait(1000); conn2.close();} 
      catch(InterruptedException e2) {}
      catch(SQLException e2) {}
    }
    finally 
    {
      try { if (sos != null) { sos.flush(); sos.close(); } } catch(IOException e2) {}
    }

  }
  finally 
  {
    // In any case, the connection MUST be returned to the pool:
    shell.getConnectionPool().releaseConnection(conn2);
    shell.log(4, "polling thread is done.");
  }
}

//----------------------------------------------------------------------
// End of the separated thread.
//----------------------------------------------------------------------

private void goProgressive ()
throws ServletException, IOException, JOPAException
{
  // Set Progressive-Output parameters:
  this.sPipename = preparePipe();
  shell.log(3, "go progressive: pipename=", this.sPipename);

  // Start another thread for polling:
  pollingThread = new Thread(this);
  pollingThread.start();

  // Request DPSP2 to go progressive...
  String stmt =
    "begin NN$PSP_RSP.goProgressive(?,?,?); end;";
  try {
    ArrayDescriptor adArray = getArrayDescriptor();

    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(stmt);
    cs.setString(1, this.sPipename);
    cs.setArray(2, new ARRAY(adArray, m_conn, m_vecParName.toArray()));
    cs.setArray(3, new ARRAY(adArray, m_conn, m_vecParData.toArray()));
    cs.execute();
    cs.close();
  }
  catch(SQLException e) {
    e.printStackTrace(shell.getLogStream(0));
    // the polling thread should die
    pollingThread = null;
    try { m_conn.clearWarnings(); } catch (SQLException e1) {}
    throw (new JOPAException("goProgressive", e.getMessage()));
  }

  // Wait until the polling thread stops:
  try { pollingThread.join(300000); }
  catch (InterruptedException ie) {}
  shell.log(3, "done progressive.");
}

//----------------------------------------------------------------------

private String preparePipe ()
{
  return Long.toHexString(System.currentTimeMillis()) +
         Thread.currentThread().getName().substring(4);
}

//----------------------------------------------------------------------

private void go()
throws ServletException, IOException, JOPAException
{
  String stmt =
    "begin NN$PSP_RSP.go(?,?); NN$PSP_RSP.getPage(?,?); end;";
  try {
    ArrayDescriptor adArray = getArrayDescriptor();

    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(stmt);

    cs.registerOutParameter(3, OracleTypes.VARCHAR);
    cs.registerOutParameter(4, OracleTypes.BLOB);

    cs.setArray(1, new ARRAY(adArray, m_conn, m_vecParName.toArray()));
    cs.setArray(2, new ARRAY(adArray, m_conn, m_vecParData.toArray()));

    cs.execute();

    respHeaders(cs.getString(3));
    respContent(cs.getBLOB(4));

    cs.close();
  }
  catch(SQLException e) {
    e.printStackTrace(shell.getLogStream(0));
    try { m_conn.clearWarnings(); } catch (SQLException e1) {}
    throw (new JOPAException("go", e.getMessage()));
  }
}

//----------------------------------------------------------------------

protected void doProc()
throws ServletException, IOException, JOPAException
{
  String s = this.props.getProperty("progressive_output", "0");
  if ( s.equals("0") ) 
    go();
  else
    goProgressive();
}

//----------------------------------------------------------------------

protected void doDownload()
throws ServletException, IOException, JOPAException
{
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
    e.printStackTrace(shell.getLogStream(0));
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    throw new JOPAException("doDownload", e.getMessage() + e + stmt);
  }
}

protected void setEnvRequestCharsets()
{
  String oracs = getOracleCharsetName(this.effCharset);
  if ( !oracs.equalsIgnoreCase(this.effCharset) )
  {
    appEnv("REQUEST_CHARSET",      oracs);
    appEnv("REQUEST_IANA_CHARSET", this.effCharset);
  }
  else
    super.setEnvRequestCharsets();
}

private volatile Thread pollingThread;

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.17 $";
} // class NATIVE

//----------------------------------------------------------------------
