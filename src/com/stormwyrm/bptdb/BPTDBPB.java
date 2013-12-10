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
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;

/** Protobuf deserialising B+ Tree database */
public class BPTDBPB<T extends MessageLite>
{
	BPTDB db;
	public FileHandle fh;
	InputStream is;
	byte[] msgbytes;
	Parser<T> parser;

	public BPTDBPB(FileHandle fh, Parser<T> p)
	{
		this.fh = fh;
		is = fh.read();
		if (is.markSupported())
			is.mark(0);
		db = new BPTDB(fh);
		parser = p;
		msgbytes = new byte[BPTDB.PAGESIZE];
	}

	/** Reset the input stream's position. See if this works. */
	private void resetIS()
	{
		try {
			if (is.markSupported())
				is.reset();
			else
				throw new IOException("mark not supported");
		} catch (IOException e) {		
			is = fh.read();
		}
	}

	public T get(int k)
	{
		Integer ptr = db.search(k);

		if (ptr == null)
			return(null);
		resetIS();
		try {
			is.skip(ptr);
			// read size
			int size = is.read();
			if (is.read(msgbytes, 0, size) != size)
				return(null);
			return(parser.parseFrom(msgbytes, 0, size));
		} catch (IOException e) {
		};
		return(null);
	}

	public T get(String k)
	{
		int ik = MurmurHash.hash(k);
		return(get(ik));
	}
}
