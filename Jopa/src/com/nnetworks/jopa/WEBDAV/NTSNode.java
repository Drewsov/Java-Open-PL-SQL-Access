//----------------------------------------------------------------------
// WEBDAV.NTSNode.java
//
// $Id: NTSNode.java,v 1.3 2005/01/13 11:39:13 Bob Exp $
//
//----------------------------------------------------------------------

package com.nnetworks.jopa.WEBDAV;

import com.nnetworks.jopa.*;

import oracle.sql.*;
import oracle.jdbc.driver.*;

import java.sql.*;
import java.io.*;
import java.text.*;
import java.util.*;

//----------------------------------------------------------------------
// ( 1:ID, 2:ID_FOLDER, 3:PATH, 4:NAME, 5:ACCL,
//   6:DISP, 7:ID_REF, 8:REF_SIZE, 9:DATA, 10:TIME_STAMP )

class NTSNode
{

//----------------------------------------------------------------------

public NUMBER   nKey;
public NUMBER   nFolder;
public String   sPath;
public String   sName;
public int      iAccl;
public String   sDisp;
public NUMBER   nRef;
public NUMBER   nSize;
public String   sData;
public DATE     dStamp;

//----------------------------------------------------------------------

private static final int
  RCUR_ID         = 1,
  RCUR_ID_FOLDER  = 2,
  RCUR_PATH       = 3,
  RCUR_NAME       = 4,
  RCUR_ACCL       = 5,
  RCUR_DISP       = 6,
  RCUR_ID_REF     = 7,
  RCUR_REF_SIZE   = 8,
  RCUR_DATA       = 9,
  RCUR_TIME_STAMP = 10;

//----------------------------------------------------------------------

private static String nts_stmt
  = "declare r NN$NTS.tNode; \n"
  + "begin NN$NTS.getNode(r, ?); \n"
  + " ? := r.ID_FOLDER; "
  + " ? := r.PATH; "
  + " ? := r.NAME; "
  + " ? := r.ACCL; "
  + " ? := r.DISP; "
  + " ? := r.ID_REF; "
  + " ? := r.REF_SIZE; "
  + " ? := r.DATA; "
  + " ? := r.TIME_STAMP; "
  + "end;";

//----------------------------------------------------------------------

public boolean materialize (Connection conn, NUMBER nKey)
throws JOPAException
{
  this.nKey = nKey;
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)conn.prepareCall(nts_stmt);
    cs.registerOutParameter( 2, OracleTypes.NUMBER);
    cs.registerOutParameter( 3, OracleTypes.VARCHAR);
    cs.registerOutParameter( 4, OracleTypes.VARCHAR);
    cs.registerOutParameter( 5, OracleTypes.NUMBER);
    cs.registerOutParameter( 6, OracleTypes.VARCHAR);
    cs.registerOutParameter( 7, OracleTypes.NUMBER);
    cs.registerOutParameter( 8, OracleTypes.NUMBER);
    cs.registerOutParameter( 9, OracleTypes.VARCHAR);
    cs.registerOutParameter(10, OracleTypes.DATE);
    cs.setNUMBER(1, nKey);
    cs.execute();
    this.nFolder = cs.getNUMBER(2);  if (cs.wasNull()) this.nFolder = null;
    this.sPath   = cs.getString(3);  if (cs.wasNull()) this.sPath   = null;
    this.sName   = cs.getString(4);  if (cs.wasNull()) this.sName   = null;
    this.iAccl   = cs.getInt(5);     if (cs.wasNull()) this.iAccl   = 0;
    this.sDisp   = cs.getString(6);  if (cs.wasNull()) this.sDisp   = "NUL";
    this.nRef    = cs.getNUMBER(7);  if (cs.wasNull()) this.nRef    = null;
    this.nSize   = cs.getNUMBER(8);  if (cs.wasNull()) this.nSize   = new NUMBER(0);
    this.sData   = cs.getString(9);  if (cs.wasNull()) this.sData   = null;
    this.dStamp  = cs.getDATE(10);   if (cs.wasNull()) this.dStamp  = null;
    cs.close();
    return true;
  }
  catch(SQLException e) {
    try { conn.clearWarnings(); } catch(SQLException e1) {}
  }
  return false;
}

//----------------------------------------------------------------------

public boolean materialize (OracleResultSet rs)
{
  try {
    this.nKey    = rs.getNUMBER(RCUR_ID);
    this.nFolder = rs.getNUMBER(RCUR_ID_FOLDER);
    this.sPath   = rs.getString(RCUR_PATH);
    this.sName   = rs.getString(RCUR_NAME);
    this.iAccl   = rs.getInt(RCUR_ACCL);
    this.sDisp   = rs.getString(RCUR_DISP);
    this.nRef    = rs.getNUMBER(RCUR_ID_REF);   if (rs.wasNull()) this.nRef = null;
    this.nSize = rs.getNUMBER(RCUR_REF_SIZE);   if (rs.wasNull()) this.nSize = new NUMBER(0);
    this.sData  = rs.getString(RCUR_DATA);
    this.dStamp = rs.getDATE(RCUR_TIME_STAMP);
    return true;
  }
  catch (SQLException e) {}
  return false;
}

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.3 $";
} // class NTSNode

//----------------------------------------------------------------------
