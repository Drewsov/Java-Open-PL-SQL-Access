//----------------------------------------------------------------------
// ConnectionPool.java
//----------------------------------------------------------------------
//
// Copyright (c) 2001 N-Networks, All rights reserved.
// Author: S.Ageshin
// Date:   [30.01.2003]
//
// Class:  ConnectionPool
//   Manages a global pool of JDBC database connections. Attempts to
//   reuse connections maintained in an internal storage, otherwise
//   creates new connections as necessary. The main methods clients
//   should use are getConnection, and releaseConnection.
//
//----------------------------------------------------------------------

package com.nnetworks.jopa;

import java.sql.*;
import java.util.*;

import oracle.jdbc.driver.*;

/**
 *  Manages a global pool of JDBC database connections. Attempts to
 *  reuse connections maintained in an internal storage, otherwise
 *  creates new connections as necessary. The main methods clients
 *  should use are {@link #getConnection getConnection}, and {@link #releaseConnection releaseConnection}.
 *
 *  @author	Sergey Ageshin
 *  @author	Vladimir M. Zakharychev
 *
 *  @version	$Revision: 1.4 $
 */
public class ConnectionPool
{
/**
 * Creates Oracle JDBC connect string given driver and database alias.
 * Valid driver values are <code>thin</code> (the default) and <code>oci8</code>.
 * Valid alias values are a NET8 V2 alias (only for OCI8) or a
 * thin driver connect string of the form <host>:<port>:<sid>
 * (if this format is passed for anything other than the thin driver,
 * this routine converts is to the long format).
 *
 * @param	sDriver	JDBC driver type
 * @param	sHost	Database host name/address
 * @param	sPort	Database port
 * @param	sSID	Oracle database SID
 *
 * @return	formatted JDBC connection string
 *
 */
public static String makeDatabaseString
( String sDriver
, String sHost
, String sPort
, String sSID
)
{
  sDriver = (sDriver == null) ? "thin" : sDriver.toLowerCase();
  StringBuffer sb = new StringBuffer(80);
  sb.append("jdbc:oracle:");
  sb.append(sDriver);
  sb.append(":@");
  if (sDriver.equals("thin")) {
    sb.append(sHost);
    sb.append(':');
    sb.append(sPort);
    sb.append(':');
    sb.append(sSID);
  }
  else {
    sb.append("(description=(address=(host=");
    sb.append(sHost);
    sb.append(")(protocol=tcp)(port=");
    sb.append(sPort);
    sb.append("))(connect_data=(sid=");
    sb.append(sSID);
    sb.append(")))");
  }
  return sb.toString();
}

/**
 * Creates a key string to label a connection.
 * Consists of the username + separator + database driver/connection string.
 * Uses a space as the separator, which seems safe enough since
 * normally spaces can't occur in the driver/connection string.
 * The username is first because it's more selective during searches.
 *
 * @param	sUsername	user name
 * @param	sDatabase	database connection string
 *
 * @return	connection key string
 */

private static String makeKey (String sUsername, String sDatabase)
{
  return (sUsername + ' ' + sDatabase);
}

//----------------------------------------------------------------------
// Instance variables; since there will be only one instance
// of this class, they're effectively global, too.

private int       iInit;
private int       iLimit;
private Hashtable hash;
private String    sOraDrv;

//----------------------------------------------------------------------
// Constructors:

public ConnectionPool
( int iInit0
, int iInit
, int iLimit
)
{
  this.iInit   = iInit;
  this.iLimit  = iLimit;
  this.hash    = new Hashtable(iInit0);
  this.sOraDrv = null;
}

/**
 * Attempts to find a matching connection in the pool;
 * if not found, adds one and returns it.
 *
 * @param	sUsername	database user name
 * @param	sPassword	database user's password
 * @param	sDatabase	database connection string
 *
 * @return	Database connection object
 *
 * @throws	SQLException	if the database connection could not be established
 */
public synchronized Connection getConnection
( String sUsername
, String sPassword
, String sDatabase
) throws SQLException
{
  Connection conn = null;

  // Key to be used:
  String sKey = makeKey(sUsername, sDatabase);

  // First, attempt to find a matching active connection
  Vector vec = (Vector) this.hash.get(sKey);
  if (vec != null) {
    while (vec.size() > 0) {
      conn = (Connection) vec.firstElement();
      vec.removeElementAt(0);
      if (!conn.isClosed()) break;
      conn = null;
    }
  }

  // If not found: create new connection entry:
  if (conn == null) {
    conn = newConnection(sUsername, sPassword, sDatabase);
    ((OracleConnection)conn).setClientData("Key", sKey);
    ((OracleConnection)conn).setClientData("Stat", "New");
  }

  return conn;
}

/**
 * Creates a new connection.
 * @param	sUsername	database user name
 * @param	sPassword	database user's password
 * @param	sDatabase	database connection string
 *
 * @return	Database connection object
 *
 * @throws	SQLException	if the database connection could not be established
 */
public Connection newConnection
( String sUsername
, String sPassword
, String sDatabase
) throws SQLException
{
  if (this.sOraDrv == null) {
    this.sOraDrv = "oracle.jdbc.driver.OracleDriver";
    try { Class.forName(this.sOraDrv); }
    catch(ClassNotFoundException e) {
      SQLException esql = new SQLException(this.sOraDrv + ": " + e.getMessage());
      this.sOraDrv = null;
      throw(esql);
    }
  }
  return DriverManager.getConnection(sDatabase, sUsername, sPassword);
}

/**
 * Releases a connection to the pool.
 * If the pool is over the maximum, the oldest connection
 * is removed and closed.
 *
 * @param	conn	database connection to return to the pool.
 */
public synchronized void releaseConnection (Connection conn)
{
  try {if (conn!=null||!conn.isClosed())conn.close(); return; } 
  catch (SQLException e) {}
  catch (NullPointerException e) {}
  try {conn.close(); return;} 
  catch (SQLException e) {}
  catch (NullPointerException e) {}
  /* Drew
  if (conn == null) return;
  try { if (conn.isClosed()) return; } catch(SQLException e) {}
  */
  try {
  String sKey = (String) ((OracleConnection)conn).getClientData("Key");
  if (sKey == null) {
    try { conn.close(); } catch(SQLException e) {}
    return;
  }

 ((OracleConnection)conn).setClientData("Stat", "Reused");

  Vector vec = (Vector) this.hash.get(sKey);
  if (vec == null) {
    vec = new Vector(this.iInit);
    this.hash.put(sKey, vec);
  }
  else if (vec.size() >= this.iLimit) {
    Connection conn0 = (Connection) vec.elementAt(0);
    vec.removeElementAt(0);
    try { if (conn0!=null||!conn0.isClosed()) conn0.close(); } catch(SQLException e) {}
    conn0 = null;
  }
  vec.addElement(conn);
  }
    catch (NullPointerException e) {return;}
}

/**
 * Remove all connections from the pool that aren't currently in use
 */
public synchronized void clearPool ()
{
  Enumeration en = this.hash.elements();
  while (en.hasMoreElements()) {
    Vector vec = (Vector) en.nextElement();
    for (int i = 0; i < vec.size(); i++) {
      Connection conn = (Connection) vec.elementAt(i);
      try { if (conn!=null||!conn.isClosed()) conn.close(); } catch(SQLException e) {}
    }
    vec.removeAllElements();
  }
  this.hash.clear();
}

/**
 * Counts connections in the pool.
 *
 * @return	number of connections currently in the pool
 */
public synchronized int countTotal ()
{
  int n = 0;
  Enumeration en = this.hash.elements();
  while (en.hasMoreElements()) {
    Vector vec = (Vector) en.nextElement();
    n += vec.size();
  }
  return n;
}

/**
 * Close the pool by setting the maximum to zero and removing
 * any unused connections; as the remaining connections are released,
 * they will be closed because the maximum is by definition exceeded.
 */
public void done ()
{
  clearPool();
}

/**
 * Sets the maximum pool size; size must be at least 0
 *
 * @param 	iLimit	maximum number of connections in the pool
 *
 */
public synchronized void setLimit (int iLimit)
{
  if (iLimit >= 0) {
    this.iLimit = iLimit;
  }
}

/**
 * Returns the current maximum pool size
 *
 * @return	maximum connections that can be pooled
 */
public synchronized int getLimit ()
{
  return (iLimit);
}

private static String fileRevision = "$Revision: 1.4 $";
} // class ConnectionPool
