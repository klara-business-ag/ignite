/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.binary.streams;

import org.apache.ignite.testframework.GridTestUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Tests that the thread-local allocator chunk drops an array above the release threshold on release.
 */
public class ThreadLocalAllocatorChunkReleaseTest {
    /** Threshold in effect. */
    private static final int THRESHOLD =
        GridTestUtils.getFieldValue(BinaryMemoryAllocator.class, "EPOST_RELEASE_THRESHOLD");

    /** Skips the tests for a threshold outside the range they are written for. */
    @BeforeClass
    public static void checkThreshold() {
        assumeTrue("Threshold out of the tested range: " + THRESHOLD,
            16 <= THRESHOLD && THRESHOLD <= 16 * 1024 * 1024);
    }

    /** Gives every test a fresh chunk, since the chunk lives in a thread local. */
    @Before
    public void before() {
        ThreadLocal<?> holders = GridTestUtils.getFieldValue(BinaryMemoryAllocator.THREAD_LOCAL, "holders");

        holders.remove();
    }

    /** */
    @Test
    public void testArrayAboveThresholdIsDropped() {
        BinaryMemoryAllocatorChunk chunk = BinaryMemoryAllocator.THREAD_LOCAL.chunk();

        byte[] data = chunk.allocate(THRESHOLD + 1);
        chunk.release(data, data.length);

        assertFalse(chunk.isAcquired());
        assertNotSame(data, chunk.allocate(16));
    }

    /** */
    @Test
    public void testArrayAtThresholdIsKept() {
        BinaryMemoryAllocatorChunk chunk = BinaryMemoryAllocator.THREAD_LOCAL.chunk();

        byte[] data = chunk.allocate(THRESHOLD);
        chunk.release(data, data.length);

        assertSame(data, chunk.allocate(16));
    }

    /** */
    @Test
    public void testForeignArrayIsIgnored() {
        BinaryMemoryAllocatorChunk chunk = BinaryMemoryAllocator.THREAD_LOCAL.chunk();

        byte[] data = chunk.allocate(16);
        chunk.release(new byte[THRESHOLD + 1], THRESHOLD + 1);

        assertTrue(chunk.isAcquired());
        assertSame(data, GridTestUtils.getFieldValue(chunk, "data"));
    }
}
