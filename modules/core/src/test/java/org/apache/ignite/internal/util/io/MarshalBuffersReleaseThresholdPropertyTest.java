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
import org.apache.ignite.testframework.GridTestUtils;
import org.junit.Test;

import static org.apache.ignite.IgniteCommonsSystemProperties.IGNITE_MARSHAL_BUFFERS_RELEASE_THRESHOLD;
import static org.junit.Assert.assertEquals;

/**
 * Tests that {@code IGNITE_MARSHAL_BUFFERS_RELEASE_THRESHOLD} sets the release threshold of the patched classes.
 * Each class reads the property once at initialization, so it is loaded again in a fresh class loader.
 */
public class MarshalBuffersReleaseThresholdPropertyTest {
    /** Non-default threshold. */
    private static final int THRESHOLD = 12345;

    /** Classes whose code sources the fresh class loader needs. */
    private static final String[] CODE_SOURCE_CLASSES = {
        "org.apache.ignite.internal.util.io.GridUnsafeDataOutput",
        "org.apache.ignite.internal.util.CommonUtils",
        "org.apache.ignite.internal.binary.streams.BinaryMemoryAllocatorChunk",
        "org.apache.ignite.internal.binary.streams.BinaryMemoryAllocator"
    };

    /** */
    @Test
    public void testInputThreshold() throws Exception {
        assertEquals(THRESHOLD, threshold("org.apache.ignite.internal.util.io.GridUnsafeDataInput"));
    }

    /** */
    @Test
    public void testOutputThreshold() throws Exception {
        assertEquals(THRESHOLD, threshold("org.apache.ignite.internal.util.io.GridUnsafeDataOutput"));
    }

    /** */
    @Test
    public void testAllocatorThreshold() throws Exception {
        assertEquals(THRESHOLD,
            threshold("org.apache.ignite.internal.binary.streams.BinaryMemoryAllocator"));
    }

    /**
     * @param clsName Name of a patched class.
     * @return Release threshold of the class initialized with the property set to {@link #THRESHOLD}.
     */
    private static int threshold(String clsName) throws Exception {
        Set<URL> urls = new LinkedHashSet<>();

        for (String name : CODE_SOURCE_CLASSES)
            urls.add(Class.forName(name).getProtectionDomain().getCodeSource().getLocation());

        String old = System.setProperty(IGNITE_MARSHAL_BUFFERS_RELEASE_THRESHOLD, Integer.toString(THRESHOLD));

        try (URLClassLoader ldr = new URLClassLoader(urls.toArray(new URL[0]), ClassLoader.getPlatformClassLoader())) {
            Class<?> cls = Class.forName(clsName, true, ldr);

            return GridTestUtils.<Integer>getFieldValue(cls, "EPOST_RELEASE_THRESHOLD");
        }
        finally {
            if (old == null)
                System.clearProperty(IGNITE_MARSHAL_BUFFERS_RELEASE_THRESHOLD);
            else
                System.setProperty(IGNITE_MARSHAL_BUFFERS_RELEASE_THRESHOLD, old);
        }
    }
}
