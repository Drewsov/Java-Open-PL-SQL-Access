package com.nnetworks.jopa;
//----------------------------------------------------------------------
// ViewFile.java
//
// $Id: ViewFile.java,v 1.82 2007/01/01 10:08:16 Drew Exp $
//
//----------------------------------------------------------------------
import com.nnetworks.jopa.*;
import java.io.*;
public class ViewFile extends Processor_PSP {
public static String _descr = "";

    /**
     * @param
     */
public static String getErrorFile(String fname, String Msg) {
           try {
               File f = new File(fname);
               DataInputStream is;
               is = new DataInputStream(new FileInputStream(f));
               byte[] buff = new byte[(int)f.length()] ;
               is.readFully(buff);
               return new String(buff)+"\n<br>"+Msg;
               }
               catch (FileNotFoundException e) {
               System.out.println("FileNotFoundException:"+fname);
               return null;
                   // TODO
               } catch (IOException e) {
               System.out.println("IOException:"+fname);
               return null;
                 // TODO
             }
         }

public static void setDescr() {
      try {
          File f = new File(Processor.MIME_TYPES);
          DataInputStream is;
          is = new DataInputStream(new FileInputStream(f));
          byte[] buff = new byte[(int)f.length()] ;
          is.readFully(buff);
         _descr =  new String(buff);
          }
          catch (FileNotFoundException e) {
              // TODO
          } catch (IOException e) {
            // TODO
        }
    }
public static  String file2mime(String fileName){
           setDescr();
           int offset = -1;
           if ((offset = fileName.lastIndexOf(".")) == -1) return "";
           String ext = fileName.substring(offset); 
           java.util.regex.Pattern recPattern = 
           java.util.regex.Pattern.compile("^\\s*([\\w\\/\\.\\-\\d]+)\\s+([\\w ]+\\s)*" 
               + ext 
               + "($|(\\s[\\w ]*$))", 
                   java.util.regex.Pattern.CASE_INSENSITIVE | 
                   java.util.regex.Pattern.MULTILINE );
           java.util.regex.Matcher mtch = recPattern.matcher(_descr);
           if (mtch.find()) {
               return mtch.group(1);
           }        
           return "";
       }

private static String fileRevision = "$Revision: 1.82 $";
} // class ViewFile

//----------------------------------------------------------------------