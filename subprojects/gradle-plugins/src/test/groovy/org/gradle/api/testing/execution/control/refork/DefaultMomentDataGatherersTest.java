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
import org.jmock.Expectations;

import java.util.List;
import java.util.Arrays;

/**
 * @author Tom Eyckmans
 */
public class DefaultMomentDataGatherersTest {

    private JUnit4Mockery context = new JUnit4Mockery();

    private DefaultMomentDataGatherers momentDataGatherers;

    @Before
    public void setUp() throws Exception
    {
        momentDataGatherers = new DefaultMomentDataGatherers();
        momentDataGatherers.initialize();
    }

    @Test
    public void defaultMomentDataGatherers()
    {
        for ( final DataGatherMoment moment : DataGatherMoment.values() ) {
            final List<ReforkReasonDataGatherer> momentDataGatherers = this.momentDataGatherers.getDataGatherers(moment);

            assertNotNull(momentDataGatherers);
            assertTrue(momentDataGatherers.isEmpty());
        }
    }

    @Test ( expected = IllegalArgumentException.class )
    public void addNullDataGatherer()
    {
        momentDataGatherers.addDataGatherer(null);

        fail();
    }

    @Test
    public void addDataGatherer()
    {
        final ReforkReasonDataGatherer dataGatherer = context.mock(ReforkReasonDataGatherer.class);

        context.checking(new Expectations(){{
            one(dataGatherer).getDataGatherMoments();will(returnValue(Arrays.asList(DataGatherMoment.AFTER_TEST_EXECUTION)));
        }});

        momentDataGatherers.addDataGatherer(dataGatherer);

        final List<ReforkReasonDataGatherer> momentDataGatherers = this.momentDataGatherers.getDataGatherers(DataGatherMoment.AFTER_TEST_EXECUTION);

        assertNotNull(momentDataGatherers);
        assertTrue(momentDataGatherers.contains(dataGatherer));
    }

    @Test ( expected = IllegalArgumentException.class )
    public void getNullDataGatherMomentDataGatherers()
    {
        momentDataGatherers.getDataGatherers(null);

        fail();
    }

    @Test
    public void getDataGatherers()
    {
        final List<ReforkReasonDataGatherer> momentDataGatherers = this.momentDataGatherers.getDataGatherers(DataGatherMoment.AFTER_TEST_EXECUTION);

        assertNotNull(momentDataGatherers);
        assertTrue(momentDataGatherers.isEmpty());
    }
}
