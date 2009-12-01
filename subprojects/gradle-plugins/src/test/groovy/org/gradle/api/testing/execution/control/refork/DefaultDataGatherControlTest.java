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
public class DefaultDataGatherControlTest {

    private JUnit4Mockery context = new JUnit4Mockery();

    private DefaultDataGatherControl dataGatherControl;

    @Before
    public void setUp() throws Exception
    {
        context.setImposteriser(ClassImposteriser.INSTANCE);

        dataGatherControl = new DefaultDataGatherControl();
    }

    @Test ( expected = IllegalArgumentException.class )
    public void nullInitialize()
    {
        dataGatherControl.initialize(null);

        fail();
    }

    @Test
    public void initialize()
    {
        final DefaultMomentDataGatherers momentDataGatherers = context.mock(DefaultMomentDataGatherers.class);
        final DefaultDataGatherControlInitialiser initialiser = context.mock(DefaultDataGatherControlInitialiser.class);
        final ReforkReasonConfigs reforkReasonConfigs = context.mock(ReforkReasonConfigs.class);

        dataGatherControl.setMomentDataGatherers(momentDataGatherers);
        dataGatherControl.setInitialiser(initialiser);

        context.checking(new Expectations(){{
            one(momentDataGatherers).initialize();
            one(initialiser).initialize(dataGatherControl, reforkReasonConfigs);
        }});

        dataGatherControl.initialize(reforkReasonConfigs);
    }

    @Test ( expected = IllegalArgumentException.class )
    public void nullDataGatherMomentGatherData()
    {
        dataGatherControl.gatherData(null);

        fail();
    }

    @Test
    public void gatherData()
    {
        final DefaultDataGatherControlGatherer gatherer = context.mock(DefaultDataGatherControlGatherer.class);
        final Object[] momentData = new Object[]{1L};

        context.checking(new Expectations(){{
            one(gatherer).gatherData(with(same(dataGatherControl)), with(aNonNull(ReforkContextData.class)), with(same(DataGatherMoment.AFTER_TEST_EXECUTION)), with(same(momentData)));
        }});

        dataGatherControl.setGatherer(gatherer);
        dataGatherControl.gatherData(DataGatherMoment.AFTER_TEST_EXECUTION, momentData);
    }

    @Test
    public void addDataGatherer()
    {
        final DefaultMomentDataGatherers momentDataGatherers = context.mock(DefaultMomentDataGatherers.class);
        final ReforkReasonDataGatherer dataGatherer = context.mock(ReforkReasonDataGatherer.class);

        context.checking(new Expectations(){{
            one(momentDataGatherers).addDataGatherer(dataGatherer);
        }});

        dataGatherControl.setMomentDataGatherers(momentDataGatherers);
        dataGatherControl.addDataGatherer(dataGatherer);
    }

    @Test
    public void getDataGatherers()
    {
        final DefaultMomentDataGatherers momentDataGatherers = context.mock(DefaultMomentDataGatherers.class);

        context.checking(new Expectations(){{
            one(momentDataGatherers).getDataGatherers(DataGatherMoment.AFTER_TEST_EXECUTION);will(returnValue(null));
        }});

        dataGatherControl.setMomentDataGatherers(momentDataGatherers);
        final List<ReforkReasonDataGatherer> dataGatherers = dataGatherControl.getDataGatherers(DataGatherMoment.AFTER_TEST_EXECUTION);

        assertNull(dataGatherers);
    }
}
