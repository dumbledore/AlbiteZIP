/* net.sf.jazzlib.ZipFile
   Copyright (C) 2001, 2002, 2003 Free Software Foundation, Inc.

This file is part of GNU Classpath.

GNU Classpath is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2, or (at your option)
any later version.

GNU Classpath is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
General Public License for more details.

You should have received a copy of the GNU General Public License
along with GNU Classpath; see the file COPYING.  If not, write to the
Free Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
02111-1307 USA.

Linking this library statically or dynamically with other modules is
making a combined work based on this library.  Thus, the terms and
conditions of the GNU General Public License cover the whole
combination.

As a special exception, the copyright holders of this library give you
permission to link this library with independent modules to produce an
executable, regardless of the license terms of these independent
modules, and to copy and distribute the resulting executable under
terms of your choice, provided that you also meet, for each linked
independent module, the terms and conditions of the license of that
module.  An independent module is a module which is not derived from
or based on this library.  If you modify this library, you may extend
this exception to your version of the library, but you are not
obligated to do so.  If you do not wish to do so, delete this
exception statement from your version. */

package net.sf.jazzlib;

import java.io.DataInput;
import java.io.InputStream;
import java.io.IOException;
import java.io.EOFException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.NoSuchElementException;
import org.albite.io.RandomReadingFile;

/**
 * This class represents a Zip archive.  You can ask for the contained
 * entries, or get an input stream for a file entry.  The entry is
 * automatically decompressed.
 *
 * This class is thread safe:  You can open input streams for arbitrary
 * entries in different threads.
 *
 * @author Jochen Hoenicke
 * @author Artur Biesiadowski
 */
public class ZipFile implements ZipConstants
{

  /**
   * Mode flag to open a zip file for reading.
   */
  public static final int OPEN_READ = 0x1;

  /**
   * Mode flag to delete a zip file after reading.
   */
  public static final int OPEN_DELETE = 0x4;

  // Name of this zip file.
  private final String name;

  // File from which zip entries are read.
  private final RandomReadingFile raf;

  // The entries of this zip file when initialized and not yet closed.
  private Hashtable entries;

  private boolean closed = false;

  /**
   * Opens a Zip file with the given name for reading.
   * @exception IOException if a i/o error occured.
   * @exception ZipException if the file doesn't contain a valid zip
   * archive.
   */
  public ZipFile(final RandomReadingFile file)
          throws ZipException, IOException {
    this.raf = file;
    this.name = file.getName();
  }

  /**
   * Read an unsigned short in little endian byte order from the given
   * DataInput stream using the given byte buffer.
   *
   * @param di DataInput stream to read from.
   * @param b the byte buffer to read in (must be at least 2 bytes long).
   * @return The value read.
   *
   * @exception IOException if a i/o error occured.
   * @exception EOFException if the file ends prematurely
   */
  private final int readLeShort(DataInput di, byte[] b) throws IOException
  {
    di.readFully(b, 0, 2);
    return (b[0] & 0xff) | (b[1] & 0xff) << 8;
  }

  /**
   * Read an int in little endian byte order from the given
   * DataInput stream using the given byte buffer.
   *
   * @param di DataInput stream to read from.
   * @param b the byte buffer to read in (must be at least 4 bytes long).
   * @return The value read.
   *
   * @exception IOException if a i/o error occured.
   * @exception EOFException if the file ends prematurely
   */
  private final int readLeInt(DataInput di, byte[] b) throws IOException
  {
    di.readFully(b, 0, 4);
    return ((b[0] & 0xff) | (b[1] & 0xff) << 8)
	    | ((b[2] & 0xff) | (b[3] & 0xff) << 8) << 16;
  }

  
  /**
   * Read an unsigned short in little endian byte order from the given
   * byte buffer at the given offset.
   *
   * @param b the byte array to read from.
   * @param off the offset to read from.
   * @return The value read.
   */
  private final int readLeShort(byte[] b, int off)
  {
    return (b[off] & 0xff) | (b[off+1] & 0xff) << 8;
  }

