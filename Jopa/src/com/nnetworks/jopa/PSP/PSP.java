//----------------------------------------------------------------------
// PSP.java
//
// $Id: PSP.java,v 1.81 2007/01/01 10:08:16 Drew Exp $
//
//----------------------------------------------------------------------

package com.nnetworks.jopa.PSP;

import com.nnetworks.jopa.*;
import com.nnetworks.jopa.ViewFile;

import javax.servlet.*;

import oracle.sql.*;
import oracle.jdbc.driver.*;

import java.sql.*;
import java.io.*;
import java.util.*;

//----------------------------------------------------------------------

class PSP extends Processor_PSP
{

 private static final String DEF_EXCLUSION_LIST = "SYS\\..*,DBMS_.*,UTL_.*,OWA_UTIL\\.SHOWSOURCE,OWA_UTIL\\.CELLSPRINT";
 static final String FORBIDDEN_MSG = "<HTML><HEAD><TITLE>403 - Access denied.</TITLE></HEAD><BODY><H1>403 - Forbidden</H1><BR>You are not allowed to access {0} on this server.</BODY>";
 protected String           _PAGES = "/_pages";
 // caching statements for repeated use - we want to reduce parsing as much as possible
 private OracleCallableStatement m_getArgTypeCS = null;
 private OracleCallableStatement m_getArgNameCS = null;
 private OracleCallableStatement m_getOvcCS = null;
 // describe structure
 private Vector                  m_overloads = null;
 private boolean                 m_Described = false;
 private boolean                 isFileExists = false;
 private   String           sendEnv    = "sendEnvOWA";
 protected String           m_localbase;
 protected String           sReqURI;
 protected String           sReqPath;
 protected String           sReqName;
 protected String           sReqRelBase;
 protected String           sReqBase;
 protected String           sReqDir;
  /**
  *  ArgSignature - PL/SQL procedure argument signature, used in {@link describeProc}
  */
 private class ArgSignature {
   String m_name;
   String m_type;
   String m_datatype;

   ArgSignature(String name, String type, String datatype)
   {
     this.m_name = name;
     this.m_type = type;
     this.m_datatype = datatype == null ? type : datatype;
   }

