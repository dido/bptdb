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

import java.io.UnsupportedEncodingException;

public class MurmurHash
{
	private static final int C1 = 0xcc9e2d51;
	private static final int C2 = 0x1b873593;
	private static byte[] tmpdata = new byte[4];

	private static int rotl32(int x, int k)
	{
		return(((x << k) | (x >>> (32 - k))) & 0xffffffff);
	}	

	public static int hash(String str, int seed)
	{
		byte[] data;
		if (str == null)
			return(hash(0, seed));
		try {
			data = str.getBytes("UTF8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException("unexpected exception: " + e.getMessage());
		}
		return(hash(data, seed));
	}

	public static int hash(String str)
	{
		return(hash(str, 0xdeadbeef));
	}

	public static int hash(int val, int seed)
	{
		tmpdata[0] = (byte)(val & 0xff);
		tmpdata[1] = (byte)((val >> 8) & 0xff);
		tmpdata[2] = (byte)((val >> 16) & 0xff);
		tmpdata[3] = (byte)((val >> 24) & 0xff);
		return(hash(tmpdata, seed));
	}

	public static int hash(int val)
	{
		return(hash(val, 0xdeadbeef));
	}

	public static int hash(byte[] data, int seed)
	{
		int h1 = seed;
		int len = data.length;
		int i = 0;
		int k1 = 0;
		while (len >= 4) {
			k1 = data[i + 0] & 0xFF;
            k1 |= (data[i + 1] & 0xFF) << 8;
            k1 |= (data[i + 2] & 0xFF) << 16;
            k1 |= (data[i + 3] & 0xFF) << 24;
 
            k1 *= C1;
            k1 = rotl32(k1, 15);
            k1 *= C2;

            h1 ^= k1;
            h1 = rotl32(h1, 13);
            h1 = h1 * 5 + 0xe6546b64;

            i += 4;
            len -= 4;
		}

		// tail
		k1 = 0;
		switch (len) {
		case 3:
			k1 ^= (data[i + 2] & 0xFF) << 16;
        case 2:
        	k1 ^= (data[i + 1] & 0xFF) << 8;
        case 1:
        	k1 ^= (data[i + 0] & 0xFF);
        	k1 *= C1;
        	k1 = rotl32(k1, 15);
        	k1 *= C2;
        	h1 ^= k1;
		}
		h1 ^= data.length;
		h1 ^= h1 >>> 16;
        h1 *= 0x85ebca6b;
        h1 ^= h1 >>> 13;
        h1 *= 0xc2b2ae35;
        h1 ^= h1 >>> 16;
        return(h1);
	}
}
