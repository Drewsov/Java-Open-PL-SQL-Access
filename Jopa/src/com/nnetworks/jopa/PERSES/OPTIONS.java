//----------------------------------------------------------------------
// PERSES.OPTIONS.java
//----------------------------------------------------------------------

package com.nnetworks.jopa.PERSES;

import com.nnetworks.jopa.PERSES.*;
import com.nnetworks.jopa.*;

import javax.servlet.*;
import javax.servlet.http.*;

import oracle.sql.*;
import oracle.jdbc.driver.*;

import java.sql.*;
import java.io.*;
import java.util.*;

//----------------------------------------------------------------------

public class OPTIONS extends PERSES
implements JOPAMethod
{

//----------------------------------------------------------------------

public void service ()
throws ServletException, IOException
{
  try {
    establishConnection();
    if (authenticate() > 0) {
      operOPTIONS();
    }
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

} // class OPTIONS

//----------------------------------------------------------------------
