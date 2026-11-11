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

import java.io.ByteArrayInputStream;
import org.apache.ignite.testframework.GridTestUtils;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assume.assumeTrue;

/**
 * Tests that {@link GridUnsafeDataInput#reset()} drops a stream buffer above the release threshold.
 */
public class GridUnsafeDataInputReleaseTest {
    /** Threshold in effect. */
    private static final int THRESHOLD =
        GridTestUtils.getFieldValue(GridUnsafeDataInput.class, "EPOST_RELEASE_THRESHOLD");

    /** Skips the tests for a threshold outside the range they are written for. */
    @BeforeClass
    public static void checkThreshold() {
        assumeTrue("Threshold out of the tested range: " + THRESHOLD,
            2048 <= THRESHOLD && THRESHOLD <= 16 * 1024 * 1024);
    }

    /** */
    @Test
    public void testBufferAboveThresholdIsDropped() throws Exception {
        GridUnsafeDataInput in = new GridUnsafeDataInput();

        read(in, THRESHOLD + 1);
        assertEquals(THRESHOLD + 1, inBuf(in).length);

        in.reset();

        assertEquals(1024, inBuf(in).length);
    }

    /** */
    @Test
    public void testBufferAtThresholdIsKept() throws Exception {
        GridUnsafeDataInput in = new GridUnsafeDataInput();

        read(in, THRESHOLD);
        byte[] buf = inBuf(in);

        in.reset();

        assertSame(buf, inBuf(in));
    }

    /** */
    @Test
    public void testDataIsReadAfterDrop() throws Exception {
        GridUnsafeDataInput in = new GridUnsafeDataInput();

        read(in, THRESHOLD + 1);
        in.reset();

        byte[] data = pattern(4096);

        assertArrayEquals(data, read(in, data));
    }

    /** */
    @Test
    public void testCallerArrayIsNotKeptAfterReset() throws Exception {
        GridUnsafeDataInput in = new GridUnsafeDataInput();

        byte[] data = pattern(64);

        in.bytes(data, data.length);
        in.readFully(new byte[data.length]);
        in.reset();

        assertNotSame(data, GridTestUtils.getFieldValue(in, "buf"));
    }

    /**
     * @param in Input.
     * @param size Number of bytes to read from a stream.
     */
    private static void read(GridUnsafeDataInput in, int size) throws Exception {
        read(in, new byte[size]);
    }

    /**
     * @param in Input.
     * @param data Stream content.
     * @return Bytes read.
     */
    private static byte[] read(GridUnsafeDataInput in, byte[] data) throws Exception {
        byte[] res = new byte[data.length];

        in.inputStream(new ByteArrayInputStream(data));
        in.readFully(res);

        return res;
    }

    /**
     * @param in Input.
     * @return Stream buffer.
     */
    private static byte[] inBuf(GridUnsafeDataInput in) {
        return GridTestUtils.getFieldValue(in, "inBuf");
    }

    /**
     * @param size Size.
     * @return Bytes of a repeating pattern.
     */
    private static byte[] pattern(int size) {
        byte[] res = new byte[size];

        for (int i = 0; i < size; i++)
            res[i] = (byte)i;

        return res;
    }
}
