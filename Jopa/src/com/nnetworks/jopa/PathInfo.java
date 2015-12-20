//----------------------------------------------------------------------
// PathInfo.java
//
// $Id: PathInfo.java,v 1.4 2005/02/11 11:21:59 Bob Exp $
//
//----------------------------------------------------------------------

package com.nnetworks.jopa;

import java.util.*;

//----------------------------------------------------------------------

public class PathInfo
{

//----------------------------------------------------------------------

public static final String SPECIALS = "!^~";

//----------------------------------------------------------------------

public String[] asPathInfo;
public int      iCount;
public int      iLast;
public char     cMode;

//----------------------------------------------------------------------

public PathInfo (String sURI, String sServletPath)
{
  parse(sURI, sServletPath);
}

public PathInfo (String sPathInfo)
{
  parse(sPathInfo);
}

//----------------------------------------------------------------------

public void parse (String sURI, String sServletPath)
{
  int i = sURI.indexOf(sServletPath);
  parse( i >= 0 ? sURI.substring(i + sServletPath.length()) : sURI );
}

//----------------------------------------------------------------------
// Parse the rest of the URL path; for example:
// "http://localhost:8080/servlet/jopa/pspdev/a/b/c/!go?id_=17"
// gives:  [0]="pspdev", [1]="a", ..., [4]="!go"

public void parse (String sPathInfo)
{
  asPathInfo = null;
  iCount = 0;
  iLast  = -1;
  cMode  = ' ';
  if (sPathInfo != null) {
    if (sPathInfo.length() > 0) {
      if (sPathInfo.charAt(0) == '/') {
        sPathInfo = sPathInfo.substring(1);
      }
      StringTokenizer st = new StringTokenizer(sPathInfo, "/");
      iCount = st.countTokens();
      iLast = iCount - 1;
      if (iCount > 0) {
        asPathInfo = new String[iCount];
        int i = 0;
        while (st.hasMoreTokens()) {
          asPathInfo[i++] = st.nextToken();
          if (i >= iCount) break;
        }
      }
    }
  }
  if (iLast >= 0)
  {
    String s = asPathInfo[iLast];
    if (s.length() > 0)
    {
      char c = s.charAt(0);
      // check and unescape the first character as some "smart" clients may
      // escape exclamation marks and other characters.
      if (c == '%' && s.length() > 2 )
      {
         c = (char)Integer.parseInt(s.substring(1, 3), 16);
         s = c + s.substring(3);
      }
      if (SPECIALS.indexOf(c) >= 0) {
        cMode = c;
        asPathInfo[iLast] = s.substring(1);
      }
    }
  }
}

//----------------------------------------------------------------------

public String merge (int iStart, int iEnd)
{
  StringBuffer sb = new StringBuffer(256);
  if (iCount > 0) {
    if (iStart < 0) iStart = 0;
    if (iEnd > iCount || iEnd < 0) iEnd = iCount;
    for (int i = iStart; i < iEnd; i++) {
      sb.append('/');
      sb.append(asPathInfo[i]);
    }
  }
  return sb.toString();
}

//----------------------------------------------------------------------

public String getItem (int i)
{
  if (iCount == 0) return null;
  if (i < 0 || i >= iCount) i = iCount - 1;
  return asPathInfo[i];
}

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.4 $";
}  // class PathInfo

//----------------------------------------------------------------------
