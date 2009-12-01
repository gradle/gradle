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
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.jmock.Expectations;

import java.util.List;

/**
 * @author Tom Eyckmans
 */
public class AmountOfTestCasesDataGathererTest {

    private JUnit4Mockery context = new JUnit4Mockery();

    private AmountOfTestCasesDataGatherer dataGatherer;

    @Before
    public void setUp() throws Exception
    {
        context.setImposteriser(ClassImposteriser.INSTANCE);

        dataGatherer = new AmountOfTestCasesDataGatherer(ReforkReasons.AMOUNT_OF_TESTCASES);
    }

    @Test ( expected = IllegalArgumentException.class)
    public void nullReforkReasonConstructorTest()
    {
        dataGatherer = new AmountOfTestCasesDataGatherer(null);

        fail();
    }

    @Test ( expected = IllegalArgumentException.class)
    public void nullConfigureTest()
    {
        dataGatherer.configure(null);
    }

    @Test
    public void reforkItemKeyTest()
    {
        final ReforkReasonKey itemKey = dataGatherer.getKey();

        assertEquals(ReforkReasons.AMOUNT_OF_TESTCASES, itemKey);
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
        final AmountOfTestCasesConfig config = context.mock(AmountOfTestCasesConfig.class);

        context.checking(new Expectations(){{
            one(config).getReforkEvery();will(returnValue(AmountOfTestCasesConfigTest.OK_REFORK_EVERY));
        }});

        dataGatherer.configure(config);

        assertEquals(AmountOfTestCasesConfigTest.OK_REFORK_EVERY, dataGatherer.getReforkEvery());
    }

    @Test
    public void noneDataSendTriggeringProcessDataGatherMomentTest()
    {
        final AmountOfTestCasesConfig config = context.mock(AmountOfTestCasesConfig.class);

        context.checking(new Expectations(){{
            one(config).getReforkEvery();will(returnValue(2L));
        }});

        dataGatherer.configure(config);

        final boolean dataSendNeeded = dataGatherer.processDataGatherMoment(DataGatherMoment.AFTER_TEST_EXECUTION);

        assertFalse(dataSendNeeded);
        assertEquals(1L, dataGatherer.getCurrentData().longValue());
    }

    @Test
    public void dataSendTriggeringProcessDataGatherMomentTest()
    {
        AmountOfTestCasesConfig config = new AmountOfTestCasesConfig(ReforkReasons.AMOUNT_OF_TESTCASES);
        config.setReforkEvery(1);

        dataGatherer.configure(config);

        final boolean dataSendNeeded = dataGatherer.processDataGatherMoment(DataGatherMoment.AFTER_TEST_EXECUTION);

        assertTrue(dataSendNeeded);
        assertEquals(1L, dataGatherer.getCurrentData().longValue());
    }


}
