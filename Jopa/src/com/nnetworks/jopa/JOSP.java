//----------------------------------------------------------------------
// JOSP.java  -- JOPA Servlet Pages processor
//
// $Id: JOSP.java,v 1.3 2005/01/13 11:41:26 Bob Exp $
//
//----------------------------------------------------------------------

package com.nnetworks.jopa;

import com.nnetworks.jopa.*;

import java.util.*;

//----------------------------------------------------------------------

public class JOSP
{

//----------------------------------------------------------------------

public static int processPage
( StringBuffer sbRes
, JOSPServer   objServer
, String       sName
)
{
  int iRes = processCode(sbRes, objServer, sName, objServer.getCode(sName));
  if (iRes < 0) { iRes = invokeMethod(sbRes, objServer, sName); }
  return iRes;
}

//----------------------------------------------------------------------

public static int processCode
( StringBuffer sbRes
, JOSPServer   objServer
, String       sName
, String       sCode
)
{
  if (sCode == null) return -1;
  int m = sCode.length();
  if (m <= 0) return 0;

  int i, i2, j, k, n, p;
  String sFunc, sRes;
  java.lang.reflect.Method method;
  Object[] args = new Object[2];
  Class[] params = new Class[2];
  params[0] = sName.getClass();
  params[1] = sName.getClass();
  Class clsServer = objServer.getClass();

  p = 0;
  while (p < m) {
    i = sCode.indexOf("<%", p);
    if (i < p) { sbRes.append(sCode.substring(p));  break; }
    sbRes.append(sCode.substring(p, i));
    i2 = i + 2;
    n = sCode.indexOf("%>", i2);
    if (n < i2) break;
    p = n + 2;
    j = sCode.indexOf("(", i2);
    k = sCode.indexOf(")", i2);
    if (i2 < j && j < k && k < n) {
      sFunc   = sCode.substring(i2, j).trim();
      args[0] = sCode.substring(j+1, k).trim();
      args[1] = sCode.substring(k+1, n);
      if (sFunc.equals("@")) {
        if (processPage(sbRes, objServer, (String)args[0]) < 0) {
          sbRes.append((String)args[1]);
        }
      }
      else if (sFunc.equals("=")) {
        if (args[0].equals("$"))
          sbRes.append(sName);
        else {
          sRes = objServer.getPar((String)args[0]);
          if (sRes != null)
            sbRes.append(sRes);
          else
            sbRes.append((String)args[1]);
        }
      }
      else {
        sRes = null;
        try {
          method = clsServer.getMethod(sFunc, params);
          sRes = (String) method.invoke(objServer, args);
        }
        catch (NoSuchMethodException e1) {}
        catch (SecurityException e2) {}
        catch (IllegalAccessException e3) {}
        catch (IllegalArgumentException e4) {}
        catch (java.lang.reflect.InvocationTargetException e5) {}
        if (sRes != null) sbRes.append(sRes);
      }
    }
  }

  return 1;
}

//----------------------------------------------------------------------

public static int invokeMethod
( StringBuffer sbRes
, JOSPServer   objServer
, String       sName
)
{
  try {
    java.lang.reflect.Method method = objServer.getClass().getMethod(sName, null);
    String sRes = (String) method.invoke(objServer, null);
    if (sRes != null) sbRes.append(sRes);
    return 1;
  }
  catch (NoSuchMethodException e1) {}
  catch (SecurityException e2) {}
  catch (IllegalAccessException e3) {}
  catch (IllegalArgumentException e4) {}
  catch (java.lang.reflect.InvocationTargetException e5) {}
  return -1;
}

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.3 $";
} // class JOSP

//----------------------------------------------------------------------
