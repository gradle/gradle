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

import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.jmock.Expectations;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import org.gradle.api.testing.execution.Pipeline;

/**
 * @author Tom Eyckmans
 */
public class ForkMemoryLowDataProcessorTest {

    private JUnit4Mockery context = new JUnit4Mockery();

    private ForkMemoryLowDataProcessor dataProcessor;

    @Before
    public void setUp() throws Exception
    {
        context.setImposteriser(ClassImposteriser.INSTANCE);

        dataProcessor = new ForkMemoryLowDataProcessor(ReforkReasons.FORK_MEMORY_LOW);
    }

    @Test ( expected = IllegalArgumentException.class)
    public void nullConstructorTest()
    {
        dataProcessor = new ForkMemoryLowDataProcessor(null);

        fail();
    }

    @Test ( expected = IllegalArgumentException.class)
    public void nullConfigureTest()
    {
        dataProcessor.configure(null);

        fail();
    }

    @Test
    public void configureTest()
    {
        final ForkMemoryLowConfig config = context.mock(ForkMemoryLowConfig.class);

        context.checking(new Expectations(){{
            one(config).getMemoryLowThreshold();will(returnValue(ForkMemoryLowConfigTest.OK_MEMORY_LOW_THRESHOLD));
        }});

        dataProcessor.configure(config);

        assertEquals(ForkMemoryLowConfigTest.OK_MEMORY_LOW_THRESHOLD, dataProcessor.getMemoryLowThreshold(), 0);
    }

    @Test
    public void noneTriggeringDetermineReforkNeededTest()
    {
        final ForkMemoryLowConfig config = context.mock(ForkMemoryLowConfig.class);

        context.checking(new Expectations(){{
            one(config).getMemoryLowThreshold();will(returnValue(ForkMemoryLowConfigTest.OK_MEMORY_LOW_THRESHOLD));
        }});

        dataProcessor.configure(config);

        final Pipeline pipeline = context.mock(Pipeline.class);
        final int forkId = 1;

        final ForkMemoryLowData data = context.mock(ForkMemoryLowData.class);

        context.checking(new Expectations(){{
            one(data).getCurrentUsagePercentage();will(returnValue(50D));
        }});

        final boolean restartNeeded = dataProcessor.determineReforkNeeded(pipeline, forkId, data);

        assertFalse(restartNeeded);
    }

    @Test
    public void triggeringDetermineReforkNeededTest()
    {
        final ForkMemoryLowConfig config = context.mock(ForkMemoryLowConfig.class);

        context.checking(new Expectations(){{
            one(config).getMemoryLowThreshold();will(returnValue(50D));
        }});

        dataProcessor.configure(config);

        final Pipeline pipeline = context.mock(Pipeline.class);
        final int forkId = 1;

        final ForkMemoryLowData data = context.mock(ForkMemoryLowData.class);
        final long freeMemory = 49L;
        final long maxMemory = 100L;
        final long totalMemory = 99L;

        context.checking(new Expectations(){{
            one(data).getCurrentUsagePercentage();will(returnValue(51D));
            one(pipeline).getName();will(returnValue("default"));
            one(data).getFreeMemory();will(returnValue(freeMemory));
            one(data).getMaxMemory();will(returnValue(maxMemory));
            one(data).getTotalMemory();will(returnValue(totalMemory));
        }});

        final boolean restartNeeded = dataProcessor.determineReforkNeeded(pipeline, forkId, data);

        assertTrue(restartNeeded);
    }
}
