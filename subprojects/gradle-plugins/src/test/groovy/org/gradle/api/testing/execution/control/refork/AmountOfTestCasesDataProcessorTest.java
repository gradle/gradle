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
public class AmountOfTestCasesDataProcessorTest {

    private JUnit4Mockery context = new JUnit4Mockery();

    private AmountOfTestCasesDataProcessor dataProcessor;

    @Before
    public void setUp() throws Exception
    {
        context.setImposteriser(ClassImposteriser.INSTANCE);

        dataProcessor = new AmountOfTestCasesDataProcessor(ReforkReasons.AMOUNT_OF_TESTCASES);
    }

    @Test ( expected = IllegalArgumentException.class)
    public void nullConstructorTest()
    {
        dataProcessor = new AmountOfTestCasesDataProcessor(null);

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
        final AmountOfTestCasesConfig config = context.mock(AmountOfTestCasesConfig.class);

        context.checking(new Expectations(){{
            one(config).getReforkEvery();will(returnValue(AmountOfTestCasesConfigTest.OK_REFORK_EVERY));
        }});

        dataProcessor.configure(config);

        assertEquals(AmountOfTestCasesConfigTest.OK_REFORK_EVERY, dataProcessor.getReforkEvery());
    }

    @Test
    public void noneTriggeringDetermineReforkNeededTest()
    {
        final AmountOfTestCasesConfig config = context.mock(AmountOfTestCasesConfig.class);

        final Pipeline pipeline = context.mock(Pipeline.class);
        final int forkId = 1;

        context.checking(new Expectations(){{
            one(config).getReforkEvery();will(returnValue(AmountOfTestCasesConfigTest.OK_REFORK_EVERY));
        }});

        dataProcessor.configure(config);

        boolean restartNeeded = dataProcessor.determineReforkNeeded(pipeline, forkId, 1L);

        assertFalse(restartNeeded);
    }

    @Test
    public void triggeringDetermineReforkNeededTest()
    {
        final AmountOfTestCasesConfig config = context.mock(AmountOfTestCasesConfig.class);

        final Pipeline pipeline = context.mock(Pipeline.class);
        final int forkId = 1;

        context.checking(new Expectations(){{
            one(config).getReforkEvery();will(returnValue(AmountOfTestCasesConfigTest.OK_REFORK_EVERY));
        }});

        dataProcessor.configure(config);

        context.checking(new Expectations(){{
            one(pipeline).getName();will(returnValue("default"));
        }});

        boolean restartNeeded = dataProcessor.determineReforkNeeded(pipeline, forkId, AmountOfTestCasesConfigTest.OK_REFORK_EVERY);

        assertTrue(restartNeeded);
    }
}
