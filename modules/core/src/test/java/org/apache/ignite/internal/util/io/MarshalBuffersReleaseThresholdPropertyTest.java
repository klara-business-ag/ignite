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

import java.net.URL;
import java.net.URLClassLoader;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.ignite.internal.util.function.ThrowableFunction;
import org.apache.ignite.testframework.GridTestUtils;
import org.junit.Test;

import static org.apache.ignite.IgniteCommonsSystemProperties.IGNITE_MARSHAL_BUFFERS_RELEASE_THRESHOLD;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * Tests that {@code IGNITE_MARSHAL_BUFFERS_RELEASE_THRESHOLD} and its default set the release threshold of the patched
 * classes, and that the data streams keep a buffer no larger than their initial size whatever the threshold.
 * Each class reads the property once at initialization, so it is loaded again in a fresh class loader.
 */
public class MarshalBuffersReleaseThresholdPropertyTest {
    /** Non-default threshold. */
    private static final int THRESHOLD = 12345;

    /** Threshold below the initial size of both streams. */
    private static final int SMALL_THRESHOLD = 100;

    /** Initial size given to the output stream, larger than {@link #SMALL_THRESHOLD}. */
    private static final int OUTPUT_INIT_SIZE = 4096;

    /** Default threshold, 1 MiB. It is not read from {@code CommonUtils}, so that a changed default fails. */
    private static final int DFLT_THRESHOLD = 1024 * 1024;

    /** Name of the input stream class. */
    private static final String INPUT = "org.apache.ignite.internal.util.io.GridUnsafeDataInput";

    /** Name of the output stream class. */
    private static final String OUTPUT = "org.apache.ignite.internal.util.io.GridUnsafeDataOutput";

    /** Name of the allocator class. */
    private static final String ALLOCATOR = "org.apache.ignite.internal.binary.streams.BinaryMemoryAllocator";

    /** Classes whose code sources the fresh class loader needs. */
    private static final String[] CODE_SOURCE_CLASSES = {
        OUTPUT,
        "org.apache.ignite.internal.util.CommonUtils",
        "org.apache.ignite.internal.binary.streams.BinaryMemoryAllocatorChunk",
        ALLOCATOR
    };

    /** */
    @Test
    public void testInputThreshold() throws Exception {
        assertEquals(THRESHOLD, threshold(INPUT, THRESHOLD));
    }

    /** */
    @Test
    public void testOutputThreshold() throws Exception {
        assertEquals(THRESHOLD, threshold(OUTPUT, THRESHOLD));
    }

    /** */
    @Test
    public void testAllocatorThreshold() throws Exception {
        assertEquals(THRESHOLD, threshold(ALLOCATOR, THRESHOLD));
    }

    /** */
    @Test
    public void testDefaultThreshold() throws Exception {
        for (String clsName : new String[] {INPUT, OUTPUT, ALLOCATOR})
            assertEquals(clsName, DFLT_THRESHOLD, threshold(clsName, null));
    }

    /** */
    @Test
    public void testInputBufferAtInitialSizeIsKept() throws Exception {
        withClass(INPUT, SMALL_THRESHOLD, cls -> {
            Object in = cls.getConstructor().newInstance();
            byte[] buf = GridTestUtils.getFieldValue(in, "inBuf");

            cls.getMethod("reset").invoke(in);

            assertSame(buf, GridTestUtils.getFieldValue(in, "inBuf"));

            return null;
        });
    }

    /** */
    @Test
    public void testOutputBufferAtInitialSizeIsKept() throws Exception {
        withClass(OUTPUT, SMALL_THRESHOLD, cls -> {
            Object out = cls.getConstructor(int.class).newInstance(OUTPUT_INIT_SIZE);
            byte[] bytes = GridTestUtils.getFieldValue(out, "bytes");

            cls.getMethod("reset").invoke(out);

            assertSame(bytes, GridTestUtils.getFieldValue(out, "bytes"));

            return null;
        });
    }

    /**
     * @param clsName Name of a patched class.
     * @param val Value of the property, {@code null} to clear it.
     * @return Release threshold of the class initialized with that value.
     */
    private static int threshold(String clsName, Integer val) throws Exception {
        return withClass(clsName, val, cls -> GridTestUtils.<Integer>getFieldValue(cls, "EPOST_RELEASE_THRESHOLD"));
    }

    /**
     * Loads a class in a fresh class loader with the property set or cleared and applies a check to it.
     *
     * @param clsName Name of a patched class.
     * @param val Value of the property, {@code null} to clear it.
     * @param check Check applied to the loaded class.
     * @return Result of the check.
     */
    private static <T> T withClass(String clsName, Integer val,
        ThrowableFunction<T, Class<?>, Exception> check) throws Exception {
        Set<URL> urls = new LinkedHashSet<>();

        for (String name : CODE_SOURCE_CLASSES)
            urls.add(Class.forName(name).getProtectionDomain().getCodeSource().getLocation());

        String old = val == null
            ? System.clearProperty(IGNITE_MARSHAL_BUFFERS_RELEASE_THRESHOLD)
            : System.setProperty(IGNITE_MARSHAL_BUFFERS_RELEASE_THRESHOLD, Integer.toString(val));

        try (URLClassLoader ldr = new URLClassLoader(urls.toArray(new URL[0]), ClassLoader.getPlatformClassLoader())) {
            return check.apply(Class.forName(clsName, true, ldr));
        }
        finally {
            if (old == null)
                System.clearProperty(IGNITE_MARSHAL_BUFFERS_RELEASE_THRESHOLD);
            else
                System.setProperty(IGNITE_MARSHAL_BUFFERS_RELEASE_THRESHOLD, old);
        }
    }
}
