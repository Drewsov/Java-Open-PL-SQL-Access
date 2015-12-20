//----------------------------------------------------------------------
// DPSPDAV.DAVNode.java
//----------------------------------------------------------------------

package com.nnetworks.jopa.DPSPDAV;

import com.nnetworks.jopa.*;

import oracle.sql.*;
import oracle.jdbc.driver.*;

import java.sql.*;
import java.io.*;
import java.util.*;

//----------------------------------------------------------------------

class DAVNode
{

//----------------------------------------------------------------------

public NUMBER   nEnt;         //  1
public NUMBER   nDir;         //  2
public String   sName;        //  3
public String   sKind;        //  4
public DATE     dIns;         //  5
public DATE     dUpd;         //  6
public String   sETag;        //  7
public int      iAccl;        //  8
public NUMBER   nSize;        //  9
public String   sMIMEType;    // 10
public String   sCharset;     // 11
public DATE     dt;           // 12
public String   sPath;        // 13

//----------------------------------------------------------------------

public static void registerOutParameters (OracleCallableStatement cs, int n)
throws java.sql.SQLException
{
  cs.registerOutParameter( 1+n, OracleTypes.NUMBER);
  cs.registerOutParameter( 2+n, OracleTypes.NUMBER);
  cs.registerOutParameter( 3+n, OracleTypes.VARCHAR);
  cs.registerOutParameter( 4+n, OracleTypes.VARCHAR);
  cs.registerOutParameter( 5+n, OracleTypes.DATE);
  cs.registerOutParameter( 6+n, OracleTypes.DATE);
  cs.registerOutParameter( 7+n, OracleTypes.VARCHAR);
  cs.registerOutParameter( 8+n, OracleTypes.NUMBER);
  cs.registerOutParameter( 9+n, OracleTypes.NUMBER);
  cs.registerOutParameter(10+n, OracleTypes.VARCHAR);
  cs.registerOutParameter(11+n, OracleTypes.VARCHAR);
  cs.registerOutParameter(12+n, OracleTypes.DATE);
  cs.registerOutParameter(13+n, OracleTypes.VARCHAR);
}

//----------------------------------------------------------------------

public boolean materialize (OracleCallableStatement cs, int n)
{
  try {
    this.nEnt  = cs.getNUMBER(1+n);
    this.nDir  = cs.getNUMBER(2+n);      if (cs.wasNull()) this.nDir = null;
    this.sName = cs.getString(3+n);
    this.sKind = cs.getString(4+n);      if (cs.wasNull()) this.sKind = "NUL";
    this.dIns  = cs.getDATE(5+n);        if (cs.wasNull()) this.dIns = null;
    this.dUpd  = cs.getDATE(6+n);        if (cs.wasNull()) this.dUpd = null;
    this.sETag = cs.getString(7+n);      if (cs.wasNull()) this.sETag = null;
    this.iAccl = cs.getInt(8+n);         if (cs.wasNull()) this.iAccl = 0;
    this.nSize = cs.getNUMBER(9+n);      if (cs.wasNull()) this.nSize = new NUMBER(0);
    this.sMIMEType = cs.getString(10+n); if (cs.wasNull()) this.sMIMEType = null;
    this.sCharset  = cs.getString(11+n); if (cs.wasNull()) this.sCharset = null;
    this.dt    = cs.getDATE(12+n);       if (cs.wasNull()) this.dt = null;
    this.sPath = cs.getString(13+n);     if (cs.wasNull()) this.sPath = null;
    return true;
  }
  catch (SQLException e) {}
  return false;
}

//----------------------------------------------------------------------

public boolean materialize (OracleResultSet rs, int n)
{
  try {
    this.nEnt  = rs.getNUMBER(1+n);
    this.nDir  = rs.getNUMBER(2+n);      if (rs.wasNull()) this.nDir = null;
    this.sName = rs.getString(3+n);
    this.sKind = rs.getString(4+n);      if (rs.wasNull()) this.sKind = "NUL";
    this.dIns  = rs.getDATE(5+n);        if (rs.wasNull()) this.dIns = null;
    this.dUpd  = rs.getDATE(6+n);        if (rs.wasNull()) this.dUpd = null;
    this.sETag = rs.getString(7+n);      if (rs.wasNull()) this.sETag = null;
    this.iAccl = rs.getInt(8+n);         if (rs.wasNull()) this.iAccl = 0;
    this.nSize = rs.getNUMBER(9+n);      if (rs.wasNull()) this.nSize = new NUMBER(0);
    this.sMIMEType = rs.getString(10+n); if (rs.wasNull()) this.sMIMEType = null;
    this.sCharset  = rs.getString(11+n); if (rs.wasNull()) this.sCharset = null;
    this.dt    = rs.getDATE(12+n);       if (rs.wasNull()) this.dt = null;
    this.sPath = rs.getString(13+n);     if (rs.wasNull()) this.sPath = null;
    return true;
  }
  catch (SQLException e) {}
  return false;
}

//----------------------------------------------------------------------

public boolean materialize (OracleResultSet rs)
{
  return this.materialize(rs, 0);
}

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.2 $";
} // class DAVNode

//----------------------------------------------------------------------