   public String getName() { return this.m_name; }
   public String getType() { return this.m_type; }
   public String getDataType() { return this.m_datatype; }
   public String toString() { return this.m_name+" "+this.m_type+" ("+this.m_datatype+")";}

 }

//----------------------------------------------------------------------

protected void prepareEnv()
{
  super.prepareEnv();
  appEnv("GATEWAY_IVERSION", "3");
}

//----------------------------------------------------------------------
 /**
  * Initializes certain request URIs.
  */
 protected void reqURI ()
 {
    StringBuffer sb = new StringBuffer(256);
    sb.append("http://");
    sb.append(this.request.getHeader("Host"));
//    int i = this.request.getRequestURI().indexOf(this.request.getServletPath());
    int i = this.request.getRequestURI().indexOf(Processor.getServletPath);
    if (i > 0)
      sb.append(this.request.getRequestURI().substring(0,i+this.request.getServletPath().length()));
    else
      sb.append(this.request.getServletPath());
    sb.append('/');
    sb.append(this.pi.getItem(0));
   this.sReqURI = this.request.getRequestURI();
   this.sReqRelBase = sb.toString();
   this.sReqBase = this.sReqRelBase;
   this.sReqPath = this.pi.merge(1, -1);
   this.sReqDir  = this.pi.merge(1,this.pi.iLast);
   this.sReqName = this.pi.getItem(this.pi.iLast);

   shell.log(4,"Request URI:"+this.sReqURI);
   shell.log(4,"Request Dir:"+this.sReqDir);
   shell.log(4,"Request Base: "+this.sReqBase);
   shell.log(4,"Request Relative Base: "+this.sReqRelBase);
   shell.log(4,"Request Path: "+this.sReqPath);
   shell.log(4,"Request Name: "+this.sReqName);
 }

protected void sendEnv()
throws JOPAException
{
  shell.log(3,"sendEnvOWA passed.");
  if (m_vecEnvName.size() == 0) return;
  String stmt = shell.getCode(sendEnv, "{A}", ARRAY_TYPE);
  try {
    ArrayDescriptor adArray = getArrayDescriptor();

    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(stmt);

    cs.setArray(1, new ARRAY(adArray, m_conn, m_vecEnvName.toArray()));
    cs.setArray(2, new ARRAY(adArray, m_conn, m_vecEnvData.toArray()));

    cs.execute();
    cs.close();
    shell.log(3,"sendEnv passed:\n"+stmt+"\n");
  }
  catch(SQLException e) {
    shell.log(0, "sendEnv/SQL: ", e.getMessage());
    PrintStream ps = shell.getLogStream(0);
    if (ps != null)
      e.printStackTrace(ps);
  }
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
  if (isFileExists){
   m_vecParName.addElement("ln");
   m_vecParData.addElement(sReqName);
   doProcFlex2("go",1);}
  else {
      if (this.pi.cMode == '!') {
       if ((this.props.getProperty("flex","2")).equals("4"))
         doProcFlex4(this.pi.getItem(-1), 1);
       else
         doProcFlex2(this.pi.getItem(-1), 1);
      }
      else {
         doProcExpl(this.pi.getItem(-1));
      }
  } 
  getPage();
}
//----------------------------------------------------------------------

private String[] getExclusionList()
// returns list of excluded packages and procedures
{
  String s = this.props.getProperty("exclusion_list");
  shell.log(3,"Exclusion list as read from config: "+(s == null ? "null" : "\""+s+"\""));
  if (s == null)
    s = DEF_EXCLUSION_LIST;
  else
  {
    if ( s.equals("#NONE#") )
      return null;
    if ( s.length() > 0 )
      s += "," + DEF_EXCLUSION_LIST;
  }
  shell.log(3, "Final exclusion list is \""+s+"\"");
  return s.toUpperCase().split(",");
}

//----------------------------------------------------------------------
protected void checkExclusions(String sProc)
throws JOPAException
{
  if (sProc == null)
    return;
  String l_sProc = sProc.toUpperCase();
  String[] exlist = getExclusionList();
  if ( exlist == null )
    // no exclusions here
    return;
  for (int i = 0; i < exlist.length; i++)
  {
    String pat = exlist[i].trim();
    shell.log(3, "Matching "+l_sProc+" against /"+pat+"/");
    if ( l_sProc.matches(pat) )
     {
       shell.log(3,"MATCH, denying access.");
       throw new JOPAException("Forbidden");
     }
  }
  shell.log(3,"Exclusion list passed.");
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

private void doProcFlex2 (String sProc, int iOver)
throws ServletException, IOException, JOPAException
{
  shell.log(3,"doProcFlex2 passed.");        
  String[] sTyp1 = getArgType(sProc, iOver+1, 1, "NN$TP.VARCHAR_ARRAY").split(":");
  String[] sTyp2 = getArgType(sProc, iOver+1, 2, "NN$TP.VARCHAR_ARRAY").split(":");
  StringBuffer stmt = shell.getCodeBuffer("doProcOWAFlex2");
  //shell.substCode(stmt, "{F1}", sTyp1[0].replace("PUBLIC.",""));
  //shell.substCode(stmt, "{F2}", sTyp2[0].replace("PUBLIC.",""));
  shell.substCode(stmt, "{F1}", replace(sTyp1[0],"PUBLIC.",""));
  shell.substCode(stmt, "{F2}", replace(sTyp2[0],"PUBLIC.",""));
  //shell.substCode(stmt, "{F1}", sTyp1[0]);
  //shell.substCode(stmt, "{F2}", sTyp2[0]);
  shell.substCode(stmt, "{P}",  sProc);
  shell.substCode(stmt, "{A}",  ARRAY_TYPE);
  shell.log(3,"doProcFlex2 statement:\n"+stmt);
  try {
    ArrayDescriptor adArray = getArrayDescriptor();
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(stmt.toString());
    cs.setArray(1, new ARRAY(adArray, m_conn, m_vecParName.toArray()));
    cs.setArray(2, new ARRAY(adArray, m_conn, m_vecParData.toArray()));
    cs.execute();
    cs.close();
  }
  catch(SQLException e) { 
       
    //  byte[] data = e.getMessage().getBytes("Cp1251");
    //   String mess = new String(data,"Cp866");
      String mess = e.getMessage();
      shell.log(3,"\n doProcFlex2 error:\n"+mess);
    try 
    { 
       m_conn.clearWarnings(); 
    } 
    catch(SQLException e1) {}
    throw (new JOPAException("\n doProcFlex2",stmt+"\n"+mess));
  }
}

//----------------------------------------------------------------------

private void doProcFlex4 (String sProc, int iOver)
throws ServletException, IOException, JOPAException
{
  shell.log(3,"doProcFlex4 passed.");        
  String[] sTyp1 = getArgType(sProc, iOver+1, 2, "NN$TP.VARCHAR_ARRAY").split(":");
  String[] sTyp2 = getArgType(sProc, iOver+1, 3, "NN$TP.VARCHAR_ARRAY").split(":");
  StringBuffer stmt = shell.getCodeBuffer("doProcOWAFlex4");
  //shell.substCode(stmt, "{F1}", sTyp1[0].replace("PUBLIC.",""));
  //shell.substCode(stmt, "{F2}", sTyp2[0].replace("PUBLIC.",""));
  shell.substCode(stmt, "{F1}", replace(sTyp1[0],"PUBLIC.",""));
  shell.substCode(stmt, "{F2}", replace(sTyp2[0],"PUBLIC.",""));
//  shell.substCode(stmt, "{F1}", sTyp1[0]);
//  shell.substCode(stmt, "{F2}", sTyp2[0]);
  shell.substCode(stmt, "{P}",  sProc);
  shell.substCode(stmt, "{A}",  ARRAY_TYPE);
  try {
    ArrayDescriptor adArray = getArrayDescriptor();

    shell.log(3,"doProcFlex4 statement:\n"+stmt);
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(stmt.toString());
    cs.setArray(1, new ARRAY(adArray, m_conn, m_vecParName.toArray()));
    cs.setArray(2, new ARRAY(adArray, m_conn, m_vecParData.toArray()));
    cs.execute();
    cs.close();
  }
  catch (SQLException e) {
    try 
    { 
      m_conn.clearWarnings(); 
    }
    catch(SQLException e1) {}
    throw (new JOPAException("doProcFlex4", e.getMessage()));
  }
}

//----------------------------------------------------------------------

private Vector describeProc(String sProc) throws JOPAException
{
  Vector result = new Vector();

  String pkgOwner;
  String pkgName;
  String pkgProcName;
  String pkgDbLink;
  int    pkgP1Type;
  int    objectId;

  try
  {
    shell.log(3, "describeProc: resolving "+sProc);
    OracleCallableStatement cs = (OracleCallableStatement)m_conn.prepareCall(
    "BEGIN SYS.DBMS_UTILITY.NAME_RESOLVE(?,1,?,?,?,?,?,?); END;"
    );

    cs.setString(1,sProc);
    cs.registerOutParameter(2, OracleTypes.VARCHAR);
    cs.registerOutParameter(3, OracleTypes.VARCHAR);
    cs.registerOutParameter(4, OracleTypes.VARCHAR);
    cs.registerOutParameter(5, OracleTypes.VARCHAR);
    cs.registerOutParameter(6, OracleTypes.NUMBER);
    cs.registerOutParameter(7, OracleTypes.NUMBER);

    cs.execute();

    pkgOwner    = cs.getString(2);
    pkgName     = cs.getString(3);
    pkgProcName = cs.getString(4);
    pkgDbLink   = cs.getString(5);
    pkgP1Type   = cs.getInt(6);
    objectId    = cs.getInt(7);

    cs.close();
    shell.log(3, "describeProc: resolved to "+pkgOwner+"."+(pkgName == null ? "" : pkgName + ".")+pkgProcName);
  }
  catch(SQLException e)
  {  if(1==1){ respFatal(404, "File Not Found"); 
     shell.log(3, "\ndescribeProc exception in name resolution:\n"+e.getMessage());
     return result;}
     else {
     throw new JOPAException("\ndescribeProc exception in name resolution:\n"+e.getMessage());}
  }
  try
  { 
    shell.log(3, "describeProc: describing "+pkgOwner+"."+(pkgName == null ? "" : pkgName + ".")+pkgProcName);
    OraclePreparedStatement cs = (OraclePreparedStatement)m_conn.prepareStatement(
      "select ARGUMENT_NAME, TYPE_OWNER, TYPE_NAME, TYPE_SUBNAME, DATA_TYPE, OVERLOAD, POSITION\n"+
      "  from ALL_ARGUMENTS\n"+
      " where OWNER = ? and OBJECT_NAME = ? and "+
      (pkgName == null ? "PACKAGE_NAME IS NULL" : "PACKAGE_NAME=?")+"\n"+
      "   and DATA_LEVEL = 0 and IN_OUT='IN'"+
      " order by OVERLOAD,POSITION"
    );
  
    cs.setString(1, pkgOwner);
    cs.setString(2, pkgProcName);
    if (pkgName != null) cs.setString(3, pkgName);

    ResultSet rs = cs.executeQuery();

    Hashtable args = new Hashtable();
    int lastOverload = 1;
    while(rs.next())
    {
      String argName        = rs.getString(1);
      String argTypeOwner   = rs.getString(2);
      String argTypeName    = rs.getString(3);
      String argTypeSubname = rs.getString(4);
      String argDataType    = rs.getString(5);
         int iOver          = rs.getInt(6);
      if (argTypeName != null)
      {
//         if (argTypeOwner != null) argTypeName = argTypeOwner + "."+argTypeName;
// PUBLIC
        shell.log(3,"argTypeOwner:"+argTypeOwner);
         if      (argTypeOwner.equals("PUBLIC")) {}
         else if (argTypeOwner != null) argTypeName = argTypeOwner + "."+argTypeName;
         shell.log(3,"argTypeName:"+argTypeOwner);
// end public         
         if (argTypeSubname != null) argTypeName += "."+argTypeSubname;
      }
      else
        argTypeName = argDataType;
      ArgSignature arg = new ArgSignature(argName, argTypeName, argDataType);
      if (iOver > lastOverload)
      {
        shell.log(3,"Adding new signature.");
        result.add(args);
        args = new Hashtable();
        lastOverload = iOver;
      }
      shell.log(3,"Adding argument to overload #"+iOver+": "+arg);
      args.put(new Integer(rs.getInt(7)).toString(), arg);
    }
    shell.log(3,"Adding new signature.");
    result.add(args);

    rs.close();
    cs.close();
  }
  catch (SQLException e)
  {
     throw new JOPAException("describeProc exception in describe query:\n"+e.getMessage());
  }
  shell.log(3,"Described all overloads.");
  m_Described = true;
  return result;
}

//----------------------------------------------------------------------
private boolean matchFlexSig( Hashtable args, int flex )
{
   // are there 2 or 4 parameters in this signature?
   if (args == null || ( args.size() != 2 && args.size() != 4 ) )
     return false;

 try
 {
   switch(flex)
   {
      case 2:
             // we expect 2 parameters of type TABLE or PL/SQL TABLE here
             return args.size() == 2 &&
                    ( ((ArgSignature)args.get("1")).getDataType().indexOf("TABLE") > 0 ) &&
                    ( ((ArgSignature)args.get("2")).getDataType().indexOf("TABLE") > 0 );
      case 4:
             // we expect 1 NUMBER and 3 parameters of type TABLE or PL/SQL TABLE here
             return args.size() == 4 &&
                    ( ((ArgSignature)args.get("1")).getDataType().equals("NUMBER") ) &&
                    ( ((ArgSignature)args.get("2")).getDataType().indexOf("TABLE") > 0 ) &&
                    ( ((ArgSignature)args.get("3")).getDataType().indexOf("TABLE") > 0 ) &&
                    ( ((ArgSignature)args.get("4")).getDataType().indexOf("TABLE") > 0 );
      default:
             return false;
   }
 }
 catch (Exception e)
 { 
   shell.log(3,"matchFlexSig exception: "+e.getMessage());
   return false; 
 }
}

//----------------------------------------------------------------------

private Vector getArrayArgument( String argName )
{
  Vector result = new Vector();

  for (int i = 0; i < this.m_vecParData.size(); i++)
    if (((String)this.m_vecParName.elementAt(i)).equalsIgnoreCase(argName))
      result.addElement( ((CHAR)this.m_vecParData.elementAt(i)).stringValue() );
  return result;
}

//----------------------------------------------------------------------
/**
 * Checks if supplied argument is an array
 *
 * @param	argName	call argument name (don't mix with procedure argument name)
 * @return	boolean	<code>true</code> if argument appears to be an array,
 *		<code>false</code> otherwise.
 *
 */
private boolean isArrayArgument( String argName )
{
  int count = 0;
  for (int i = 0; i < this.m_vecParName.size(); i++)
    if ( ((String)this.m_vecParName.elementAt(i)).equalsIgnoreCase(argName) && count++ > 0 )
      return true;
  return false;
}

//----------------------------------------------------------------------
/**
 * Returns distinct argument names passed in
 *
 * @return	{@link HashSet} of all distinct arguments, uppercased
 *
 */
private HashSet getDistinctArgumentNames()
{
  HashSet result  = new HashSet();
  for (int i = 0; i < this.m_vecParName.size(); i++)
  {
    String s = ((String)this.m_vecParName.elementAt(i)).toUpperCase();
    if ( result.add(s) )
      shell.log(3, "Added "+s+" to the list of passed arguments");
  }
  return result;
}

//----------------------------------------------------------------------
/**
 * Returns given procedure's argument type
 *
 * @param	args	Hashtable of {@Link ArgSignature}s
 * @param	name	looked up argument name
 * @return	argument type or <code>null</code> if not found
 *
 */
private String getProcArgType( Hashtable args, String name )
{
  for (int i = 1; i <= args.size(); i++)
  {
    ArgSignature arg = (ArgSignature)args.get(new Integer(i).toString());
 try {
    if ( arg.getName().equalsIgnoreCase(name) )
      return arg.getType();
  }
  catch (NullPointerException e) {}
  }
  return null;
}


//----------------------------------------------------------------------
/**
 * Returns given procedure's argument data type
 *
 * @param	args	Hashtable of {@Link ArgSignature}s
 * @param	name	looked up argument name
 * @return	argument data type or <code>null</code> if not found
 *
 */
private String getProcArgDataType( Hashtable args, String name )
{
  for (int i = 1; i <= args.size(); i++)
  {
    ArgSignature arg = (ArgSignature)args.get(new Integer(i).toString());
  try{    
    if (arg.getName().equalsIgnoreCase(name) )
      return arg.getDataType();
  }
  catch (NullPointerException e) {}
    }
  return null;
}

//----------------------------------------------------------------------
/**
 * Tries to find an overloaded version of the procedure that matches
 * input argument names and types
 *
 * @param	names	array of argument names
 * @param	sProc	procedure name
 * @return	1-based index of the overload that matched arguments
 *
 * @throws	{@link JOPAException}
 */

private int findMatchingOverload(String[] names, String sProc)
throws JOPAException
{
   if (m_overloads.size() <= 1 || names.length == 0)
     return 0; // the only available version

   boolean matchFound = false;
   for (int i = 0; i < m_overloads.size(); i++)
   {
     Hashtable procargs = (Hashtable)m_overloads.elementAt(i);
     HashSet   pargnames = new HashSet();
     shell.log(3,"findMatchingOverload: comparing signature #"+i);

     for (int k = 1; k <= procargs.size(); k++)
       pargnames.add( ((ArgSignature)(procargs.get(new Integer(k).toString()))).getName().toUpperCase() );

     matchFound = true; // assume this version will match

     for (int k = 0; k < names.length; k++)
     {
       if ( pargnames.add( names[k].toUpperCase() ) )
       {
         // this argument is not present in the procedure signature
         matchFound = false;
         break;
       }
       // gotta ensure only that array arguments have corresponging array-typed proc arguments
       // if it's not an array argument, but proc argument is an array, we'll deal with it later
       // when binding.
       if ( isArrayArgument(names[k]) && !(getProcArgDataType(procargs, names[k]).indexOf("TABLE") > 0) )
       {
         matchFound = false;
         break;
       }
     }
     // ran through all declared arguments and they all have matches
     if (matchFound) return i;
   }
  return 0;  // assume first version anyway
}

/** ********************************************************************
 * Tries procedure for FPPC (Flexible Parameter Passing Convention)
 * and executes it if its signature matches one of FPPC versions
 *
 * @param	args	{@link Hashtable} of {@link ArgSignature}s for this [overloaded version of the] procedure
 * @param	sProc	procedure name
 * 
 * @return	<code>true</code> if the procedure was executed, <code>false</code> otherwise
 *
 * @throws {@link javax.servlet.ServletException}, {@link java.io.IOException}, {@link JOPAException}
 */
private void tryFlex(String sProc)
throws ServletException, IOException, JOPAException
{
  // try to match the proc signature with flexible argument passing
  // and call corresponding doProcFlexN() if matches.

  Hashtable args;
  int overzise = m_overloads.size();
  shell.log(3,"Trying tryFlex for any FPPC overolad size:"+overzise);
  for (int i = 0; i < overzise; i++)
  {
    shell.log(3,"Trying "+sProc+" overload #"+i+" for FPPC");
    args = (Hashtable)m_overloads.elementAt(i);
    if ( matchFlexSig(args, 2) )
    {
      shell.log(3,"Signature matches FPPC2");
      doProcFlex2(sProc, i);
      return;
    }
    if ( matchFlexSig(args, 4) )
    {
      shell.log(3,"Signature matches FPPC4");
      doProcFlex4(sProc, i);
      return;
    }
  }
}


/** ********************************************************************
 *  Executes stored procedure using normal argument passing<br>
 *
 *  @param	sProc	procedure name
 *
 *  @throws	{@link ServletException}, {@link IOException}, {@link JOPAException}
 */
private void doProcExpl (String sProc)
throws ServletException, IOException, JOPAException
{
    shell.log(3, "doProcExpl passed");
  // collect all distinct arguments passed in
  String[] names = new String[0];
  Hashtable args = null;

    // describe all overloads into a vector
    // parse anyway even do not have a parameters
    m_overloads = describeProc(sProc);
   // moved from see below position  AAT
  if ( m_vecParName.size() > 0 )
  {
    names = (String[])getDistinctArgumentNames().toArray(names);
    // describe all overloads into a vector
    //    m_overloads = describeProc(sProc);
    // try to find a procedure signature that matches our arguments
    args = (Hashtable)m_overloads.elementAt( findMatchingOverload(names, sProc) );
  }

  // Compose sql:
  StringBuffer stmt = new StringBuffer(256);

  // generate array translation stubs
  boolean hasArrays = false;
  boolean[] isArray = new boolean[names.length];
  for (int i = 0; i < names.length; i++)
  {
    String argDataType = getProcArgDataType(args, names[i]);
    if ( isArrayArgument(names[i]) || (argDataType != null && argDataType.indexOf("TABLE") > 0) )
    // either array passed in or procedure argument is of array type
    {
       if (!hasArrays) { stmt.append("declare\n jopa$idx pls_integer;\n jopa$idx2 pls_integer;\n"); hasArrays = true; }
       stmt.append(" "+names[i]+"$ "+getProcArgType(args, names[i])+";\n");
       stmt.append(" jopa$"+names[i]+" "+ARRAY_TYPE+" := ?;\n");
       isArray[i] = true;
    } 
    else
      isArray[i] = false;
  }
  stmt.append("begin\n");

  if (hasArrays)
  {
    for (int i = 0; i < names.length; i++)
    {
      if ( isArray[i] )
        stmt.append(" jopa$idx := jopa$"+names[i]+".first;\n jopa$idx2 := 1;\n"+
                    " while jopa$idx is not null loop\n"+
                    "  "+names[i]+"$(jopa$idx2) := jopa$"+names[i]+"(jopa$idx);\n"+
                    "  jopa$idx := jopa$"+names[i]+".next(jopa$idx);\n  jopa$idx2 := jopa$idx2+1;\n end loop;\n");
    }
  }
  stmt.append(" "+sProc);
  if (names.length > 0) {
    stmt.append('(');
    for (int i = 0; i < names.length; i++ )
    {
      if (i > 0) stmt.append(", ");
      if (isArray[i])
        stmt.append(names[i]+"=>"+names[i]+"$");
      else
        stmt.append(names[i]+"=>?");
    }
    stmt.append(')');
  }
  stmt.append(";\nend;");
  shell.log(3, "final statement:\n", stmt.toString());

  // Execute the sql:
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(stmt.toString());

    ArrayDescriptor adArray = getArrayDescriptor();

    int argIdx = 1;
    // bind all arrays first because we declared them before anything else
    for (int i = 0; i < names.length; i++)
    {
      String[] arrval = new String[0];
      if ( isArray[i] )
        {
          shell.log(3,"binding array argument \""+names[i]+"\"");
          arrval = (String[])getArrayArgument(names[i]).toArray(arrval);
          cs.setArray(argIdx++, new ARRAY(adArray, m_conn, arrval));
        }
    }
    // bind the rest of the arguments (assume strings)
    for (int i = 0; i < names.length; i++)
    {
      if ( !isArray[i] )
      {
        String argType = getProcArgType(args, names[i]);
        if (argType != null && (argType.indexOf("NUMBER") > 0 || argType.indexOf("INTEGER") > 0) )
        {
          shell.log(3,"binding numeric argument \""+names[i]+"\"");
          try
          {
            cs.setInt(argIdx++, Integer.parseInt(getPar(names[i])));
          }
          catch(NumberFormatException e)
          {
            throw new SQLException("Signature mismatch - non-numeric value passed to numeric argument.");
          }
        }
        else
        {
          shell.log(3,"binding string argument \""+names[i]+"\"");
          cs.setString(argIdx++, getPar(names[i]));
        }
      }
    }
    cs.execute();
    cs.close();
  }
  catch (SQLException e)
  { // direct execution failed for some reason, try flexible parameter passing
   shell.log(3,"direct execution failed for some reason:\n"+e.getMessage());
   /*ServletOutputStream out = response.getOutputStream();
   response.setContentType("text/html; charset=windows-1251");
   out.println("<pre>"+stmt.toString()+"</pre>");
   out.println("<br><pre>"+e.getMessage()+"</pre></br>");
   out.close();*/
    try 
    { 
      m_conn.clearWarnings(); 
    } 
    catch(SQLException e1) {shell.log(3,e1.getMessage());}
    shell.log(3,"Direct execution failed, trying FPPC");
    // try all overloaded versions for FPPC
    try
    {
      if(!m_Described)
        m_overloads = new Vector(); // no parameters were passed in
      tryFlex(sProc);
      return;
    }
    catch(Exception e2)
    {    
/*        byte[] data = e2.getMessage().getBytes("Cp1251");
        String mess = new String(data);*/
      throw new JOPAException("doProcExpl", e2.getMessage());
    }
  }
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
    /*
private int readChunk(byte[] srcBuff,int offset, byte[] dstBuff) {
     int slen =srcBuff.length, dlen = dstBuff.length;
     if(offset>slen)
     throw new  ArrayIndexOutOfBoundsException();
     if(offset==slen) return -1;
     if(slen<dlen){System.arraycopy(srcBuff,offset,dstBuff,0,slen); return slen;}
     if(slen-offset-dlen>-0){System.arraycopy(srcBuff,offset,dstBuff,0,dlen); return dlen;}
     else{System.arraycopy(srcBuff,offset,dstBuff,0,slen-offset); return slen-offset;
     }

}*/
     private boolean operCOPY (File src, File dst)
     {
       shell.log(3,"=> operCOPY: ");
       FileInputStream  fis = null;
       FileOutputStream fos = null;
       try {
         fis = new FileInputStream(src);
         fos = new FileOutputStream(dst);
         byte[] buf = new byte[BUFFER_SIZE];
         int n = fis.read(buf, 0, BUFFER_SIZE);
         while (n > 0) {
           fos.write(buf, 0, n);
           n = fis.read(buf, 0, BUFFER_SIZE);
         }
         try { fos.close(); } catch (IOException ioe1) {}
         try { fis.close(); } catch (IOException ioe1) {}
         return true;
       }
       catch (IOException ioe) {
         if (fos != null) try { fos.close(); } catch (IOException ioe1) {}
         if (fis != null) try { fis.close(); } catch (IOException ioe1) {}
         dst.delete();
       }
       return false;
     }
    
//----------------------------------------------------------------------
private void compareFiles (File src, String _pages) {
    String compareFile = _pages + this.sReqPath;
    File dirs = new File(m_localbase,_pages+this.sReqDir);
    if (!dirs.exists()){dirs.mkdirs();}
    File dist = new File(m_localbase, compareFile);
    if (!dist.exists()){
        operCOPY (src,dist);
        //shell.log(3,"=> operCOPY: "+dist.lastModified());
    }
    if (src.lastModified() > dist.lastModified()){
    //shell.log(3,"=> src.lastModified() >= dist.lastModified() : "+ src.lastModified() +":"+dist.lastModified());
    operCOPY (src,dist);
    //dist.setLastModified(src.lastModified());
    }
    //shell.log(3,"=> compareFiles.file.lastModified : "+ src.lastModified() +":"+dist.lastModified());
    }
//----------------------------------------------------------------------
 private void getPage ()
 throws ServletException, IOException, JOPAException
 {
   String stmt = shell.getCode("getPageOWA");
   try {
     OracleCallableStatement cs =
       (OracleCallableStatement)m_conn.prepareCall(stmt);
     cs.setString(1, getOracleCharsetName(this.effCharset) );
     cs.registerOutParameter(2, OracleTypes.VARCHAR);
     cs.registerOutParameter(3, OracleTypes.BLOB);
     cs.execute();
     respHeaders(cs.getString(2));
       // AAT
        if(downloadForced){doDownloadContentDisposition();}
       //
     respContent(cs.getBLOB(3));
     cs.close();
   }
   catch (SQLException e) {
     try { m_conn.clearWarnings(); } catch(SQLException e1) {}
     throw (new JOPAException("getPage", e.getMessage()));
   }
 }

protected void PreparePage ()
throws ServletException, IOException, JOPAException
{
  String stmt = shell.getCode("DPSPImportUnit");
  //String unitCode = "";  
  try{
  CLOB clob  = obtainClobTemp ();
  shell.log(3,"=> m_localbase: "+m_localbase+":"+this.sReqPath+":"+this.sReqName);
  File file = locateResource(this.sReqPath);
  shell.log(3,"=> file.lastModified : "+ file.lastModified());
  compareFiles(file,_PAGES);
  FileInputStream in = new FileInputStream(file);
  isFileExists = true;
   try 
    {
         byte[] buf = new byte[BUFFER_SIZE];
         int n = in.read(buf, 0, BUFFER_SIZE);
         //unitCode = new String(buf);
         int len = 0;
         while (n > 0) {
           clob.setString(len+1,new String(buf),0,n);
           len = len + BUFFER_SIZE;
           n = in.read(buf, 0, BUFFER_SIZE);
         }
    }
  catch (SQLException e){
          byte[] data = e.getMessage().getBytes("Cp1251");
          String mess = new String(data,"Cp866");
          //shell.log(3,"=> SQLException "+mess);}
          shell.log(3,"=> SQLException: ORA-"+e.getErrorCode()+":"+e.getMessage());}
  catch (java.lang.NullPointerException e){shell.log(3,"=> NullPointerException "+e.getMessage());}
    in.close();
    shell.log(3,"=> clob " + clob.getChunkSize());
     OracleCallableStatement cs = (OracleCallableStatement)m_conn.prepareCall(stmt);
    cs.setCLOB(1,clob);
    cs.setString(2,this.sReqDir);
    cs.execute();
    cs.close();
  }
  catch (FileNotFoundException fnot) {isFileExists=false;}
  catch (SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    throw (new JOPAException("\n", e.getMessage()+"\n"+ stmt+"\n"));
  }
 
}

//----------------------------------------------------------------------
/**
 * Returns argument type name for a procedure at given position. This
 * function tries to save db roundtrips until absolutely unavoidable.
 * If we already described the procedure, it will get result from
 * <code>m_overloads</code> table.
 *
 * @param	sProc	procedure name
 * @param	iOver	overload version (1-based)
 * @param	iPos	argument position (1-based)
 * @param	sDef	default return value if the lookup failed
 * @return	argument type name (either SQL or PL/SQL type)
 *
 * @throws	{@link JOPAException}
 */
private String getArgType(String sProc, int iOver, int iPos, String sDef)
throws JOPAException
{
  if(m_Described)
  {
    Hashtable args = (Hashtable)m_overloads.elementAt(iOver-1);
    return ((ArgSignature)args.get(new Integer(iPos).toString())).getType();
  }

  String sType = sDef;
  String sDataType = null;
  try
  {
    if (m_getArgTypeCS == null )
    {
       m_getArgTypeCS =
         (OracleCallableStatement)m_conn.prepareCall(shell.getCode("getArgType"));
    }
    m_getArgTypeCS.registerOutParameter(4, OracleTypes.VARCHAR);
    m_getArgTypeCS.registerOutParameter(5, OracleTypes.VARCHAR);
    m_getArgTypeCS.setString(1, sProc);
    m_getArgTypeCS.setInt(2, iPos);
    m_getArgTypeCS.setInt(3, iOver);
    m_getArgTypeCS.execute();
    sType = m_getArgTypeCS.getString(4);
    if (m_getArgTypeCS.wasNull()) 
      sType = sDef;
    sDataType = m_getArgTypeCS.getString(5);
    if (m_getArgTypeCS.wasNull()) 
      sDataType = null;
  }
  catch (SQLException e) {
    try { m_conn.clearWarnings(); } catch (SQLException e1) {}
    shell.log(5, "getArgType exception: "+e.getMessage());
    sType = sDef;
  }
  return sType+(sDataType != null ? ":"+sDataType : "");
}

//----------------------------------------------------------------------
/**
 * Returns argument name for a procedure at given position. This
 * function tries to save db roundtrips until absolutely unavoidable.
 * If we already described the procedure, it will get result from
 * <code>m_overloads</code> table.
 *
 * @param	sProc	procedure name
 * @param	iOver	overload version (1-based)
 * @param	iPos	argument position (1-based)
 * @param	sDef	default return value if the lookup failed
 * @return	argument name
 *
 * @throws	{@link JOPAException}
 */
private String getArgName(String sProc, int iOver, int iPos)
throws JOPAException
{
  if(m_Described)
  {
    Hashtable args = (Hashtable)m_overloads.elementAt(iOver-1);
    return ((ArgSignature)args.get(new Integer(iPos).toString())).getName();
  }
  String sName = null;
  try
  {
    if (m_getArgNameCS == null)
    {
      m_getArgNameCS =
        (OracleCallableStatement)m_conn.prepareCall(shell.getCode("getArgName"));
    }
    m_getArgNameCS.registerOutParameter(4, OracleTypes.VARCHAR);
    m_getArgNameCS.setString(1, sProc);
    m_getArgNameCS.setInt(2, iPos);
    m_getArgNameCS.setInt(3, iOver);
    m_getArgNameCS.execute();
    sName = m_getArgNameCS.getString(4);
    if (m_getArgNameCS.wasNull()) sName = null;
  }
  catch (SQLException e) {
    try 
    { m_conn.clearWarnings(); } 
    catch (SQLException e1) 
    { }
    shell.log(5, "getArgName exception: "+e.getMessage());
    sName = null;
  }
  return sName;
}


//----------------------------------------------------------------------
 protected void PrepareFile  ()
 throws ServletException, IOException, JOPAException
 {
    downloadFilename = unescape(this.sReqPath);
    downloadFilename = new String(downloadFilename.getBytes("Cp1251"),"UTF8");
    //shell.log(3,"=> downloadFilename:"+downloadFilename);
    this.sReqPath =  downloadFilename;
    shell.log(3,"=> PrepareFile: "+m_localbase+":"+this.sReqPath+":"+this.sReqName);
    try {
     File file = locateResource(downloadFilename);
     //shell.log(3,"=> PrepareFile : "+ file.lastModified());
     FileInputStream in = new FileInputStream(file);
     downloadFileType = ViewFile.file2mime(downloadFilename);
     shell.log(3,"=> ViewFile.getsMimeType : "+ downloadFileType);
     response.setContentType(downloadFileType);
     isFileExists = true;
      int    n;
      byte[] buf = new byte[BUFFER_SIZE];
     OutputStream sos = this.response.getOutputStream();
     while ((n = in.read(buf)) != -1)sos.write(buf, 0, n);
     in.close();
     sos.flush();
     sos.close();
    }
     catch (FileNotFoundException fnot) {
         int m_iQueryMode = getQueryMode();
         switch (m_iQueryMode) {
           case 1: 
              shell.log(3,"=> PrepareFile:File Not Found:"+m_localbase+":"+this.sReqPath+":"+this.sReqName);
              establishConnection();
              prepareEnv();
              sendEnv = "sendEnv";
              sendEnv();
              prepareParams(this.request, preparePath());
              go();
              shell.log(3,"PrepareFile.doProc passed.");
          case 2:
              establishConnection();
              prepareEnv();
              sendEnv = "sendEnv";
              sendEnv();
              //doDownload();
              doDownloadNative();
         break;
         }
     return;
     }
 }


    //----------------------------------------------------------------------

protected void doDownloadNative()
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
        throw new JOPAException("doDownload", e.getMessage());
      }
    }

