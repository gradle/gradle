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
import org.jmock.lib.legacy.ClassImposteriser;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * @author Tom Eyckmans
 */
public class DefaultDataGatherControlInitialiserTest {

    private JUnit4Mockery context = new JUnit4Mockery();

    private DefaultDataGatherControlInitialiser initialiser;

    @Before
    public void setUp() throws Exception
    {
        context.setImposteriser(ClassImposteriser.INSTANCE);

        initialiser = new DefaultDataGatherControlInitialiser();
    }

    @Test ( expected = IllegalArgumentException.class )
    public void nullReforkReasonConfigsInitialize()
    {
        final DataGatherControl dataGatherControl = context.mock(DataGatherControl.class);

        initialiser.initialize(dataGatherControl, null);

        fail();
    }

    @Test ( expected = IllegalArgumentException.class )
    public void nullDataGatherControlInitialize()
    {
        final ReforkReasonConfigs reforkReasonConfig = context.mock(ReforkReasonConfigs.class);

        initialiser.initialize(null, reforkReasonConfig);

        fail();
    }

    @Test
    public void initialize()
    {
        final DataGatherControl dataGatherControl = context.mock(DataGatherControl.class);
        final ReforkReasonConfigs reforkReasonConfig = context.mock(ReforkReasonConfigs.class);
        final ReforkReasonRegisterAdapter reforkReasonRegisterAdapter = context.mock(ReforkReasonRegisterAdapter.class);

        // throws exception during configure
        final ReforkReason reasonOne = context.mock(ReforkReason.class, "one#reason");
        final ReforkReasonConfig configOne = context.mock(ReforkReasonConfig.class, "one#config");
        final ReforkReasonDataGatherer dataGathererOne = context.mock(ReforkReasonDataGatherer.class, "one#dataGatherer");
        // configures without errors
        final ReforkReason reasonTwo = context.mock(ReforkReason.class, "two#reason");
        final ReforkReasonConfig configTwo = context.mock(ReforkReasonConfig.class, "two#config");
        final ReforkReasonDataGatherer dataGathererTwo = context.mock(ReforkReasonDataGatherer.class, "two#dataGatherer");

        final Map<ReforkReasonKey, ReforkReasonConfig> reasonConfigs = new HashMap<ReforkReasonKey, ReforkReasonConfig>();
        reasonConfigs.put(TestReforkReasons.TEST_KEY_1, configOne);
        reasonConfigs.put(TestReforkReasons.TEST_KEY_2, configTwo);

        final List<ReforkReasonKey> reasonKeys = new ArrayList<ReforkReasonKey>();
        reasonKeys.add(TestReforkReasons.TEST_KEY_1);
        reasonKeys.add(TestReforkReasons.TEST_KEY_2);

        context.checking(new Expectations(){{
            one(reforkReasonConfig).getConfigs();will(returnValue(reasonConfigs));
            one(reforkReasonConfig).getKeys();will(returnValue(reasonKeys));

            one(reforkReasonRegisterAdapter).getReforkReason(TestReforkReasons.TEST_KEY_1);will(returnValue(reasonOne));
            one(reasonOne).getDataGatherer();will(returnValue(dataGathererOne));
            one(dataGathererOne).configure(configOne);will(throwException(new NullPointerException()));

            one(reforkReasonRegisterAdapter).getReforkReason(TestReforkReasons.TEST_KEY_2);will(returnValue(reasonTwo));
            one(reasonTwo).getDataGatherer();will(returnValue(dataGathererTwo));
            one(dataGathererTwo).configure(configTwo);
            one(dataGatherControl).addDataGatherer(dataGathererTwo);
        }});

        initialiser.setReforkReasonRegisterAdapter(reforkReasonRegisterAdapter);
        initialiser.initialize(dataGatherControl, reforkReasonConfig);
    }

}
