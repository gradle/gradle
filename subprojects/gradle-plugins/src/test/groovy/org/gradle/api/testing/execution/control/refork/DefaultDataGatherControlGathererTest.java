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

import java.util.Arrays;

/**
 * @author Tom Eyckmans
 */
public class DefaultDataGatherControlGathererTest {

    private JUnit4Mockery context = new JUnit4Mockery();

    private DefaultDataGatherControlGatherer gatherer;

    @Before
    public void setUp() throws Exception
    {
        context.setImposteriser(ClassImposteriser.INSTANCE);

        gatherer = new DefaultDataGatherControlGatherer();
    }

    @Test ( expected = IllegalArgumentException.class )
    public void nullDataGatherControlGatherData()
    {
        final ReforkContextData reforkContextData = context.mock(ReforkContextData.class);

        gatherer.gatherData(null, reforkContextData, DataGatherMoment.AFTER_TEST_EXECUTION);

        fail();
    }

    @Test ( expected = IllegalArgumentException.class )
    public void nullReforkNeededContextGatherData()
    {
        final DataGatherControl dataGatherControl = context.mock(DataGatherControl.class);

        gatherer.gatherData(dataGatherControl, null, DataGatherMoment.AFTER_TEST_EXECUTION);

        fail();
    }

    @Test ( expected = IllegalArgumentException.class )
    public void nullDataGatherMomentGatherData()
    {
        final DataGatherControl dataGatherControl = context.mock(DataGatherControl.class);
        final ReforkContextData reforkContextData = context.mock(ReforkContextData.class);

        gatherer.gatherData(dataGatherControl, reforkContextData, null);

        fail();
    }

    @Test
    public void gatherData()
    {
        final DataGatherControl dataGatherControl = context.mock(DataGatherControl.class);
        final ReforkContextData reforkContextData = context.mock(ReforkContextData.class);
        final DataGatherMoment moment = DataGatherMoment.AFTER_TEST_EXECUTION;

        final ReforkReasonDataGatherer dataGathererOne = context.mock(ReforkReasonDataGatherer.class, "one@dataGatherer");

        final ReforkReasonKey keyTwo = TestReforkReasons.TEST_KEY_2;
        final Long dataTwo = 2L;
        final ReforkReasonDataGatherer dataGathererTwo = context.mock(ReforkReasonDataGatherer.class, "two@dataGatherer");

        context.checking(new Expectations(){{
            one(dataGatherControl).getDataGatherers(moment);will(returnValue(Arrays.asList(dataGathererOne, dataGathererTwo)));

            one(dataGathererOne).processDataGatherMoment(moment);will(throwException(new NullPointerException()));

            one(dataGathererTwo).processDataGatherMoment(moment);will(returnValue(true));
            one(dataGathererTwo).getKey();will(returnValue(keyTwo));
            one(dataGathererTwo).getCurrentData();will(returnValue(dataTwo));
            one(reforkContextData).addReasonData(keyTwo, dataTwo);
        }});

        gatherer.gatherData(dataGatherControl, reforkContextData, moment);
    }
}
