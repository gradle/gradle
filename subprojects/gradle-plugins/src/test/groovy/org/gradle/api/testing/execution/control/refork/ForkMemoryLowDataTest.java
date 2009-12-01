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

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;
import org.apache.commons.lang.SerializationUtils;

/**
 * @author Tom Eyckmans
 */
public class ForkMemoryLowDataTest {

    static final long OK_FREE_MEMORY = 150000L;
    static final long OK_MAX_MEMORY = 750000L;
    static final long OK_TOTAL_MEMORY = 500000L;

    private ForkMemoryLowData data;

    @Before
    public void setUp() throws Exception
    {
        data = new ForkMemoryLowData();
    }

    @Test
    public void defaultConstructorTest()
    {
        data = new ForkMemoryLowData();

        assertEquals(-1, data.getFreeMemory());
        assertEquals(-1, data.getMaxMemory());
        assertEquals(-1, data.getTotalMemory());
    }

    @Test(expected = IllegalArgumentException.class)
    public void setNegativeFreeMemory()
    {
        data.setFreeMemory(-1);

        fail();
    }

    @Test
    public void setZeroFreeMemory()
    {
        data.setFreeMemory(0);
    }

    @Test
    public void setOkFreeMemory()
    {
        data.setFreeMemory(OK_FREE_MEMORY);

        assertEquals(OK_FREE_MEMORY, data.getFreeMemory());
    }

    @Test(expected = IllegalArgumentException.class)
    public void setNegativeMaxMemory()
    {
        data.setMaxMemory(-1);

        fail();
    }

    @Test(expected = IllegalArgumentException.class)
    public void setZeroMaxMemory()
    {
        data.setMaxMemory(0);

        fail();
    }

    @Test
    public void setOkMaxMemory()
    {
        data.setMaxMemory(OK_MAX_MEMORY);

        assertEquals(OK_MAX_MEMORY, data.getMaxMemory());
    }

    @Test(expected = IllegalArgumentException.class)
    public void setNegativeTotalMemory()
    {
        data.setTotalMemory(-1);

        fail();
    }

    @Test(expected = IllegalArgumentException.class)
    public void setZeroTotalMemory()
    {
        data.setTotalMemory(0);

        fail();
    }

    @Test
    public void setOkTotalMemory()
    {
        data.setTotalMemory(OK_TOTAL_MEMORY);

        assertEquals(OK_TOTAL_MEMORY, data.getTotalMemory());
    }

    @Test
    public void defaultUsagePercentage()
    {
        final double usagePercentage = data.getCurrentUsagePercentage();

        assertEquals(0, usagePercentage, 0);
    }

    @Test
    public void maxMemoryUsagePercentage()
    {
        data.setFreeMemory(0);
        data.setMaxMemory(100);

        final double usagePercentage = data.getCurrentUsagePercentage();

        assertEquals(100, usagePercentage, 0);
    }

    @Test
    public void zeroUsagePercentage()
    {
        data.setFreeMemory(100);
        data.setMaxMemory(100);

        final double usagePercentage = data.getCurrentUsagePercentage();

        assertEquals(0, usagePercentage, 0);
    }

    @Test
    public void serializationTest()
    {
        data.setFreeMemory(OK_FREE_MEMORY);
        data.setMaxMemory(OK_MAX_MEMORY);
        data.setTotalMemory(OK_TOTAL_MEMORY);

        final byte[] objectBytes = SerializationUtils.serialize(data);
        final ForkMemoryLowData deserialized = (ForkMemoryLowData) SerializationUtils.deserialize(objectBytes);

        assertEquals(OK_FREE_MEMORY, deserialized.getFreeMemory());
        assertEquals(OK_MAX_MEMORY, deserialized.getMaxMemory());
        assertEquals(OK_TOTAL_MEMORY, deserialized.getTotalMemory());
    }


}
