//----------------------------------------------------------------------
// jopa.DPSPDAV.OPTIONS.java
//----------------------------------------------------------------------

package com.nnetworks.jopa.DPSPDAV;

import com.nnetworks.jopa.DPSPDAV.*;
import com.nnetworks.jopa.*;

import javax.servlet.*;
import javax.servlet.http.*;

import oracle.sql.*;
import oracle.jdbc.driver.*;

import java.sql.*;
import java.io.*;
import java.util.*;

//----------------------------------------------------------------------

public class OPTIONS extends DPSPDAV
implements JOPAMethod
{

//----------------------------------------------------------------------

public void service ()
throws ServletException, IOException
{
  try {
    establishConnection();
    if (authenticate(1) < 0) return;
    operOPTIONS();
  }
  catch (JOPAException e) {
    respFatal(500, e);
  }
  finally {
    releaseConnection();
  }
}

//----------------------------------------------------------------------

private void operOPTIONS ()
{
  respStatus(200, "OK");
  respWebDAV(4);
}

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.2 $";
} // class OPTIONS

//----------------------------------------------------------------------
