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
 * B+ Tree database. Should be able to read the same database files generated by the Ruby BPTDB class in misc
 */
public class BPTDB
{
	public static final int IPAGE = 0;
	public static final int LPAGE = 1;
	public static final int INFIMUM_KEY = -2147483648;
	public static final int PAGESIZE = 256;
	public static final int DATASIZE = 4;
	public static final int MAXKEYS = (PAGESIZE/(DATASIZE*2)) - 2;

	abstract class Page
	{
		public int[] keys, values;
		public int nkeys;
		public int parent;
		private Integer[] findret;

		public Page()
		{
			keys = new int[MAXKEYS];
			values = new int[MAXKEYS];
			parent = -1;
			findret = new Integer[2];
		}

		/** find the nearest keys to k */
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

		public abstract Integer get(int k);
	}

	public class LPage extends Page
	{
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

	public class IPage extends Page
	{
		@Override
		public Integer get(int k)
		{
			Integer[] bounds = find(k);
			return(values[bounds[0]]);
		}
	}

	public class PageLoader
	{
		private FileHandle dbfile;
		private InputStream dbis;
		private IPage ipage;
		private LPage lpage;

		public PageLoader(FileHandle fh)
		{
			dbfile = fh;
			dbis = fh.read();
			if (dbis.markSupported())
				dbis.mark(0);
			ipage = new IPage();
			lpage = new LPage();
		}

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

		public int loadInt(InputStream fp) throws IOException
		{
			int ret = 0;

			for (int i=0; i<4; i++) {
				ret <<= 8;
				int b = fp.read() & 0xff;
				ret |= b;
			}
			return(ret);
		}

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

	PageLoader pageloader;

	public BPTDB(FileHandle fh)
	{
		pageloader = new PageLoader(fh);
	}

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
