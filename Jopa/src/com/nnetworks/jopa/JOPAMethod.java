//----------------------------------------------------------------------
// JOPAMethod.java
//
// $Id: JOPAMethod.java,v 1.5 2005/01/13 11:45:28 Bob Exp $
//
//----------------------------------------------------------------------

package com.nnetworks.jopa;

import com.nnetworks.jopa.*;

import javax.servlet.*;
import javax.servlet.http.*;

import java.io.IOException;
import java.util.Properties;

//----------------------------------------------------------------------

public interface JOPAMethod
{

//----------------------------------------------------------------------

public void init
( JOPAShell           shell
, PathInfo            pi
, Properties          props
, HttpServletRequest  request
, HttpServletResponse response
, ServletContext      context
);

//----------------------------------------------------------------------

public void service ()
throws ServletException, IOException;

//----------------------------------------------------------------------
} // interface JOPAMethod

//----------------------------------------------------------------------