  /**
   * Read an int in little endian byte order from the given
   * byte buffer at the given offset.
   *
   * @param b the byte array to read from.
   * @param off the offset to read from.
   * @return The value read.
   */
  private final int readLeInt(byte[] b, int off)
  {
    return ((b[off] & 0xff) | (b[off+1] & 0xff) << 8)
	    | ((b[off+2] & 0xff) | (b[off+3] & 0xff) << 8) << 16;
  }
  

  /**
   * Read the central directory of a zip file and fill the entries
   * array.  This is called exactly once when first needed. It is called
   * while holding the lock on <code>raf</code>.
   *
   * @exception IOException if a i/o error occured.
   * @exception ZipException if the central directory is malformed 
   */
  private void readEntries() throws ZipException, IOException
  {
    /* Search for the End Of Central Directory.  When a zip comment is 
     * present the directory may start earlier.
     * FIXME: This searches the whole file in a very slow manner if the
     * file isn't a zip file.
     */
    int pos = raf.length() - ENDHDR;
    byte[] ebs  = new byte[CENHDR];
    
    do
      {
	if (pos < 0)
	  throw new ZipException
	    ("central directory not found, probably not a zip file: " + name);
	raf.seek(pos--);
      }
    while (readLeInt(raf, ebs) != ENDSIG);
    
    if (raf.skipBytes(ENDTOT - ENDNRD) != ENDTOT - ENDNRD)
      throw new EOFException(name);
    int count = readLeShort(raf, ebs);
    if (raf.skipBytes(ENDOFF - ENDSIZ) != ENDOFF - ENDSIZ)
      throw new EOFException(name);
    int centralOffset = readLeInt(raf, ebs);

    entries = new Hashtable(count+count/2);
    raf.seek(centralOffset);
    
    byte[] buffer = new byte[16];
    for (int i = 0; i < count; i++)
      {
      	raf.readFully(ebs);
	if (readLeInt(ebs, 0) != CENSIG)
	  throw new ZipException("Wrong Central Directory signature: " + name);

	int method = readLeShort(ebs, CENHOW);
	int dostime = readLeInt(ebs, CENTIM);
	int crc = readLeInt(ebs, CENCRC);
	int csize = readLeInt(ebs, CENSIZ);
	int size = readLeInt(ebs, CENLEN);
	int nameLen = readLeShort(ebs, CENNAM);
	int extraLen = readLeShort(ebs, CENEXT);
	int commentLen = readLeShort(ebs, CENCOM);
	
	int offset = readLeInt(ebs, CENOFF);

	int needBuffer = Math.max(nameLen, commentLen);
	if (buffer.length < needBuffer)
	  buffer = new byte[needBuffer];

	raf.readFully(buffer, 0, nameLen);
	String name = stringFromSubarray(buffer, 0, 0, nameLen);

	ZipEntry entry = new ZipEntry(name);
	entry.setMethod(method);
	entry.setCrc(crc & 0xffffffffL);
	entry.setSize(size & 0xffffffffL);
	entry.setCompressedSize(csize & 0xffffffffL);
	entry.setDOSTime(dostime);
	if (extraLen > 0)
	  {
	    byte[] extra = new byte[extraLen];
	    raf.readFully(extra);
	    entry.setExtra(extra);
	  }
	if (commentLen > 0)
	  {
	    raf.readFully(buffer, 0, commentLen);
	    entry.setComment(new String(buffer, 0, commentLen));
	  }
	entry.offset = offset;
	entries.put(name, entry);
      }
  }

  /**
   * Closes the ZipFile.  This also closes all input streams given by
   * this class.  After this is called, no further method should be
   * called.
   * 
   * @exception IOException if a i/o error occured.
   */
  public void close() throws IOException
  {
    synchronized (raf)
      {
	closed = true;
	entries = null;
	raf.close();
      }
  }

