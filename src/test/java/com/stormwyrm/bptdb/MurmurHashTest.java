package com.stormwyrm.bptdb;

import static org.junit.Assert.*;

import org.junit.Test;

import com.stormwyrm.bptdb.MurmurHash;

public class MurmurHashTest
{
	@Test public void testChangingSeed()
	{
        // use a fixed key
        byte [] key = new byte[] { 0x4E, (byte) 0xE3, (byte) 0x91, 0x00,
                                   0x10, (byte) 0x8F, (byte) 0xFF };

        int [] expected = {  0x51d5aa73, 0x23b8cbd0, 0x6d2c10e9, 0x5deb2403,
                			 0xce0a0f7c, 0x5f52c1e1, 0xa4e33a96, 0x09228bdb,
                			 0x2def66bd, 0x4b0a2391, 0xc8864444, 0x593f1a60,
                			 0x15825b01, 0xb0f04256, 0xebf55421, 0x2aff1b0a };

        for (int i = 0; i < expected.length; i++) {
            int expectedHash = expected[i];
            int hash = MurmurHash.hash(key, i);
            assertEquals("i = " + i, expectedHash, hash);
        }
    }

    @Test public void testChangingKey()
    {
        byte [] key = new byte[133];

        int [] expected = { 0x108bb224, 0x6699a1c1, 0x4205ea7a, 0x5a635b39,
                			0x4674c124, 0x500173ce, 0xfada1ef5, 0xda854d83,
                			0xe9cc0f5b, 0x68517082, 0x5ba90f8d, 0xe316e97c,
                			0x7cb4c206, 0xa9a0f739, 0x4728523a, 0x4462277f  };
        for (int i = 0; i < 16; i++) {
            // keep seed constant, generate a known key pattern
            setKey(key, i);
            int expectedHash = expected[i];
            int hash = MurmurHash.hash(key, 0x1234ABCD);
            assertEquals("i = " + i, expectedHash, hash);
        }
    }

    @Test public void testChangingKeyLength() {
        int [] expected = {  0x865366d4, 0x8cd4e3ce, 0xa5e2c3f5, 0x35c13d54,
        					 0x872425fc, 0x10df7f3f, 0xeb572c53, 0xdfae7ee9,
        					 0xcecc6c55, 0x9a8f6ae8, 0xd734d6b0, 0x6ddd724c,
        					 0x4dc113ab, 0xe8ee3178, 0xaecf904f, 0x2109de0c };
        // vary the key and the length
        for (int i = 0; i < 16; i++) {
            byte [] key = new byte[i];
            setKey(key, i);
            int expectedHash = expected[i];
            int hash = MurmurHash.hash(key, 0x7870AAFF);
            assertEquals("i = " + i, expectedHash, hash);
        }
    }

    /** Fill a key with a known pattern (incrementing numbers) */
    private void setKey(byte [] key, int start) {
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) ((start + i) & 0xFF);
        }
    }
}
