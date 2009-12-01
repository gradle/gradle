/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.testing.execution.control.refork;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import org.apache.commons.lang.SerializationUtils;

import java.io.IOException;

/**
 * @author Tom Eyckmans
 */
public class ForkMemoryLowConfigTest {

    static final double OK_MEMORY_LOW_THRESHOLD = 75D;

    private ForkMemoryLowConfig config;

    @Before
    public void setUp() throws Exception
    {
        config = new ForkMemoryLowConfig(ReforkReasons.FORK_MEMORY_LOW);
    }

    @Test ( expected = IllegalArgumentException.class)
    public void nullReforkReasonConstructorTest()
    {
        config = new ForkMemoryLowConfig(null);

        fail();
    }

    @Test
    public void constructorTest()
    {
        config = new ForkMemoryLowConfig(ReforkReasons.FORK_MEMORY_LOW);

        assertEquals(ForkMemoryLowConfig.DEFAULT_MEMORY_LOW_THRESHOLD, config.getMemoryLowThreshold(), 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setNegativeMemoryLowThreshold()
    {
        config.setMemoryLowThreshold(-1);

        fail();
    }

    @Test(expected = IllegalArgumentException.class)
    public void setZeroMemoryLowThreshold()
    {
        config.setMemoryLowThreshold(0);

        fail();
    }

    @Test
    public void setOkMemoryLowThreshold()
    {
        config.setMemoryLowThreshold(OK_MEMORY_LOW_THRESHOLD);

        assertEquals(OK_MEMORY_LOW_THRESHOLD, config.getMemoryLowThreshold(), 0);
    }

    @Test
    public void serializationTest() throws IOException, ClassNotFoundException
    {
        config.setMemoryLowThreshold(OK_MEMORY_LOW_THRESHOLD);

        final byte[] objectBytes = SerializationUtils.serialize(config);

        final ForkMemoryLowConfig deserialized = (ForkMemoryLowConfig) SerializationUtils.deserialize(objectBytes);

        assertEquals(OK_MEMORY_LOW_THRESHOLD, deserialized.getMemoryLowThreshold(), 0);
    }
    


}
