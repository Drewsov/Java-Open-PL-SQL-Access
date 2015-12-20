package com.nnetworks.jopa;

/* -------------------------------------------------------------------------
   Base64.java

   Copyright(c) 2000-2002 by N-Networks. All rights reserved.
   Written by Bobby Z.

   Implements Base64 Java class. Class implements Base64 encoding of raw data
   and decoding of encoded strings.

   [10.02.2003] S.Ageshin added: encodeString(), decodeToString()
   [15.04.2003] S.Ageshin added: Encryption support

   ------------------------------------------------------------------------- */

public class Base64 {

//----------------------------------------------------------------------
//---[10.02.2003 S.Ageshin added this:

public static String encodeString (String s)
{
  return encodeString(s, "ISO-8859-1");
}

public static String encodeString (String s, String enc)
{
  try { return encode(s.getBytes(enc)); }
  catch (java.io.UnsupportedEncodingException e) {}
  return null;
}

//----------------------------------------------------------------------

public static String decodeToString (String s)
{
  return decodeToString(s, "ISO-8859-1");
}

public static String decodeToString (String s, String enc)
{
  try { return new String(decode(s), enc); }
  catch (java.io.UnsupportedEncodingException e) {}
  return null;
}

//---]
//----------------------------------------------------------------------

static public String encode (byte[] data)
// encodes raw data into string
{
 char[] out = new char[((data.length + 2) / 3) * 4];

 for (int i=0, index=0; i<data.length; i+=3, index+=4) {
  boolean quad = false;
  boolean trip = false;

  int val = (0xFF & (int) data[i]);
  val <<= 8;
  if ((i+1) < data.length) {
      val |= (0xFF & (int) data[i+1]);
      trip = true;
  }
  val <<= 8;
  if ((i+2) < data.length) {
    val |= (0xFF & (int) data[i+2]);
    quad = true;
  }
  out[index+3] = alphabet[(quad? (val & 0x3F): 64)];
  val >>= 6;
  out[index+2] = alphabet[(trip? (val & 0x3F): 64)];
  val >>= 6;
  out[index+1] = alphabet[val & 0x3F];
  val >>= 6;
  out[index+0] = alphabet[val & 0x3F];
 }
 return new String(out);
}

//----------------------------------------------------------------------

static public byte[] decode (String dta)
// decodes dta string into raw data
{
 // as our input could contain non-BASE64 data (newlines,
 // whitespace of any sort, whatever) we must first adjust
 // our count of USABLE data so that...
 // (a) we don't misallocate the output array, and
 // (b) think that we miscalculated our data length
 //     just because of extraneous throw-away junk

 char[] data = dta.toCharArray();

 int tempLen = data.length;
 for( int ix=0; ix<data.length; ix++ )
  {
   if( (data[ix] > 255) || codes[ data[ix] ] < 0 )
    --tempLen;  // ignore non-valid chars and padding
  }
 // calculate required length:
 //  -- 3 bytes for every 4 valid base64 chars
 //  -- plus 2 bytes if there are 3 extra base64 chars,
 //     or plus 1 byte if there are 2 extra.

 int len = (tempLen / 4) * 3;
 if ((tempLen % 4) == 3) len += 2;
 if ((tempLen % 4) == 2) len += 1;

 byte[] out = new byte[len];

 int shift = 0;   // # of excess bits stored in accum
 int accum = 0;   // excess bits
 int index = 0;

 // we now go through the entire array (NOT using the 'tempLen' value)
 for (int ix=0; ix<data.length; ix++)
 {
  int value = (data[ix]>255)? -1: codes[ data[ix] ];

  if ( value >= 0 )           // skip over non-code
  {
    accum <<= 6;            // bits shift up by 6 each time thru
    shift += 6;             // loop, with new bits being put in
    accum |= value;         // at the bottom.
    if ( shift >= 8 )       // whenever there are 8 or more shifted in,
    {
        shift -= 8;         // write them out (from the top, leaving any
        out[index++] =      // excess at the bottom for next iteration.
            (byte) ((accum >> shift) & 0xff);
    }
  }
  // we will also have skipped processing a padding null byte ('=') here;
  // these are used ONLY for padding to an even length and do not legally
  // occur as encoded data. for this reason we can ignore the fact that
  // no index++ operation occurs in that special case: the out[] array is
  // initialized to all-zero bytes to start with and that works to our
  // advantage in this combination.
 }

 // if there is STILL something wrong we just have to throw up now!
 if( index != out.length)
 {
  throw new Error("Miscalculated data length (wrote " + index + " instead of " + out.length + ")");
 }

 return out;
}

//----------------------------------------------------------------------
//
// code characters for values 0..63
//
static private char[] alphabet =
  "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/="
  .toCharArray();

//----------------------------------------------------------------------
//
// lookup table for converting base64 characters to value in range 0..63
//
static private byte[] codes = new byte[256];
static {
  for (int i=0; i<256; i++) codes[i] = -1;
  for (int i = 'A'; i <= 'Z'; i++) codes[i] = (byte)(     i - 'A');
  for (int i = 'a'; i <= 'z'; i++) codes[i] = (byte)(26 + i - 'a');
  for (int i = '0'; i <= '9'; i++) codes[i] = (byte)(52 + i - '0');
  codes['+'] = 62;
  codes['/'] = 63;
}

//----------------------------------------------------------------------
// Encryption support:
//----------------------------------------------------------------------

public static String scrambleString (String s)
{
  if (s == null) return s;
  int n = s.length();
  if (n == 0) return s;
  StringBuffer sb = new StringBuffer(s);
  char c1, c2;
  for (int i = 1; i < n; i += 2) {
    c1 = sb.charAt(i-1);
    c2 = sb.charAt(i);
    sb.setCharAt(i-1, c2);
    sb.setCharAt(i,   c1);
  }
  return sb.toString();
}

//----------------------------------------------------------------------

public static String unscrambleString (String s)
{
  return scrambleString(s);
}

//----------------------------------------------------------------------

public static String encryptPasw (String sPasw)
{
  if (sPasw != null) {
    if (sPasw.length() > 0) {
      return '!' +
             scrambleString(
               Base64.encodeString(
                 scrambleString(sPasw)
               )
             );
    }
  }
  return sPasw;
}

//----------------------------------------------------------------------

public static String decryptPasw (String sPasw)
{
  if (sPasw != null) {
    if (sPasw.length() > 0) {
      if (sPasw.charAt(0) == '!') {
        return unscrambleString(
                 Base64.decodeToString(
                   unscrambleString(sPasw.substring(1))
                 )
               );
      }
    }
  }
  return sPasw;
}

//----------------------------------------------------------------------
private static String fileRevision = "$Revision: 1.2 $";
}  // class Base64

//----------------------------------------------------------------------
