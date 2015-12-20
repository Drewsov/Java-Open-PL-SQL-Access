//----------------------------------------------------------------------
// PERSES.Probe.java
//----------------------------------------------------------------------

package com.nnetworks.jopa.PERSES;

import com.nnetworks.jopa.PERSES.*;
import com.nnetworks.jopa.*;

import oracle.sql.*;
import oracle.jdbc.driver.*;

import java.sql.*;

//----------------------------------------------------------------------

class Probe
{
  public int     iLayer;
  public boolean bCollection;
  public String  sHRef;
  public String  sSchema;
  public String  sParentName;
  public String  sName;
  public int     iObjtype;
  //
  public NUMBER  nObjID;
  public String  sStatus;
  public DATE    dCre;
  public DATE    dUpd;
}

//----------------------------------------------------------------------