//----------------------------------------------------------------------

protected void doDownload ()
throws ServletException, IOException, JOPAException
{
  String sInfo = null;
  String stmt = shell.getCode("doDownloadOWA",
    "{P}", this.props.getProperty("document_proc", "NN$NTS_DISP_DAT.download"));
  shell.log(3,"doDownload:\n",stmt);    
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(stmt);
    cs.registerOutParameter(1, OracleTypes.VARCHAR);
    cs.execute();
    sInfo = cs.getString(1);
    if (cs.wasNull()) sInfo = null;
    cs.close();
    shell.log(3,"doDownload:sInfo=",sInfo);
  }
  catch(SQLException e) {
      shell.log(3,"doDownload:\n"+e.getMessage());
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    throw (new JOPAException("doDownload","\n"+e.getMessage()));
  }
  if (sInfo == null) {
    respStatus(204, HTTP_NO_CONTENT);
  }
  else if (sInfo.equals("B")) {
    getPageBlob();
  }
  else if (sInfo.equals("F")) {
    respStatus(400, HTTP_BAD_REQUEST);
  }
  else {
    shell.log(3,"doDownload:",sInfo);
    getPageData(sInfo);
  }
}

    //----------------------------------------------------------------------

protected void doDownloadContentDisposition ()
    throws ServletException, IOException, JOPAException
    {
      String sInfo = null;
      String stmt = shell.getCode("doDownloadContentDisposition",
        "{P}", this.props.getProperty("document_proc", "NN$NTS_DISP_DAT.download"));
      shell.log(3,"doDownloadContentDisposition:\n",stmt);    
      try {
        OracleCallableStatement cs =
          (OracleCallableStatement)m_conn.prepareCall(stmt);
        cs.registerOutParameter(1, OracleTypes.VARCHAR);
        cs.execute();
        sInfo = cs.getString(1);
        if (cs.wasNull()) sInfo = null;
        cs.close();
        shell.log(3,"doDownloadContentDisposition:sInfo=",sInfo);
        
      }
      catch(SQLException e) {
          shell.log(3,"doDownloadContentDisposition:\n"+e.getMessage());
        try { m_conn.clearWarnings(); } catch(SQLException e1) {}
        throw (new JOPAException("doDownloadContentDisposition","\n"+e.getMessage()));
      }
      if (sInfo == null) {
        respStatus(204, HTTP_NO_CONTENT);
      }
      else if (sInfo.equals("B")) {
        getPageBlob();
      }
      else if (sInfo.equals("F")) {
        respStatus(400, HTTP_BAD_REQUEST);
      }
      else {
        shell.log(3,"doDownloadContentDisposition:",sInfo);
        getPageData(sInfo);
      }
    }

