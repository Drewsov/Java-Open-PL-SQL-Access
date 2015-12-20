//----------------------------------------------------------------------
// Logger.java
//
// $Id: Logger.java,v 1.3 2005/01/13 11:41:26 Bob Exp $
//
//----------------------------------------------------------------------

package com.nnetworks.shell;

import java.io.*;
import java.util.*;
import java.text.DateFormat;

//----------------------------------------------------------------------

public class Logger
{

//----------------------------------------------------------------------
public static int    LogLevel = 3;
// Message level  control:
private int         iLevSys;
private int         iLevLog;

// Encoding control:
private String      sEncSys;
private String      sEncLog;

// Prefix control:
private String      sPrefix;
private int         iPrefix;
private String      sPrefix0;
private int         iPrefix0;

// Appending control:
private boolean     bAppend;

// Date/time support:
private GregorianCalendar cal;
private DateFormat  fmt;

// Console output:
private PrintStream cout;

// File output:
private PrintStream fout;
private FileOutputStream fos;
private File        file;

//----------------------------------------------------------------------
// Constructor/destructor:
//----------------------------------------------------------------------

public Logger ()
{
  // Default message levels:
  this.iLevSys  = -1;
  this.iLevLog  = -1;

  // Default Encodings: 
  this.sEncSys  = "cp866";
  this.sEncLog  = "Windows-1251";

  // Default prefixes:
  this.sPrefix  = "  ";
  this.iPrefix  = 2;
  this.sPrefix0 = "**";
  this.iPrefix0 = 2;

  // Default appending:
  this.bAppend  = true;

  // Init date/time:
  this.cal = new GregorianCalendar();
  this.fmt = DateFormat.getDateTimeInstance
               ( DateFormat.SHORT
               , DateFormat.DEFAULT
               );

  // Reset console output:
  this.cout     = null;

  // Reset file output:
  this.fout     = null;
  this.fos      = null;
  this.file     = null;
}

//----------------------------------------------------------------------

protected void finalize()
throws Throwable
{
  closeLog();
}

//----------------------------------------------------------------------
// Configurator:
//----------------------------------------------------------------------

public void config (Properties props)
{
  String s;

  s = props.getProperty("SysLevel");
  if (s != null) {
    setSysLevel(getInt(s, this.iLevSys));
  }

  s = props.getProperty("LogLevel");
  if (s != null) {
    setLogLevel(getInt(s, this.iLevLog));
   }

  s = props.getProperty("SysEncoding");
  if (s != null) {
    setSysEncoding(s);
  }

  s = props.getProperty("LogEncoding");
  if (s != null) {
    setLogEncoding(s);
  }

  s = props.getProperty("Prefix");
  if (s != null) {
    setPrefix(s);
  }

  s = props.getProperty("ErrPrefix");
  if (s != null) {
    setErrPrefix(s);
  }

  s = props.getProperty("LogFile");
  if (s != null) {
    setLogPath(s, this.bAppend);
  }

  s = props.getProperty("LogAppend");
  if (s != null) {
    setLogAppend(getBool(s, this.bAppend));
  }

}

//----------------------------------------------------------------------

public void getConfig (Properties props)
{
  props.setProperty("SysLevel", Integer.toString(this.iLevSys));
  props.setProperty("LogLevel", Integer.toString(this.iLevLog));
  props.setProperty("SysEncoding", this.sEncSys);
  props.setProperty("LogEncoding", this.sEncLog);
  props.setProperty("Prefix", this.iPrefix > 0 ? this.sPrefix : "");
  props.setProperty("ErrPrefix", this.iPrefix0 > 0 ? this.sPrefix0 : "");
  props.setProperty("LogFile", this.file == null ? "" : this.file.getPath());
  props.setProperty("LogAppend", this.bAppend ? "1" : "0");
}

//----------------------------------------------------------------------

private int getInt (String s, int iDef)
{
  if (s != null) {
    try { return Integer.parseInt(s, 10); }
    catch (NumberFormatException e) {}
  }
  return iDef;
}

private boolean getBool (String s, boolean bDef)
{
  if (s != null) {
         if (s.equals("1")) return true;
    else if (s.equals("0")) return false;
    else if (s.equalsIgnoreCase("true")) return true;
    else if (s.equalsIgnoreCase("false")) return false;
  }
  return bDef;
}

//----------------------------------------------------------------------
// Message level control:
//----------------------------------------------------------------------

public void setMsgLevel (int iLevSys, int iLevLog)
{
  this.iLevSys = iLevSys;
  this.iLevLog = iLevLog;
}

public void setSysLevel (int iLevSys)
{
  this.iLevSys = iLevSys;
}

public void setLogLevel (int iLevLog)
{
  this.iLevLog = iLevLog;
}

public int getSysLevel ()
{
  return this.iLevSys;
}

public int getLogLevel ()
{
  return this.iLevLog;
}

public int getMaxLevel ()
{
  return (this.iLevSys > this.iLevLog ? this.iLevSys : this.iLevLog);
}
//----------------------------------------------------------------------
// Encoding control:
//----------------------------------------------------------------------

public void setEncodings (String sEncSys, String sEncLog)
{
  setSysEncoding(sEncSys);
  setLogEncoding(sEncLog);
}

public void setSysEncoding (String sEncSys)
{
  if (sEncSys != null) {
    if (!this.sEncSys.equals(sEncSys)) {
      this.sEncSys = sEncSys;
      closeCout();
    }
  }
}

public void setLogEncoding (String sEncLog)
{
  if (sEncLog != null) {
    if (!this.sEncLog.equals(sEncLog)) {
      this.sEncLog = sEncLog;
      closeFout();
    }
  }
}

public String getSysEncoding ()
{
  return this.sEncSys;
}

public String getLogEncoding ()
{
  return this.sEncLog;
}

//----------------------------------------------------------------------
// Prefix control:
//----------------------------------------------------------------------

public void setPrefixes (String sPrefix, String sErrPrefix)
{
  setPrefix(sPrefix);
  setErrPrefix(sErrPrefix);
}

public void setPrefix (String sPrefix)
{
  this.sPrefix = sPrefix;
  this.iPrefix = (this.sPrefix == null ? 0 : this.sPrefix.length());
}

public void setErrPrefix (String sPrefix)
{
  this.sPrefix0 = sPrefix;
  this.iPrefix0 = (this.sPrefix0 == null ? 0 : this.sPrefix0.length());
}

public String getPrefix ()
{
  return this.sPrefix;
}

public String getErrPrefix ()
{
  return this.sPrefix0;
}

//----------------------------------------------------------------------
// Appending control:
//----------------------------------------------------------------------

public void setLogAppend (boolean b)
{
  if (b != this.bAppend) {
    this.bAppend = b;
    closeFout();
  }
}

public boolean getLogAppend ()
{
  return this.bAppend;
}

//----------------------------------------------------------------------
// Stream control:
//----------------------------------------------------------------------

public PrintStream getSysStream (int iLev)
{
  if ((this.iLevSys >= 0) && (iLev <= this.iLevSys)) {
    if (openCout()) {
      return this.cout;
    }
  }
  return null;
}

public PrintStream getLogStream (int iLev)
{
  if ((this.iLevLog >= 0) && (iLev <= this.iLevLog)) {
    if (openFout()) {
      return this.fout;
    }
  }
  return null;
}

public void setLogStream (PrintStream out)
{
  if (out != null) {
    closeFout();
    this.fout = out;
  }
}

public void setLogFile (File file, boolean bAppend)
{
  if (file != null) {
    this.file = file;
    this.bAppend = bAppend;
    closeFout();
  }
}

public void setLogPath (String sPath, boolean bAppend)
{
  if (sPath != null) {
    this.file = new File(sPath);
    this.bAppend = bAppend;
    closeFout();
  }
}

//----------------------------------------------------------------------

public void closeLog ()
{
  if (this.fout != null) closeFout();
  if (this.cout != null) closeCout();
}

//----------------------------------------------------------------------

private void closeCout ()
{
  if (this.cout != null) {
    this.cout = null;
  }
}

//----------------------------------------------------------------------

private boolean openCout ()
{
  if (this.cout == null) {
    try {
      this.cout = new PrintStream(System.out, true, this.sEncSys);
    }
    catch (UnsupportedEncodingException e) {
      this.cout = new PrintStream(System.out, true);
    }
  }
  return (this.cout != null);
}

//----------------------------------------------------------------------

private void closeFout ()
{
  if (this.fout != null) {
    this.fout.close();
    this.fout = null;
  }
  if (this.fos != null) {
    try { this.fos.close(); } catch (IOException e) {}
    this.fos = null;
  }
}

//----------------------------------------------------------------------

private boolean openFout ()
{
  if (this.fout == null) {
    if (this.fos == null) {
      if (this.file != null) {
        try {
          this.fos = new FileOutputStream(this.file, this.bAppend);
        }
        catch (IOException e1) {
          this.iLevLog = -1;
          this.fos = null;
        }
      }
    }
    if (this.fos != null) {
      try {
        this.fout = new PrintStream(this.fos, true, this.sEncLog);
      }
      catch (UnsupportedEncodingException e2) {
        this.fout = new PrintStream(this.fos, true);
      }
    }
  }
  return (this.fout != null);
}

//----------------------------------------------------------------------
// Logging:
//----------------------------------------------------------------------

private void sysPrefix (int iLev)
{
  if ((iLev <= 0) && (this.iPrefix0 > 0)) {
    this.cout.print(this.sPrefix0);
    this.cout.print(' ');
  }
  else if (this.iPrefix > 0) {
    this.cout.print(this.sPrefix);
    this.cout.print(' ');
  }
}

private void logPrefix (int iLev)
{
  if ((iLev <= 0) && (this.iPrefix0 > 0)) {
    this.fout.print(this.sPrefix0);
    this.fout.print(' ');
  }
  else if (this.iPrefix > 0) {
    this.fout.print(this.sPrefix);
    this.fout.print(' ');
  }
}

//----------------------------------------------------------------------

private void logTimeStamp ()
{
  this.cal.setTimeInMillis(System.currentTimeMillis());
  this.fout.print('[');
  this.fout.print(fmt.format(this.cal.getTime()));
  this.fout.print(" ");
  String s = Thread.currentThread().getName();
  int i = s.indexOf('-');
  if (i > 0) s = s.substring(i+1);
  this.fout.print('#');
  this.fout.print(s);
  this.fout.print("] ");
}

//----------------------------------------------------------------------

private int calcStat (int iLev)
{
  return
   ((this.iLevSys >= 0) && (iLev <= this.iLevSys) ? 2 : 0) +
   ((this.iLevLog >= 0) && (iLev <= this.iLevLog) ? 1 : 0);
}

//----------------------------------------------------------------------

public synchronized void log (int iLev, String sMsg)
{
  int n = calcStat(iLev);
  if (n != 0) {
    int m = 0;
    if ((sMsg == null ? 0 : sMsg.length()) > 0) m |= 1;
    if ((n & 2) != 0) {
      if (openCout()) {
        if (m != 0) {
          sysPrefix(iLev);
          this.cout.print(sMsg);
        }
        this.cout.println();
      }
    }
    if ((n & 1) != 0) {
      if (openFout()) {
        if (m != 0) {
          logTimeStamp();
          logPrefix(iLev);
          this.fout.print(sMsg);
        }
        this.fout.println();
      }
    }
  }
}

//----------------------------------------------------------------------

public synchronized void log (int iLev, String sMsg1, String sMsg2)
{
  int n = calcStat(iLev);
  if (n != 0) {
    int m = 0;
    if ((sMsg1 == null ? 0 : sMsg1.length()) > 0) m |= 1;
    if ((sMsg2 == null ? 0 : sMsg2.length()) > 0) m |= 2;
    if ((n & 2) != 0) {
      if (openCout()) {
        if (m != 0) {
          sysPrefix(iLev);
          if ((m & 1) != 0) this.cout.print(sMsg1);
          if ((m & 2) != 0) this.cout.print(sMsg2);
        }
        this.cout.println();
      }
    }
    if ((n & 1) != 0) {
      if (openFout()) {
        if (m != 0) {
          logTimeStamp();
          logPrefix(iLev);
          if ((m & 1) != 0) this.fout.print(sMsg1);
          if ((m & 2) != 0) this.fout.print(sMsg2);
        }
        this.fout.println();
      }
    }
  }
}

//----------------------------------------------------------------------

public synchronized void log (int iLev, String sMsg1, String sMsg2, String sMsg3)
{
  int n = calcStat(iLev);
  if (n != 0) {
    int m = 0;
    if ((sMsg1 == null ? 0 : sMsg1.length()) > 0) m |= 1;
    if ((sMsg2 == null ? 0 : sMsg2.length()) > 0) m |= 2;
    if ((sMsg3 == null ? 0 : sMsg3.length()) > 0) m |= 4;
    if ((n & 2) != 0) {
      if (openCout()) {
        if (m != 0) {
          sysPrefix(iLev);
          if ((m & 1) != 0) this.cout.print(sMsg1);
          if ((m & 2) != 0) this.cout.print(sMsg2);
          if ((m & 4) != 0) this.cout.print(sMsg3);
        }
        this.cout.println();
      }
    }
    if ((n & 1) != 0) {
      if (openFout()) {
        if (m != 0) {
          logTimeStamp();
          logPrefix(iLev);
          if ((m & 1) != 0) this.fout.print(sMsg1);
          if ((m & 2) != 0) this.fout.print(sMsg2);
          if ((m & 4) != 0) this.fout.print(sMsg3);
        }
        this.fout.println();
      }
    }
  }
}

//----------------------------------------------------------------------

public synchronized void log (int iLev, String sMsg1, String sMsg2,
                                        String sMsg3, String sMsg4)
{
  int n = calcStat(iLev);
  if (n != 0) {
    int m = 0;
    if ((sMsg1 == null ? 0 : sMsg1.length()) > 0) m |= 1;
    if ((sMsg2 == null ? 0 : sMsg2.length()) > 0) m |= 2;
    if ((sMsg3 == null ? 0 : sMsg3.length()) > 0) m |= 4;
    if ((sMsg4 == null ? 0 : sMsg4.length()) > 0) m |= 8;
    if ((n & 2) != 0) {
      if (openCout()) {
        if (m != 0) {
          sysPrefix(iLev);
          if ((m & 1) != 0) this.cout.print(sMsg1);
          if ((m & 2) != 0) this.cout.print(sMsg2);
          if ((m & 4) != 0) this.cout.print(sMsg3);
          if ((m & 8) != 0) this.cout.print(sMsg4);
        }
        this.cout.println();
      }
    }
    if ((n & 1) != 0) {
      if (openFout()) {
        if (m != 0) {
          logTimeStamp();
          logPrefix(iLev);
          if ((m & 1) != 0) this.fout.print(sMsg1);
          if ((m & 2) != 0) this.fout.print(sMsg2);
          if ((m & 4) != 0) this.fout.print(sMsg3);
          if ((m & 8) != 0) this.fout.print(sMsg4);
        }
        this.fout.println();
      }
    }
  }
}

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.3 $";
} // class Logger

//----------------------------------------------------------------------