  /**
   * Returns an enumeration of all Zip entries in this Zip file.
   *
   * <p />
   * <i>Note</i>: Returns the original zip entries, so one should not
   * write stuff to them, if one does not want to break something.
   * If that is unsuitable, one can clone the entries first, using
   * their clone method.
   */
  public Enumeration entries()
  {
    try
      {
//	return new ZipEntryEnumeration(getEntries().elements());
	return getEntries().elements();
      }
    catch (IOException ioe)
      {
	return null;
      }
  }

  /**
   * Checks that the ZipFile is still open and reads entries when necessary.
   *
   * @exception IllegalStateException when the ZipFile has already been closed.
   * @exception IOEexception when the entries could not be read.
   */
  private Hashtable getEntries() throws IOException
  {
    synchronized(raf)
      {
	if (closed)
	  throw new IllegalStateException("ZipFile has closed: " + name);

	if (entries == null)
	  readEntries();

	return entries;
      }
  }

  /**
   * Searches for a zip entry in this archive with the given name.
   *
   * @param the name. May contain directory components separated by
   * slashes ('/').
   * @return    the zip entry, or null if no entry with that name exists.
   *            <p />
   *            <i>Note</i>: Returns the original zip entry, so one should not
   *            write stuff to it, if one does not want to break something.
   *            If that is unsuitable, one can clone the entry first, using
   *            its clone method.
   */
  public ZipEntry getEntry(String name)
  {
    try
      {
	Hashtable entries = getEntries();
	ZipEntry entry = (ZipEntry) entries.get(name);
        //TODO: cloning not supported
	return entry != null ? (ZipEntry) entry : null;
      }
    catch (IOException ioe)
      {
	return null;
      }
  }


  //access should be protected by synchronized(raf)
  private byte[] locBuf = new byte[LOCHDR];

  /**
   * Checks, if the local header of the entry at index i matches the
   * central directory, and returns the offset to the data.
   * 
   * @param entry to check.
   * @return the start offset of the (compressed) data.
   *
   * @exception IOException if a i/o error occured.
   * @exception ZipException if the local header doesn't match the 
   * central directory header
   */
  private long checkLocalHeader(ZipEntry entry) throws IOException
  {
    synchronized (raf)
      {
	raf.seek(entry.offset);
	raf.readFully(locBuf);
	
	if (readLeInt(locBuf, 0) != LOCSIG)
	  throw new ZipException("Wrong Local header signature: " + name);

	if (entry.getMethod() != readLeShort(locBuf, LOCHOW))
	  throw new ZipException("Compression method mismatch: " + name);

	if (entry.getName().length() != readLeShort(locBuf, LOCNAM))
	  throw new ZipException("file name length mismatch: " + name);

	int extraLen = entry.getName().length() + readLeShort(locBuf, LOCEXT);
	return entry.offset + LOCHDR + extraLen;
      }
  }

  /**
   * Creates an input stream reading the given zip entry as
   * uncompressed data.  Normally zip entry should be an entry
   * returned by getEntry() or entries().
   *
   * @param entry the entry to create an InputStream for.
   * @return the input stream.
   *
   * @exception IOException if a i/o error occured.
   * @exception ZipException if the Zip archive is malformed.  
   */
  public InputStream getInputStream(ZipEntry entry) throws IOException
  {
    Hashtable entries = getEntries();
    String name = entry.getName();
    ZipEntry zipEntry = (ZipEntry) entries.get(name);
    if (zipEntry == null)
      throw new NoSuchElementException(name);

    int start = (int) checkLocalHeader(zipEntry);
    int method = zipEntry.getMethod();
    InputStream is = new PartialInputStream
      (raf, start, (int) zipEntry.getCompressedSize());
//    InputStream is = new BufferedInputStream(new PartialInputStream
//      (raf, start, (int) zipEntry.getCompressedSize()));
    switch (method)
      {
      case ZipOutputStream.STORED:
	return is;
      case ZipOutputStream.DEFLATED:
	return new InflaterInputStream(is, new Inflater(true));
      default:
	throw new ZipException("Unknown compression method " + method);
      }
  }
  
