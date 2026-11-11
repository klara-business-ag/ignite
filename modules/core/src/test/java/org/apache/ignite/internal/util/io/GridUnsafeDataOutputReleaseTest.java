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
import static org.junit.Assume.assumeTrue;

/**
 * Tests that {@link GridUnsafeDataOutput#reset()} drops a buffer above the release threshold.
 */
public class GridUnsafeDataOutputReleaseTest {
    /** Threshold in effect. */
    private static final int THRESHOLD =
        GridTestUtils.getFieldValue(GridUnsafeDataOutput.class, "EPOST_RELEASE_THRESHOLD");

    /** Skips the tests for a threshold outside the range they are written for. */
    @BeforeClass
    public static void checkThreshold() {
        assumeTrue("Threshold out of the tested range: " + THRESHOLD,
            0 <= THRESHOLD && THRESHOLD <= 16 * 1024 * 1024);
    }

    /** */
    @Test
    public void testBufferAboveThresholdIsDropped() throws Exception {
        GridUnsafeDataOutput out = new GridUnsafeDataOutput(THRESHOLD + 1);

        out.write(new byte[THRESHOLD + 1]);
        out.reset();

        assertEquals(4096, out.internalArray().length);
    }

    /** */
    @Test
    public void testBufferAtThresholdIsKept() throws Exception {
        GridUnsafeDataOutput out = new GridUnsafeDataOutput(THRESHOLD);

        out.write(new byte[THRESHOLD]);
        out.reset();

        assertEquals(THRESHOLD, out.internalArray().length);
    }

    /** */
    @Test
    public void testDataIsWrittenAfterDrop() throws Exception {
        GridUnsafeDataOutput out = new GridUnsafeDataOutput(THRESHOLD + 1);

        out.write(new byte[THRESHOLD + 1]);
        out.reset();

        byte[] data = new byte[8192];

        for (int i = 0; i < data.length; i++)
            data[i] = (byte)i;

        out.write(data);

        assertArrayEquals(data, out.array());
    }
}
