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

package org.apache.ignite.internal.marshaller.optimized;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.apache.ignite.internal.util.CommonUtils;
import org.apache.ignite.internal.util.io.GridUnsafeDataInput;
import org.apache.ignite.internal.util.io.GridUnsafeDataOutput;
import org.apache.ignite.marshaller.MarshallerContextTestImpl;
import org.apache.ignite.marshaller.Marshallers;
import org.apache.ignite.testframework.GridTestUtils;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;
import org.junit.Test;

/**
 * Tests that the per-thread streams of {@link OptimizedMarshaller} drop a large buffer after use if the release
 * threshold is set, and keep it by default (IGNITE-10858).
 */
public class OptimizedMarshallerBuffersReleaseTest extends GridCommonAbstractTest {
    /** Release threshold set for the tests. */
    private static final int THRESHOLD = 64 * 1024;

    /** Initial size of the input stream buffer. */
    private static final int IN_INIT_SIZE = 1024;

    /** Initial size of the output stream buffer, as given by the stream registry. */
    private static final int OUT_INIT_SIZE = 4 * 1024;

    /** Release threshold to restore after a test. */
    private int oldThreshold = CommonUtils.DFLT_MARSHAL_BUFFERS_RELEASE_THRESHOLD;

    /** {@inheritDoc} */
    @Override protected void beforeTest() throws Exception {
        super.beforeTest();

        oldThreshold = GridTestUtils.getFieldValue(GridUnsafeDataInput.class, "RELEASE_THRESHOLD");
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        setThreshold(oldThreshold);

        super.afterTest();
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testBuffersAboveThresholdAreDropped() throws Exception {
        setThreshold(THRESHOLD);

        marshalAndUnmarshal(new byte[2 * THRESHOLD]);

        assertEquals(OUT_INIT_SIZE, outBuf().length);
        assertEquals(IN_INIT_SIZE, inBuf().length);
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testBuffersAreKeptByDefault() throws Exception {
        assertEquals(Integer.MAX_VALUE, CommonUtils.DFLT_MARSHAL_BUFFERS_RELEASE_THRESHOLD);
        assertEquals(CommonUtils.DFLT_MARSHAL_BUFFERS_RELEASE_THRESHOLD, oldThreshold);

        int outThreshold = GridTestUtils.getFieldValue(GridUnsafeDataOutput.class, "RELEASE_THRESHOLD");

        assertEquals(CommonUtils.DFLT_MARSHAL_BUFFERS_RELEASE_THRESHOLD, outThreshold);

        marshalAndUnmarshal(new byte[2 * THRESHOLD]);

        assertTrue(outBuf().length > 2 * THRESHOLD);
        assertTrue(inBuf().length >= 2 * THRESHOLD);
    }

    /**
     * Marshals the data to an output stream and unmarshals it from an input stream, the paths that grow the buffers
     * of both thread-local streams.
     *
     * @param data Data.
     * @throws Exception If failed.
     */
    private static void marshalAndUnmarshal(byte[] data) throws Exception {
        OptimizedMarshaller marsh = Marshallers.optimizedForAllClasses();

        marsh.setContext(new MarshallerContextTestImpl());

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        marsh.marshal(data, out);

        byte[] res = marsh.unmarshal(new ByteArrayInputStream(out.toByteArray()), null);

        assertEquals(data.length, res.length);
    }

    /**
     * @param threshold Release threshold.
     */
    private static void setThreshold(int threshold) {
        GridTestUtils.setFieldValue(GridUnsafeDataInput.class, "RELEASE_THRESHOLD", threshold);
        GridTestUtils.setFieldValue(GridUnsafeDataOutput.class, "RELEASE_THRESHOLD", threshold);
    }

    /**
     * @return Buffer of the thread-local output stream.
     */
    private static byte[] outBuf() {
        OptimizedObjectOutputStream out = GridTestUtils.getFieldValue(holders().get(), "out");

        return out.out().internalArray();
    }

    /**
     * @return Buffer of the thread-local input stream.
     */
    private static byte[] inBuf() {
        OptimizedObjectInputStream in = GridTestUtils.getFieldValue(holders().get(), "in");

        return GridTestUtils.getFieldValue(in.in(), "inBuf");
    }

    /**
     * @return Thread-local stream holders of the stream registry.
     */
    private static ThreadLocal<?> holders() {
        return GridTestUtils.getFieldValue(OptimizedObjectSharedStreamRegistry.class, "holders");
    }
}