//----------------------------------------------------------------------

protected void printString( OutputStream os, String sContent )
// quick and dirty string writer that serves the only purpose of sending
// 403 error messages for protected packages that match our exclusion list.
{
  try {
  OutputStreamWriter wr = new OutputStreamWriter(os);
  wr.write(sContent);
  }
  catch(IOException e)
  {
  }
}

//----------------------------------------------------------------------

private void getPageBlob ()
throws ServletException, IOException, JOPAException
{
  String stmt = shell.getCode("getPageBlob");
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
  catch (SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    throw (new JOPAException("getPageBlob", e.getMessage()));
  }
}

//----------------------------------------------------------------------

private void getPageData (String sInfo)
throws ServletException, IOException, JOPAException
{
  StringBuffer sbHeaders = new StringBuffer(256);
  String[] asInfo = parseInfoString(sInfo);

  sbHeaders.append("Status: 200 OK\n");

  sbHeaders.append("Content-Type: ");
  sbHeaders.append(asInfo[2]);
  sbHeaders.append('\n');

  sbHeaders.append("Content-Length: ");
  sbHeaders.append(asInfo[5]);
  sbHeaders.append('\n');
  sbHeaders.append('\n');

  String stmt = shell.getCode("getPageData",
    "{T}", this.props.getProperty("document_table", "NN$T_DOWNLOAD"));
  try {
    OracleCallableStatement cs =
      (OracleCallableStatement)m_conn.prepareCall(stmt);
    cs.registerOutParameter(2, OracleTypes.BLOB);
    cs.setString(1, asInfo[0]);
    cs.execute();
    respHeaders(sbHeaders.toString());
    respContent(cs.getBLOB(2));
    cs.close();
  }
  catch(SQLException e) {
    try { m_conn.clearWarnings(); } catch(SQLException e1) {}
    throw (new JOPAException("getPageData", e.getMessage()));
  }
}

//----------------------------------------------------------------------

private String[] parseInfoString (String sInfo)
{
  // Parsing info string - it is not a simple thing to do
  // when the string is produced by Asiatic fellows...
  String[] as = new String[6];
  int i, j, n;
  int iPos = 0;
  for (j = 0; j < 6; j++) as[j] = "";
  for (j = 0; j < 6; j++) {
    i = sInfo.indexOf('X', iPos);
    if (i < 0) return as;
    try { n = Integer.parseInt(sInfo.substring(iPos, i), 10); }
    catch(NumberFormatException nfe1) { n = 0; }
    i++;
    as[j] = sInfo.substring(i, i+n);
    iPos = i + n + 1;
  }
  return as;
}

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.81 $";
} // class PSP

//----------------------------------------------------------------------
