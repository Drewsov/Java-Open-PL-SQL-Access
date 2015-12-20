//----------------------------------------------------------------------
// WEBDAV.IfHeader.java
//
// $Id: IfHeader.java,v 1.3 2005/01/13 11:39:13 Bob Exp $
//
//----------------------------------------------------------------------

package com.nnetworks.jopa.WEBDAV;

import java.io.*;
import java.util.*;

//----------------------------------------------------------------------

class IfHeader
{

//----------------------------------------------------------------------

private static final int
  STATE_TOKEN      = 0x00,
  ENTITY_TAG       = 0x01,
  NOT_STATE_TOKEN  = 0x02,
  NOT_ENTITY_TAG   = 0x03,
  NOT_             = 0x02,
  WEAK_            = 0x04;

//----------------------------------------------------------------------

private Hashtable hashRes;
private StreamTokenizer st;

//----------------------------------------------------------------------

public IfHeader ()
{
  this.hashRes = new Hashtable(8);
}

//----------------------------------------------------------------------

public void clear ()
{
  this.hashRes.clear();
  this.st = null;
}

//----------------------------------------------------------------------

public void parse (String sHeaderValue)
{
  init(sHeaderValue);
  parseIfHeaderValue();
  this.st = null;
}

//----------------------------------------------------------------------

private void init (String sHeaderValue)
{
  this.hashRes.clear();
  this.st = new StreamTokenizer(new StringReader(sHeaderValue));
  this.st.resetSyntax();
  this.st.whitespaceChars(11, 32);
  this.st.quoteChar(34);
  this.st.wordChars(33, 33);  // !
  this.st.wordChars(35, 38);  // # &
  this.st.wordChars(42, 47);  // * /
  this.st.wordChars(58, 59);  // : ;
  this.st.wordChars(61, 61);  // =
  this.st.wordChars(63, 64);  // ? @
  this.st.wordChars(94, 95);  // ^ _
  this.st.wordChars((int)'0', (int)'9');
  this.st.wordChars((int)'A', (int)'Z');
  this.st.wordChars((int)'a', (int)'z');
  this.st.wordChars(126, 126);  // ^ _
  this.st.eolIsSignificant(false);
  this.st.lowerCaseMode(false);
  this.st.slashSlashComments(false);
  this.st.slashStarComments(false);
  try { this.st.nextToken(); }
  catch (IOException e) {}
}

//----------------------------------------------------------------------
// If-header = "If" ":" ( 1*No-tag-list | 1*Tagged-list )

public void parseIfHeaderValue ()
{
  Vector vecOr = null;
  if ((vecOr = parseListOfLists()) != null) {
    this.hashRes.put("*", vecOr);
  }
  else {
    String sRes = null;
    if ((sRes = parseCodedURL()) != null) {
      if ((vecOr = parseListOfLists()) != null) {
        this.hashRes.put(sRes, vecOr);
        while ((sRes = parseCodedURL()) != null) {
          if ((vecOr = parseListOfLists()) != null) {
            this.hashRes.put(sRes, vecOr);
          }
          else {
            break;
          }
        }
      }
    }
  }
}

//----------------------------------------------------------------------
// List-of-lists =  1*List

private Vector parseListOfLists ()
{
  Vector vecOr, vecAnd;
  if ((vecAnd = parseList()) != null) {
    vecOr = new Vector(8);
    vecOr.addElement(vecAnd);
    while ((vecAnd = parseList()) != null) {
      vecOr.addElement(vecAnd);
    }
    return vecOr;
  }
  return null;
}

//----------------------------------------------------------------------
// List = "(" 1*(["Not"] (State-token | Entity-tag)) ")"

private Vector parseList ()
{
  if (this.st.ttype == (int)'(') {
    try {
      this.st.nextToken();
      Vector vecAnd = new Vector(8);
      String s = null;
      int iNot = 0;
      while (true) {
        if (this.st.ttype == (int)')') {
          this.st.nextToken();
          return vecAnd;
        }
        else {
          iNot = (parseNot() ? NOT_ : 0);
          if ((s = parseStateToken()) != null) {
            //System.out.print("State-token: ");  System.out.println(s);
            vecAnd.addElement(new IfEntry(STATE_TOKEN | iNot, s));
          }
          else if ((s = parseEntityTag()) != null) {
            //System.out.print("Entity-tag: ");  System.out.println(s);
            vecAnd.addElement(new IfEntry(ENTITY_TAG | iNot, s));
          }
          else {
            this.st.pushBack();
            break;
          }
        }
      }
    }
    catch (IOException e) {
      System.out.print("IOException: ");  System.out.println(e.getMessage());
    }
  }
  return null;
}

//----------------------------------------------------------------------
// ["Not"]

private boolean parseNot ()
{
  if (this.st.ttype == this.st.TT_WORD) {
    if (this.st.sval.equalsIgnoreCase("Not")) {
      try {
        this.st.nextToken();
        return true;
      }
      catch (IOException e) {}
    }
  }
  return false;
}

//----------------------------------------------------------------------
// State-token = Coded-URL

private String parseStateToken ()
{
  String s = parseCodedURL();
  if (s != null) {
    if (s.startsWith("opaquelocktoken:")) {
      return s.substring(16);
    }
    if (s.startsWith("locktoken:")) {
      return s.substring(10);
    }
  }
  return s;
}

//----------------------------------------------------------------------
// "[" entity-tag "]"

private String parseEntityTag ()
{
  if (this.st.ttype == (int)'[') {
    try {
      this.st.nextToken();
      StringBuffer sb = new StringBuffer(64);
      while (this.st.ttype == this.st.TT_WORD || this.st.ttype == (int)'\"') {
        sb.append(this.st.sval);
        this.st.nextToken();
      }
      if (this.st.ttype == (int)']') {
        this.st.nextToken();
        return sb.toString();
      }
    }
    catch (IOException e) {}
  }
  return null;
}

//----------------------------------------------------------------------
// Coded-URL = "<" absoluteURL ">"

private String parseCodedURL ()
{
  if (this.st.ttype == (int)'<') {
    try {
      this.st.nextToken();
      if (this.st.ttype == this.st.TT_WORD || this.st.ttype == (int)'\"') {
        String s = this.st.sval;
        this.st.nextToken();
        if (this.st.ttype == (int)'>') {
          this.st.nextToken();
          return s;
        }
      }
      else {
        this.st.pushBack();
      }
    }
    catch (IOException e) {}
  }
  return null;
}

//----------------------------------------------------------------------

public boolean match (String sResource)
{
  boolean bMatches = true;
  if (this.hashRes != null) {
    if (this.hashRes.size() > 0) {
      Enumeration enRes = this.hashRes.keys();
      while (enRes.hasMoreElements()) {
        String sRes = (String) enRes.nextElement();
        if (sRes.equals("*") || sRes.equals(sResource)) {
          bMatches = true;
          Vector vecOr = (Vector) this.hashRes.get(sRes);
          if (vecOr != null) {
            bMatches = false;
            Enumeration enOr = vecOr.elements();
            while (enOr.hasMoreElements()) {
              Vector vecAnd = (Vector) enOr.nextElement();
              if (vecAnd != null) {
                Enumeration enAnd = vecAnd.elements();
                while (enAnd.hasMoreElements()) {
                  IfEntry entry = (IfEntry) enAnd.nextElement();
                  switch (entry.iMode & 0x01) {
                    case STATE_TOKEN:

                      break;
                    case ENTITY_TAG:

                      break;
                  }//switch
                }//while (enAnd)
              }//if (vecAnd)
              if (bMatches) return true;
            }//while (enOr)
          }//if (vecOr)
        }//if
      }//while (enRes)
    }
  }
  return bMatches;
}

//----------------------------------------------------------------------

public void spy ()
{
  String sRes;
  IfEntry entry;
  Vector vecOr, vecAnd;
  Enumeration enRes, enOr, enAnd;
  System.out.println("------------------------------------------");
  enRes = this.hashRes.keys();
  while (enRes.hasMoreElements()) {
    sRes = (String) enRes.nextElement();
    System.out.print(sRes);  System.out.println(":");
    vecOr = (Vector) this.hashRes.get(sRes);
    if (vecOr != null) {
      enOr = vecOr.elements();
      while (enOr.hasMoreElements()) {
        System.out.println("    or:");
        vecAnd = (Vector) enOr.nextElement();
        if (vecAnd != null) {
          enAnd = vecAnd.elements();
          while (enAnd.hasMoreElements()) {
            System.out.print("        and: ");
            entry = (IfEntry) enAnd.nextElement();
            if ((entry.iMode & NOT_) != 0) System.out.print("Not ");
            if ((entry.iMode & WEAK_) != 0) System.out.print("Weak ");
            switch (entry.iMode & 0x01) {
              case STATE_TOKEN:
                System.out.print('<');
                System.out.print(entry.sText);
                System.out.print('>');
                break;
              case ENTITY_TAG:
                System.out.print('[');
                System.out.print(entry.sText);
                System.out.print(']');
                break;
            }
            System.out.println('.');
          }
        }
      }
    }
  }
}

//----------------------------------------------------------------------

private class IfEntry
{
  public int    iMode;
  public String sText;

  public IfEntry (int iMode, String sText)
  {
    if (sText.startsWith("W/") || sText.startsWith("w/")) {
      this.iMode = iMode | WEAK_;
      this.sText = sText.substring(2);
    }
    else {
      this.iMode = iMode;
      this.sText = sText;
    }
  }
}

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.3 $";
} // class IfHeader

//----------------------------------------------------------------------
