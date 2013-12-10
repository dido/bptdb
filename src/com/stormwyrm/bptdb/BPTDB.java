/*
 * Copyright (c) 2013 Rafael R. Sevilla (http://games.stormwyrm.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.stormwyrm.bptdb;

import java.io.IOException;
import java.io.InputStream;

import com.badlogic.gdx.files.FileHandle;

/**
 * B+ Tree database. Should be able to read the same database files generated by the Ruby bptree.rb program
 * in the main directory.
 * 
 * @author Rafael R. Sevilla
 * @see BPTDBPB
 */
public class BPTDB
{
	public static final int IPAGE = 0;
	public static final int LPAGE = 1;
	public static final int INFIMUM_KEY = -2147483648;
	public static final int PAGESIZE = 256;
	public static final int DATASIZE = 4;
	public static final int MAXKEYS = (PAGESIZE/(DATASIZE*2)) - 2;

	/**
	 * Abstract class representing a B+ tree page.  Used for both leaf pages and internal pages.
	 * 
	 * @author Rafael R. Sevilla
	 * @see BPTDB
	 */
	abstract class Page
	{
		public int[] keys, values;
		public int nkeys;
		public int parent;
		private Integer[] findret;

		/**
		 * Creates a new page.
		 */
		public Page()
		{
			keys = new int[MAXKEYS];
			values = new int[MAXKEYS];
			parent = -1;
			findret = new Integer[2];
		}

		/**
		 * Find the nearest keys to <code>k</code> stored in the page.  It will return an array of two integers,
		 * the first of which is the closest key less than <code>k</code>, and the second the closest key greater
		 * than <code>k</code>.  If the key is present in the page, both elements of the array will be equal.  If
		 * <code>k</code> is less than all the keys stored in the page, the first element will be null and the second
		 * element will contain the smallest key in the page.  Similarly, if <code>k</code> is greater than all the
		 * keys in the page, the first element will contain the largest key in the page, and the second
		 * element will be null. 
		 *  
		 * @param <code>k</code> - the key to search for
		 * @return the key(s) closest to <code>k</code>.
		 **/
		protected Integer[] find(int k)
		{
			int first = 0, last = nkeys-1;

			if (keys[first] > k) {
				findret[0] = null;
				findret[1] = first;
				return(findret);
			}
			
			if (keys[last] < k) {
				findret[0] = last;
				findret[1] = null;
				return(findret);
			}

			while (last - first > 1) {
				int mid = (first + last)/2;
				if (keys[mid] == k) {
					findret[0] = findret[1] = mid;
					return(findret);
				}
				if (keys[mid] < k) {
					first = mid;
					continue;
				}
				last = mid;			
			}
			if (keys[first] == k) {
				last = first;
			} else if (keys[last] == k) {
				first = last;
			}
			findret[0] = first;
			findret[1] = last;
			return(findret);
		}

		/**
		 * Get the value of the key <code>k</code>.  What this returns depends on the type of page.
		 * @param <code>k</code> The key to search for
		 */
		public abstract Integer get(int k);
	}

	/**
	 * A B+ Tree leaf page.  The values inside leaf pages are file offsets to the actual data
	 * pointed to by their keys.
	 */
	public class LPage extends Page
	{
		/**
		 * Get the file offset value of the key <code>k</code>.  Returns null if the key is not present in the page requested.
		 * @param <code>k</code> the key to search for
		 * @return the corresponding value, or null if the key was not found.
		 */
		@Override
		public Integer get(int k)
		{
			Integer[] bounds = find(k);
			if (bounds[0] == null || bounds[0] != bounds[1]) {
				return(null);
			}
			return(values[bounds[0]]);
		}
	}

	/**
	 * A B+ Tree Internal Page. Internal pages can point to other internal pages or data pages.
	 */
	public class IPage extends Page
	{
		/**
		 * Get the file offset value of the closest key in the page less than <code>k</code>.
		 * If <code>k</code> is less than all values in the page, returns the value of the smallest element
		 * (the infimum).
		 * @param <code>k</code> the key to search for
		 */
		@Override
		public Integer get(int k)
		{
			Integer[] bounds = find(k);
			if (bounds[0] == null)
				return(values[bounds[1]]);
			return(values[bounds[0]]);
		}
	}

	/**
	 * Class to load pages.
	 */
	public class PageLoader
	{
		private FileHandle dbfile;
		private InputStream dbis;
		private IPage ipage;
		private LPage lpage;

		/**
		 * Creates a new page loader given a libgdx <code>FileHandle</code>.  There are no restrictions on
		 * the file handle, and it may be of any type supported by libgdx.
		 * @param <code>fh</code> The file handle to use for the page loader.
		 */
		public PageLoader(FileHandle fh)
		{
			dbfile = fh;
			dbis = fh.read();
			if (dbis.markSupported())
				dbis.mark(0);
			ipage = new IPage();
			lpage = new LPage();
		}

		/**
		 * Reset the DB input stream.  Uses reset if the stream allows it, else rereads the file so that
		 * its offset returns to the beginning.
		 */
		private void resetDBIS()
		{
			try {
				if (dbis.markSupported())
					dbis.reset();
				else
					throw new IOException("mark not supported");
			} catch (IOException e) {		
				dbis = dbfile.read();
			}
		}

		/**
		 * Load an integer from an <code>InputStream</code>.  Integers are encoded as big-endian two's complement
		 * signed integers.
		 * @param <code>fp</code> The input stream to read from
		 * @throws <code>IOException</code> if there was an error reading the stream.
		 */
		private int loadInt(InputStream fp) throws IOException
		{
			int ret = 0;

			for (int i=0; i<4; i++) {
				ret <<= 8;
				int b = fp.read() & 0xff;
				ret |= b;
			}
			return(ret);
		}

		/**
		 * Load a page from the stream at the file offset <code>offset</code>. Returns <code>null</code> if a page cannot
		 * be decoded at that location.
		 * @param <code>offset</code> The file offset at which to load a page.
		 */
		public Page loadPage(int offset)
		{
			Page page = null;
			try {
				resetDBIS();
				dbis.skip(offset);
				int t = loadInt(dbis);
				int len = t >> 1;

				page = ((t & 1) == IPAGE) ? ipage : lpage;
				page.nkeys = len;
				page.parent = loadInt(dbis);
				// This assumes the saved keys are sorted. If the data were generated
				// by the Ruby code, this is always true.
				for (int i=0; i<len; i++) {
					int k, v;
					k = loadInt(dbis);
					v = loadInt(dbis);
					page.keys[i] = k;
					page.values[i] = v;
				}
			} catch (IOException ex) {
			}
			return(page);
		}
	}

	private PageLoader pageloader;

	/**
	 * Create a new BPTDB instance, given the libgdx <code>FileHandle fh</code>.
	 * @param <code>fh</code> file handle on which this instance is based. 
	 */
	public BPTDB(FileHandle fh)
	{
		pageloader = new PageLoader(fh);
	}

	/**
	 * Search for the key <code>key</code> inside the database.  Returns the offset corresponding to the key if it
	 * exists, or <code>null</code> if the key is not present in the database.
	 * @param <code>key</code> the key to search for 
	 */
	public Integer search(int key)
	{
		int offset = 0;
		Page page;
		for (;;) {
			page = pageloader.loadPage(offset);
			if (page == null)
				return(null);
			if (page instanceof LPage) {
				return(page.get(key));
			}
			offset = page.get(key);
		}
	}
}