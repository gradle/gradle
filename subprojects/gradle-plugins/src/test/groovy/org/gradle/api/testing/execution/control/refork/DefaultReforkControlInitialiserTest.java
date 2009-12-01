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

import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;

/**
 * @author Tom Eyckmans
 */
public class DefaultReforkControlInitialiserTest {

    private JUnit4Mockery context = new JUnit4Mockery();

    private DefaultReforkControlInitialiser initialiser;

    @Before
    public void setUp() throws Exception
    {
        context.setImposteriser(ClassImposteriser.INSTANCE);

        initialiser = new DefaultReforkControlInitialiser();
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullSetReforkReasonRegisterAdapter()
    {
        initialiser.setReforkReasonRegisterAdapter(null);

        fail();
    }

    @Test
    public void setReforkReasonRegisterAdapter()
    {
        final ReforkReasonRegisterAdapter reforkReasonRegisterAdapter = context.mock(ReforkReasonRegisterAdapter.class);

        initialiser.setReforkReasonRegisterAdapter(reforkReasonRegisterAdapter);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullReforkControlInitialize()
    {
        final ReforkReasonConfigs configs = context.mock(ReforkReasonConfigs.class);

        initialiser.initialize(null, configs);

        fail();
    }

    @Test
    public void doNothingInitialize()
    {
        final ReforkControl reforkControl = context.mock(ReforkControl.class);

        initialiser.initialize(reforkControl, null);
    }

    @Test
    public void initializeWithFailures()
    {
        final ReforkControl reforkControl = context.mock(ReforkControl.class);
        final ReforkReasonConfigs configs = context.mock(ReforkReasonConfigs.class);

        final ReforkReasonRegisterAdapter reforkReasonRegisterAdapter = context.mock(ReforkReasonRegisterAdapter.class);

        // throws exception during configure
        final ReforkReason reasonOne = context.mock(ReforkReason.class, "one#reason");
        final ReforkReasonConfig configOne = context.mock(ReforkReasonConfig.class, "one#config");
        final ReforkReasonDataProcessor dataProcessorOne = context.mock(ReforkReasonDataProcessor.class, "one#dataProcessor");
        // configures without errors
        final ReforkReason reasonTwo = context.mock(ReforkReason.class, "two#reason");
        final ReforkReasonConfig configTwo = context.mock(ReforkReasonConfig.class, "two#config");
        final ReforkReasonDataProcessor dataProcessorTwo = context.mock(ReforkReasonDataProcessor.class, "two#dataProcessor");

        final Map<ReforkReasonKey, ReforkReasonConfig> reasonConfigs = new HashMap<ReforkReasonKey, ReforkReasonConfig>();
        reasonConfigs.put(TestReforkReasons.TEST_KEY_1, configOne);
        reasonConfigs.put(TestReforkReasons.TEST_KEY_2, configTwo);

        context.checking(new Expectations(){{
            one(configs).getConfigs();will(returnValue(reasonConfigs));
            one(configs).getKeys();will(returnValue(Arrays.asList(TestReforkReasons.TEST_KEY_1, TestReforkReasons.TEST_KEY_2)));

            one(reforkReasonRegisterAdapter).getReforkReason(TestReforkReasons.TEST_KEY_1);will(returnValue(reasonOne));
            one(reasonOne).getDataProcessor();will(returnValue(dataProcessorOne));
            one(dataProcessorOne).configure(configOne);will(throwException(new NullPointerException()));

            one(reforkReasonRegisterAdapter).getReforkReason(TestReforkReasons.TEST_KEY_2);will(returnValue(reasonTwo));
            one(reasonTwo).getDataProcessor();will(returnValue(dataProcessorTwo));
            one(dataProcessorTwo).configure(configTwo);
            one(reforkControl).addDataProcessor(dataProcessorTwo);
        }});

        initialiser.setReforkReasonRegisterAdapter(reforkReasonRegisterAdapter);
        initialiser.initialize(reforkControl, configs);
    }

}
