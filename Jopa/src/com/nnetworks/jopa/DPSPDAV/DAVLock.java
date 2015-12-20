//----------------------------------------------------------------------
// DPSPDAV.DAVLock.java
//----------------------------------------------------------------------

package com.nnetworks.jopa.DPSPDAV;

import com.nnetworks.jopa.*;

import oracle.sql.*;
import oracle.jdbc.driver.*;

import java.sql.*;
import java.io.*;
import java.util.*;

//----------------------------------------------------------------------

class DAVLock
{

//----------------------------------------------------------------------

public String   sToken;      // 1
public String   sOwner;      // 2
public NUMBER   nTimeout;    // 3
public String   sDepth;      // 4
public String   sScope;      // 5
public String   sType;       // 6

//----------------------------------------------------------------------

public static void registerOutParameters (OracleCallableStatement cs, int n)
throws java.sql.SQLException
{
  cs.registerOutParameter(1+n, OracleTypes.VARCHAR);
  cs.registerOutParameter(2+n, OracleTypes.VARCHAR);
  cs.registerOutParameter(3+n, OracleTypes.NUMBER);
  cs.registerOutParameter(4+n, OracleTypes.VARCHAR);
  cs.registerOutParameter(5+n, OracleTypes.VARCHAR);
  cs.registerOutParameter(6+n, OracleTypes.VARCHAR);
}

//----------------------------------------------------------------------

public boolean materialize (OracleCallableStatement cs, int n)
{
  try {
    this.sToken   = cs.getString(1+n);   if (cs.wasNull()) this.sToken = null;
    this.sOwner   = cs.getString(2+n);   if (cs.wasNull()) this.sOwner = null;
    this.nTimeout = cs.getNUMBER(3+n);   if (cs.wasNull()) this.nTimeout = null;
    this.sDepth   = cs.getString(4+n);   if (cs.wasNull()) this.sDepth = null;
    this.sScope   = cs.getString(5+n);   if (cs.wasNull()) this.sScope = null;
    this.sType    = cs.getString(6+n);   if (cs.wasNull()) this.sType = null;
    return true;
  }
  catch (SQLException e) {}
  return false;
}

//----------------------------------------------------------------------

public boolean materialize (OracleResultSet rs)
{
  try {
    this.sToken   = rs.getString("LOCK_TOKEN");  if (rs.wasNull()) this.sToken = null;
    this.sOwner   = rs.getString("OWNER");       if (rs.wasNull()) this.sOwner = null;
    this.nTimeout = rs.getNUMBER("TIMEOUT");     if (rs.wasNull()) this.nTimeout = null;
    this.sDepth   = rs.getString("LOCK_DEPTH");  if (rs.wasNull()) this.sDepth = null;
    this.sScope   = rs.getString("LOCK_SCOPE");  if (rs.wasNull()) this.sScope = null;
    this.sType    = rs.getString("LOCK_TYPE");   if (rs.wasNull()) this.sType = null;
    return true;
  }
  catch (SQLException e) {}
  return false;
}

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.2 $";
} // class DAVLock

//----------------------------------------------------------------------
