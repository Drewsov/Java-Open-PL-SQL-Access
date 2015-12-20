//----------------------------------------------------------------------
// PERSES.LockTable.java
//----------------------------------------------------------------------

package com.nnetworks.jopa.PERSES;

import com.nnetworks.jopa.PERSES.*;
import com.nnetworks.jopa.*;

import java.io.*;
import java.util.*;

//----------------------------------------------------------------------

public class LockTable
{

//----------------------------------------------------------------------

private JOPAShell m_shell;

private File      m_file;
private Vector    m_vec;
private int       m_dirty;

//----------------------------------------------------------------------

public LockTable (JOPAShell shell, String sFile)
{
  m_shell = shell;
  m_file  = new File(sFile);
  m_vec   = null;
  m_dirty = 0;
}

//----------------------------------------------------------------------

protected void finalize ()
throws Throwable
{
  close();
}

//----------------------------------------------------------------------

public void setFile (String sFile)
{
  File file = new File(sFile);
  if (!file.equals(m_file)) {
    close();
    m_file = file;
  }
}

//----------------------------------------------------------------------

public String getFile ()
{
  return m_file.getAbsolutePath();
}

//----------------------------------------------------------------------

public void open ()
{
  if (m_vec == null) {
    load();
    if (m_vec != null) update();
    if (m_vec == null) m_vec = new Vector(64);
  }
}

//----------------------------------------------------------------------

public void clean (int i)
{
  if (m_vec != null) {
    if (m_dirty > i) save();
  }
}

//----------------------------------------------------------------------

public void close ()
{
  if (m_vec != null) {
    if (m_dirty > 0) save();
    m_vec.removeAllElements();
    m_vec = null;
    m_shell.log(3, "LockTable closed.");
  }
}

//----------------------------------------------------------------------

public void load ()
{
  m_dirty = 0;
  try {
    FileInputStream fis = new FileInputStream(m_file);
    ObjectInputStream ois = new ObjectInputStream(fis);
    m_vec = (Vector) ois.readObject();
    fis.close();
    m_shell.log(3, "LockTable loaded: ", m_file.getAbsolutePath());
  }
  catch (java.io.IOException ioe) {
    m_shell.log(0, "LockTable.load/IO: ", ioe.getMessage());
  }
  catch (java.lang.ClassNotFoundException cnfe) {
    m_shell.log(0, "LockTable.load: ", cnfe.getMessage());
  }
}

//----------------------------------------------------------------------

public void save ()
{
  try {
    FileOutputStream fos = new FileOutputStream(m_file);
  	ObjectOutputStream oos = new ObjectOutputStream(fos);
  	oos.writeObject(m_vec);
  	oos.flush();
  	fos.close();
    m_dirty = 0;
    m_shell.log(3, "LockTable saved: ", m_file.getAbsolutePath());
  }
  catch (java.io.IOException ioe) {
    m_shell.log(0, "LockTable.save/IO: ", ioe.getMessage());
  }
}

//----------------------------------------------------------------------

public int count ()
{
  open();
  return m_vec.size();
}

//----------------------------------------------------------------------

public void addLock
( String sSchema
, String sToken
, String sETag
, String sOwner
, char   cDepth
, int    iTimeout
)
{
  LockEntry lock = new LockEntry();
  lock.set(sSchema, sToken, sETag, sOwner, cDepth, iTimeout);
  open();
  m_vec.addElement(lock);
  m_dirty++;
}

//----------------------------------------------------------------------

protected int findLock
( String sSchema
, String sToken
)
{
  LockEntry lock;
  open();
  if (m_vec.isEmpty()) return -1;
  int n = m_vec.size();
  for (int i = 0; i < n; i++) {
    lock = (LockEntry) m_vec.elementAt(i);
    if (lock.sSchema.equalsIgnoreCase(sSchema)) {
      if (lock.sToken.equals(sToken)) {
        lock = null;
        return i;
      }
    }
  }
  lock = null;
  return -1;
}

//----------------------------------------------------------------------

public void removeLock
( String sSchema
, String sToken
)
{
  int i = findLock(sSchema, sToken);
  if (i >= 0) {
    m_vec.removeElementAt(i);
    m_dirty++;
  }
}

//----------------------------------------------------------------------

public LockEntry getLock
( String sSchema
, String sToken
)
{
  int i = findLock(sSchema, sToken);
  if (i >= 0) return (LockEntry) m_vec.elementAt(i);
  return null;
}

//----------------------------------------------------------------------

public void refreshLock
( String sSchema
, String sToken
)
{
  int i = findLock(sSchema, sToken);
  if (i >= 0) {
    LockEntry lock = (LockEntry) m_vec.elementAt(i);
    lock.refresh();
    m_dirty++;
  }
}

//----------------------------------------------------------------------

public void refreshLock
( String sSchema
, String sToken
, int    iTimeout
)
{
  int i = findLock(sSchema, sToken);
  if (i >= 0) {
    LockEntry lock = (LockEntry) m_vec.elementAt(i);
    lock.refresh(iTimeout);
    m_dirty++;
  }
}

//----------------------------------------------------------------------

public Vector enumLocks
( String  sSchema
, String  sETag
, boolean bActual
)
{
  boolean b;
  LockEntry lock;
  Date dNow = new Date();
  Vector vec = new Vector(16);
  open();
  int n = m_vec.size();
  for (int i = 0; i < n; i++) {
    lock = (LockEntry) m_vec.elementAt(i);
    b = true;
    if (sSchema != null) {
      b &= sSchema.equalsIgnoreCase(lock.sSchema);
    }
    if (sETag != null) {
      b &= sETag.equals(lock.sETag);
    }
    if (bActual) {
      if (lock.iTimeout > 0) {
        b &= dNow.before(lock.dExpire);
      }
    }
    if (b) {
      vec.addElement(lock);
    }
  }
  return vec;
}

//----------------------------------------------------------------------

public void update ()
{
  if (m_vec != null) {
    int n = m_vec.size();
    if (n > 0) {
      LockEntry lock;
      Date dNow = new Date();
      for (int i = 0; i < n; i++) {
        lock = (LockEntry) m_vec.elementAt(i);
        if (lock.iTimeout > 0) {
          if (dNow.after(lock.dExpire)) {
            m_vec.removeElementAt(i);
            m_dirty++;
          }
        }
      }
      lock = null;
    }
  }
}

//----------------------------------------------------------------------

} // class LockTable

//----------------------------------------------------------------------