  /**
   * Returns the (path) name of this zip file.
   */
  public String getName()
  {
    return name;
  }

  /**
   * Returns the number of entries in this zip file.
   */
  public int size()
  {
    try
      {
	return getEntries().size();
      }
    catch (IOException ioe)
      {
	return 0;
      }
  }

  private static class PartialInputStream extends InputStream
  {
    private final RandomReadingFile raf;
    int filepos, end;

    public PartialInputStream(RandomReadingFile raf, int start, int len)
    {
      this.raf = raf;
      filepos = start;
      end = start + len;
    }
    
    public int available()
    {
      long amount = end - filepos;
      if (amount > Integer.MAX_VALUE)
	return Integer.MAX_VALUE;
      return (int) amount;
    }

    public int read() throws IOException {
        if (filepos == end) {
            return -1;
            }

        synchronized (raf) {
            raf.seek(filepos++);
            return raf.readByte();
	}
    }

    public int read(byte[] b, int off, int len) throws IOException
    {
      if (len > end - filepos)
	{
	  len = (int) (end - filepos);
	  if (len == 0)
	    return -1;
	}
      synchronized (raf)
	{
	  raf.seek(filepos);
	  int count = raf.read(b, off, len);
	  if (count > 0)
	    filepos += len;
	  return count;
	}
    }

    public long skip(long amount)
    {
      if (amount < 0)
	throw new IllegalArgumentException();
      if (amount > end - filepos)
	amount = end - filepos;
      filepos += amount;
      return amount;
    }
  }

    /**
     * Allocates a new <code>String</code> constructed from a subarray
     * of an array of 8-bit integer values.
     * <p>
     * The <code>offset</code> argument is the index of the first byte
     * of the subarray, and the <code>count</code> argument specifies the
     * length of the subarray.
     * <p>
     * Each <code>byte</code> in the subarray is converted to a
     * <code>char</code> as specified in the method above.
     *
     * @deprecated This method does not properly convert bytes into characters.
     * As of JDK&nbsp;1.1, the preferred way to do this is via the
     * <code>String</code> constructors that take a character-encoding name or
     * that use the platform's default encoding.
     *
     * @param      ascii     the bytes to be converted to characters.
     * @param      hibyte    the top 8 bits of each 16-bit Unicode character.
     * @param      offset    the initial offset.
     * @param      count     the length.
     * @exception  IndexOutOfBoundsException  if the <code>offset</code>
     *               or <code>count</code> argument is invalid.
     * @exception NullPointerException if <code>ascii</code> is
     *                       <code>null</code>.
     * @see        java.lang.String#String(byte[], int)
     * @see        java.lang.String#String(byte[], int, int, java.lang.String)
     * @see        java.lang.String#String(byte[], int, int)
     * @see        java.lang.String#String(byte[], java.lang.String)
     * @see        java.lang.String#String(byte[])
     */
    private static String stringFromSubarray(
            byte ascii[], int hibyte, int offset, int count) {
	if (offset < 0) {
	    throw new StringIndexOutOfBoundsException(offset);
	}
	if (count < 0) {
	    throw new StringIndexOutOfBoundsException(count);
	}
	// Note: offset or count might be near -1>>>1.
	if (offset > ascii.length - count) {
	    throw new StringIndexOutOfBoundsException(offset + count);
	}

	char value[] = new char[count];

	if (hibyte == 0) {
	    for (int i = count ; i-- > 0 ;) {
		value[i] = (char) (ascii[i + offset] & 0xff);
	    }
	} else {
	    hibyte <<= 8;
	    for (int i = count ; i-- > 0 ;) {
		value[i] = (char) (hibyte | (ascii[i + offset] & 0xff));
	    }
	}

        return new String(value);
    }
}
