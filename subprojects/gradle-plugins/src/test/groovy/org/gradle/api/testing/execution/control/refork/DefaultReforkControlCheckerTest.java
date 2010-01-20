/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.testing.execution.QueueingPipeline;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.jmock.Expectations;
import org.gradle.api.testing.execution.Pipeline;

import java.util.List;
import java.util.ArrayList;

/**
 * @author Tom Eyckmans
 */
public class DefaultReforkControlCheckerTest {

    private JUnit4Mockery context = new JUnit4Mockery();

    private DefaultReforkControlChecker checker;

    @Before
    public void setUp() throws Exception
    {
        context.setImposteriser(ClassImposteriser.INSTANCE);

        checker = new DefaultReforkControlChecker();
    }

    @Test ( expected = IllegalArgumentException.class )
    public void nullReforkNeededContextCheck()
    {
        final ReforkControl control = context.mock(ReforkControl.class);

        checker.checkReforkNeeded(control, null);

        fail();
    }

    @Test ( expected = IllegalArgumentException.class )
    public void nullReforkControlCheck()
    {
        final ReforkContextData reforkContextData = context.mock(ReforkContextData.class);

        checker.checkReforkNeeded(null, reforkContextData);

        fail();
    }

    @Test
    public void checkWithFailures()
    {
        final ReforkControl control = context.mock(ReforkControl.class);
        final ReforkContextData reforkContextData = context.mock(ReforkContextData.class);

        final Pipeline pipeline = context.mock(QueueingPipeline.class);
        final int forkId = 1;

        // throws exception during check
        final Long dataOne = 1L;
        final ReforkReasonDataProcessor dataProcessorOne = context.mock(ReforkReasonDataProcessor.class, "one#dataProcessor");
        // checks without errors
        final Long dataTwo = 2L;
        final ReforkReasonDataProcessor dataProcessorTwo = context.mock(ReforkReasonDataProcessor.class, "two#dataProcessor");

        final List<ReforkReasonKey> reasonKeys = new ArrayList<ReforkReasonKey>();
        reasonKeys.add(TestReforkReasons.TEST_KEY_1);
        reasonKeys.add(TestReforkReasons.TEST_KEY_2);

        context.checking(new Expectations(){{
            one(reforkContextData).getPipeline();will(returnValue(pipeline));
            one(reforkContextData).getForkId();will(returnValue(forkId));
            one(control).getReforkReasonKeys();will(returnValue(reasonKeys));

            one(reforkContextData).getReasonData(TestReforkReasons.TEST_KEY_1);will(returnValue(dataOne));
            one(control).getDataProcessor(TestReforkReasons.TEST_KEY_1);will(returnValue(dataProcessorOne));
            one(dataProcessorOne).determineReforkNeeded(pipeline, forkId, dataOne);will(throwException(new NullPointerException()));

            one(reforkContextData).getReasonData(TestReforkReasons.TEST_KEY_2);will(returnValue(dataTwo));
            one(control).getDataProcessor(TestReforkReasons.TEST_KEY_2);will(returnValue(dataProcessorTwo));
            one(dataProcessorTwo).determineReforkNeeded(pipeline, forkId, dataTwo);will(returnValue(true));
        }});

        final boolean reforkNeeded = checker.checkReforkNeeded(control, reforkContextData);

        assertTrue(reforkNeeded);
    }
}
