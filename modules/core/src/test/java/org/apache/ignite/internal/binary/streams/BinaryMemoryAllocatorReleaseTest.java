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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Tests that the thread-local and the pooled allocator chunks drop an array above the release threshold on release.
 */
public class BinaryMemoryAllocatorReleaseTest {
    /** Release threshold set for the tests. */
    private static final int THRESHOLD = 64 * 1024;

    /** Release threshold to restore after a test. */
    private int oldThreshold;

    /** Sets the release threshold and clears the thread-local chunk and pool, so that every test starts empty. */
    @Before
    public void before() {
        oldThreshold = GridTestUtils.getFieldValue(BinaryMemoryAllocator.class, "RELEASE_THRESHOLD");

        GridTestUtils.setFieldValue(BinaryMemoryAllocator.class, "RELEASE_THRESHOLD", THRESHOLD);

        clearHolders();
    }

    /** Restores the release threshold and clears the thread-local chunk and pool. */
    @After
    public void after() {
        GridTestUtils.setFieldValue(BinaryMemoryAllocator.class, "RELEASE_THRESHOLD", oldThreshold);

        clearHolders();
    }

    /** */
    @Test
    public void testThreadLocalArrayAboveThresholdIsDropped() {
        checkArrayAboveThresholdIsDropped(BinaryMemoryAllocator.THREAD_LOCAL);
    }

    /** */
    @Test
    public void testPooledArrayAboveThresholdIsDropped() {
        checkArrayAboveThresholdIsDropped(BinaryMemoryAllocator.POOLED);
    }

    /** */
    @Test
    public void testThreadLocalArrayAtThresholdIsKept() {
        checkArrayAtThresholdIsKept(BinaryMemoryAllocator.THREAD_LOCAL);
    }

    /** */
    @Test
    public void testPooledArrayAtThresholdIsKept() {
        checkArrayAtThresholdIsKept(BinaryMemoryAllocator.POOLED);
    }

    /** */
    @Test
    public void testThreadLocalForeignArrayIsIgnored() {
        checkForeignArrayIsIgnored(BinaryMemoryAllocator.THREAD_LOCAL);
    }

    /** */
    @Test
    public void testPooledForeignArrayIsIgnored() {
        checkForeignArrayIsIgnored(BinaryMemoryAllocator.POOLED);
    }

    /**
     * The size check due on a release must not run after a drop, since it would allocate half of the dropped array.
     */
    @Test
    public void testThreadLocalDropSkipsSizeCheck() {
        BinaryMemoryAllocatorChunk chunk = BinaryMemoryAllocator.THREAD_LOCAL.chunk();

        byte[] data = chunk.allocate(THRESHOLD + 1);

        forceShrinkCheck(chunk);
        chunk.release(data, data.length);

        assertNull(GridTestUtils.getFieldValue(chunk, "data"));
    }

    /**
     * A drop forgets the message size of the dropped array, so that the next size check shrinks a small array.
     */
    @Test
    public void testThreadLocalDropResetsMaxMessageSize() {
        BinaryMemoryAllocatorChunk chunk = BinaryMemoryAllocator.THREAD_LOCAL.chunk();

        byte[] data = chunk.allocate(THRESHOLD + 1);

        chunk.release(data, data.length);

        data = chunk.allocate(1024);

        forceShrinkCheck(chunk);
        chunk.release(data, 1);

        assertEquals(data.length / 2, chunk.allocate(1).length);
    }

    /**
     * @param alloc Allocator.
     */
    private static void checkArrayAboveThresholdIsDropped(BinaryMemoryAllocator alloc) {
        BinaryMemoryAllocatorChunk chunk = alloc.chunk();

        byte[] data = chunk.allocate(THRESHOLD + 1);

        chunk.release(data, data.length);

        assertFalse(chunk.isAcquired());
        assertNotSame(data, alloc.chunk().allocate(16));
    }

    /**
     * @param alloc Allocator.
     */
    private static void checkArrayAtThresholdIsKept(BinaryMemoryAllocator alloc) {
        BinaryMemoryAllocatorChunk chunk = alloc.chunk();

        byte[] data = chunk.allocate(THRESHOLD);

        chunk.release(data, data.length);

        assertSame(data, alloc.chunk().allocate(16));
    }

    /**
     * @param alloc Allocator.
     */
    private static void checkForeignArrayIsIgnored(BinaryMemoryAllocator alloc) {
        BinaryMemoryAllocatorChunk chunk = alloc.chunk();

        byte[] data = chunk.allocate(16);

        chunk.release(new byte[THRESHOLD + 1], THRESHOLD + 1);

        assertTrue(chunk.isAcquired());

        chunk.release(data, data.length);

        assertSame(data, alloc.chunk().allocate(16));
    }

    /**
     * Makes the next release of a thread-local chunk run the size check, by moving the last check
     * {@code BinaryMemoryAllocator#CHECK_FREQ} milliseconds back.
     *
     * @param chunk Thread-local chunk.
     */
    private static void forceShrinkCheck(BinaryMemoryAllocatorChunk chunk) {
        long checkFreq = GridTestUtils.<Long>getFieldValue(BinaryMemoryAllocator.class, "CHECK_FREQ");

        GridTestUtils.setFieldValue(chunk, "lastCheckNanos", System.nanoTime() - checkFreq * 1_000_000);
    }

    /** Clears the thread-local chunk and pool. */
    private static void clearHolders() {
        GridTestUtils.<ThreadLocal<?>>getFieldValue(BinaryMemoryAllocator.THREAD_LOCAL, "holders").remove();
        GridTestUtils.<ThreadLocal<?>>getFieldValue(BinaryMemoryAllocator.POOLED, "holders").remove();
    }
}
