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
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * Tests that {@link GridUnsafeDataInput#reset()} and {@link GridUnsafeDataOutput#reset()} drop a buffer above
 * the release threshold.
 */
public class GridUnsafeDataInputOutputReleaseTest {
    /** Threshold in effect, the same for both streams. */
    private static final int THRESHOLD =
        GridTestUtils.getFieldValue(GridUnsafeDataInput.class, "RELEASE_THRESHOLD");

    /** Initial size of the input stream buffer. */
    private static final int IN_INIT_SIZE = 1024;

    /**
     * Initial size of the output buffer. It differs from the size the optimized marshaller passes, so the test sees
     * it restored.
     */
    private static final int OUT_INIT_SIZE = 1000;

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testInputBufferAboveThresholdIsDropped() throws Exception {
        GridUnsafeDataInput in = new GridUnsafeDataInput();

        read(in, THRESHOLD + 1);

        assertEquals(THRESHOLD + 1, inBuf(in).length);

        in.reset();

        assertEquals(IN_INIT_SIZE, inBuf(in).length);
        assertSame(inBuf(in), GridTestUtils.getFieldValue(in, "buf"));

        int maxOff = GridTestUtils.getFieldValue(in, "maxOff");

        assertEquals(0, maxOff);
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testInputBufferAtThresholdIsKept() throws Exception {
        GridUnsafeDataInput in = new GridUnsafeDataInput();

        read(in, THRESHOLD);

        byte[] buf = inBuf(in);

        assertEquals(THRESHOLD, buf.length);

        in.reset();

        assertSame(buf, inBuf(in));
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testInputDataIsReadAfterDrop() throws Exception {
        GridUnsafeDataInput in = new GridUnsafeDataInput();

        read(in, THRESHOLD + 1);
        in.reset();

        byte[] data = pattern(4096);

        assertArrayEquals(data, read(in, data));
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testInputCallerArrayIsNotKeptAfterReset() throws Exception {
        GridUnsafeDataInput in = new GridUnsafeDataInput();

        byte[] data = pattern(64);

        in.bytes(data, data.length);
        in.readFully(new byte[data.length]);
        in.reset();

        assertNotSame(data, GridTestUtils.getFieldValue(in, "buf"));
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testOutputBufferAboveThresholdIsDropped() throws Exception {
        GridUnsafeDataOutput out = new GridUnsafeDataOutput(OUT_INIT_SIZE);

        out.write(new byte[THRESHOLD + 1]);
        out.reset();

        assertEquals(OUT_INIT_SIZE, out.internalArray().length);

        int maxOff = GridTestUtils.getFieldValue(out, "maxOff");

        assertEquals(0, maxOff);
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testOutputBufferAtThresholdIsKept() throws Exception {
        GridUnsafeDataOutput out = new GridUnsafeDataOutput(OUT_INIT_SIZE);

        // On growth the stream allocates twice the size it needs. Writing half the threshold allocates the threshold.
        out.write(new byte[THRESHOLD / 2]);

        byte[] buf = out.internalArray();

        assertEquals(THRESHOLD, buf.length);

        out.reset();

        assertSame(buf, out.internalArray());
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testOutputNoArgBufferIsDroppedToEmpty() throws Exception {
        GridUnsafeDataOutput out = new GridUnsafeDataOutput();

        out.bytes(new byte[THRESHOLD + 1], 0);
        out.reset();

        assertEquals(0, out.internalArray().length);

        byte[] data = pattern(64);

        out.write(data);

        assertArrayEquals(data, out.array());
    }

    /** */
    @Test
    public void testOutputNoArgResetWithoutBuffer() {
        GridUnsafeDataOutput out = new GridUnsafeDataOutput();

        out.reset();

        assertNull(out.internalArray());
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testOutputDataIsWrittenAfterDrop() throws Exception {
        GridUnsafeDataOutput out = new GridUnsafeDataOutput(OUT_INIT_SIZE);

        out.write(new byte[THRESHOLD + 1]);
        out.reset();

        byte[] data = pattern(8192);

        out.write(data);

        assertArrayEquals(data, out.array());
    }

    /**
     * @param in Input.
     * @param size Number of bytes to read from a stream.
     * @throws Exception If failed.
     */
    private static void read(GridUnsafeDataInput in, int size) throws Exception {
        read(in, new byte[size]);
    }

    /**
     * @param in Input.
     * @param data Stream content.
     * @return Bytes read.
     * @throws Exception If failed.
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
