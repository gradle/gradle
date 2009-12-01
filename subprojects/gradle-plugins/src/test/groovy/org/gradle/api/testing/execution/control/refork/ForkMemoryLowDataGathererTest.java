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
import org.gradle.util.JavaLangRuntimeAdapter;

import java.util.List;

/**
 * @author Tom Eyckmans
 */
public class ForkMemoryLowDataGathererTest {

    private JUnit4Mockery context = new JUnit4Mockery();

    private ForkMemoryLowDataGatherer dataGatherer;

    @Before
    public void setUp() throws Exception
    {
        context.setImposteriser(ClassImposteriser.INSTANCE);

        dataGatherer = new ForkMemoryLowDataGatherer(ReforkReasons.FORK_MEMORY_LOW);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullReforkReasonConstructorTest()
    {
        dataGatherer = new ForkMemoryLowDataGatherer(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullConfigureTest()
    {
        dataGatherer.configure(null);

        fail();
    }

    @Test
    public void reforkItemKeyTest()
    {
        final ReforkReasonKey itemKey = dataGatherer.getKey();

        assertEquals(ReforkReasons.FORK_MEMORY_LOW, itemKey);
    }

    @Test
    public void dataGatherMomentsTest()
    {
        final List<DataGatherMoment> moments = dataGatherer.getDataGatherMoments();

        assertNotNull(moments);
        assertEquals(1, moments.size());
        assertEquals(DataGatherMoment.AFTER_TEST_EXECUTION, moments.get(0));
    }

    @Test
    public void configureTest()
    {
        final ForkMemoryLowConfig config = context.mock(ForkMemoryLowConfig.class);

        context.checking(new Expectations(){{
            one(config).getMemoryLowThreshold();will(returnValue(ForkMemoryLowConfigTest.OK_MEMORY_LOW_THRESHOLD));
        }});

        dataGatherer.configure(config);

        assertEquals(ForkMemoryLowConfigTest.OK_MEMORY_LOW_THRESHOLD, dataGatherer.getMemoryLowThreshold(), 0);
    }

    @Test
    public void noneDataSendTriggeringProcessDataGatherMomentTest()
    {
        final ForkMemoryLowConfig config = context.mock(ForkMemoryLowConfig.class);

        context.checking(new Expectations(){{
            one(config).getMemoryLowThreshold();will(returnValue(50D));
        }});

        dataGatherer.configure(config);

        final JavaLangRuntimeAdapter runtimeAdapter = context.mock(JavaLangRuntimeAdapter.class);
        dataGatherer.setRuntimeAdapter(runtimeAdapter);
        
        final long freeMemory = 50L;
        final long maxMemory = 100L;
        final long totalMemory = 99L;

        context.checking(new Expectations(){{
            one(runtimeAdapter).getFreeMemory();will(returnValue(freeMemory));
            one(runtimeAdapter).getMaxMemory();will(returnValue(maxMemory));
            one(runtimeAdapter).getTotalMemory();will(returnValue(totalMemory));
        }});

        final boolean dataSendNeeded = dataGatherer.processDataGatherMoment(DataGatherMoment.AFTER_TEST_EXECUTION);

        assertFalse(dataSendNeeded);

        final ForkMemoryLowData currentData = (ForkMemoryLowData) dataGatherer.getCurrentData();

        assertEquals(freeMemory, currentData.getFreeMemory());
        assertEquals(maxMemory, currentData.getMaxMemory());
        assertEquals(totalMemory, currentData.getTotalMemory());
    }

    @Test
    public void dataTriggeringProcessDataGatherMomentTest()
    {
        final ForkMemoryLowConfig config = context.mock(ForkMemoryLowConfig.class);

        context.checking(new Expectations(){{
            one(config).getMemoryLowThreshold();will(returnValue(50D));
        }});

        dataGatherer.configure(config);

        final JavaLangRuntimeAdapter runtimeAdapter = context.mock(JavaLangRuntimeAdapter.class);
        dataGatherer.setRuntimeAdapter(runtimeAdapter);

        final long freeMemory = 49L;
        final long maxMemory = 100L;
        final long totalMemory = 99L;

        context.checking(new Expectations(){{
            one(runtimeAdapter).getFreeMemory();will(returnValue(freeMemory));
            one(runtimeAdapter).getMaxMemory();will(returnValue(maxMemory));
            one(runtimeAdapter).getTotalMemory();will(returnValue(totalMemory));
        }});

        final boolean dataSendNeeded = dataGatherer.processDataGatherMoment(DataGatherMoment.AFTER_TEST_EXECUTION);

        assertTrue(dataSendNeeded);

        final ForkMemoryLowData currentData = (ForkMemoryLowData) dataGatherer.getCurrentData();

        assertEquals(freeMemory, currentData.getFreeMemory());
        assertEquals(maxMemory, currentData.getMaxMemory());
        assertEquals(totalMemory, currentData.getTotalMemory());
    }

}
