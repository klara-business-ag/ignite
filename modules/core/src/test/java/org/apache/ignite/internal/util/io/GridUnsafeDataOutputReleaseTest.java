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

package org.apache.ignite.internal.util.io;

import org.apache.ignite.testframework.GridTestUtils;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assume.assumeTrue;

/**
 * Tests that {@link GridUnsafeDataOutput#reset()} drops a buffer above the release threshold.
 */
public class GridUnsafeDataOutputReleaseTest {
    /** Initial buffer size. It differs from the 4 KiB the optimized marshaller passes, so the test sees it restored. */
    private static final int INIT_SIZE = 1000;

    /** Threshold in effect. */
    private static final int THRESHOLD =
        GridTestUtils.getFieldValue(GridUnsafeDataOutput.class, "EPOST_RELEASE_THRESHOLD");

    /** Skips the tests for a threshold outside the range they are written for. */
    @BeforeClass
    public static void checkThreshold() {
        assumeTrue("Threshold out of the tested range: " + THRESHOLD,
            INIT_SIZE <= THRESHOLD && THRESHOLD <= 16 * 1024 * 1024);
    }

    /** */
    @Test
    public void testBufferAboveThresholdIsDropped() throws Exception {
        GridUnsafeDataOutput out = new GridUnsafeDataOutput(INIT_SIZE);

        out.write(new byte[THRESHOLD + 1]);
        out.reset();

        assertEquals(INIT_SIZE, out.internalArray().length);

        int maxOff = GridTestUtils.getFieldValue(out, "maxOff");

        assertEquals(0, maxOff);
    }

    /** */
    @Test
    public void testBufferAtThresholdIsKept() throws Exception {
        assumeTrue("Threshold not an even number above twice the initial size: " + THRESHOLD,
            THRESHOLD % 2 == 0 && THRESHOLD / 2 > INIT_SIZE);

        GridUnsafeDataOutput out = new GridUnsafeDataOutput(INIT_SIZE);

        // On growth the stream allocates twice the size it needs. Writing half the threshold allocates the threshold.
        out.write(new byte[THRESHOLD / 2]);

        byte[] buf = out.internalArray();

        assertEquals(THRESHOLD, buf.length);

        out.reset();

        assertSame(buf, out.internalArray());
    }

    /** */
    @Test
    public void testNoArgBufferIsDroppedToEmpty() throws Exception {
        GridUnsafeDataOutput out = new GridUnsafeDataOutput();

        out.bytes(new byte[THRESHOLD + 1], 0);
        out.reset();

        assertEquals(0, out.internalArray().length);

        byte[] data = new byte[64];

        for (int i = 0; i < data.length; i++)
            data[i] = (byte)i;

        out.write(data);

        assertArrayEquals(data, out.array());
    }

    /** */
    @Test
    public void testDataIsWrittenAfterDrop() throws Exception {
        GridUnsafeDataOutput out = new GridUnsafeDataOutput(INIT_SIZE);

        out.write(new byte[THRESHOLD + 1]);
        out.reset();

        byte[] data = new byte[8192];

        for (int i = 0; i < data.length; i++)
            data[i] = (byte)i;

        out.write(data);

        assertArrayEquals(data, out.array());
    }
}
