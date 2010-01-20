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
import org.gradle.api.testing.execution.PipelineConfig;

import java.util.List;

/**
 * @author Tom Eyckmans
 */
public class DefaultReforkControllerTest {

    private JUnit4Mockery context = new JUnit4Mockery();

    private DefaultReforkControl control;

    @Before
    public void setUp() throws Exception
    {
        context.setImposteriser(ClassImposteriser.INSTANCE);

        control = new DefaultReforkControl();
    }

    @Test ( expected = IllegalArgumentException.class )
    public void nullPipelineInitializationTest()
    {
        control.initialize(null);

        fail();
    }

    @Test
    public void okInitializationTest()
    {
        final QueueingPipeline pipeline = context.mock(QueueingPipeline.class);
        final PipelineConfig pipelineConfig = context.mock(PipelineConfig.class);
        final ReforkReasonConfigs reforkReasonConfigs = context.mock(ReforkReasonConfigs.class);
        final DefaultReforkControlInitialiser initialiser = context.mock(DefaultReforkControlInitialiser.class);

        context.checking(new Expectations(){{
            one(pipeline).getConfig();will(returnValue(pipelineConfig));
            one(pipelineConfig).getReforkReasonConfigs();will(returnValue(reforkReasonConfigs));
            one(initialiser).initialize(control, reforkReasonConfigs);
        }});

        control.setInitialiser(initialiser);
        control.initialize(pipeline);
    }

    @Test
    public void defaultGetReforkReasonKeys()
    {
        final List<ReforkReasonKey> keys = control.getReforkReasonKeys();

        assertNotNull(keys);
        assertTrue(keys.isEmpty());
    }

    @Test ( expected = IllegalArgumentException.class )
    public void setNullChecker()
    {
        control.setChecker(null);

        fail();
    }

    @Test
    public void setChecker()
    {
        final ReforkControlChecker checker = context.mock(ReforkControlChecker.class);

        control.setChecker(checker);
    }

    @Test ( expected = IllegalArgumentException.class )
    public void setNullInitialiser()
    {
        control.setInitialiser(null);

        fail();
    }

    @Test
    public void setInitialiser()
    {
        final DefaultReforkControlInitialiser initialiser = context.mock(DefaultReforkControlInitialiser.class);

        control.setInitialiser(initialiser);
    }

    @Test ( expected = IllegalArgumentException.class )
    public void addNullDataProcessor()
    {
        control.addDataProcessor(null);

        fail();
    }

    @Test
    public void addDataProcessor()
    {
        final ReforkReasonDataProcessor dataProcessor = context.mock(ReforkReasonDataProcessor.class);

        context.checking(new Expectations(){{
            one(dataProcessor).getKey();will(returnValue(TestReforkReasons.TEST_KEY_1));
        }});

        control.addDataProcessor(dataProcessor);

        final ReforkReasonDataProcessor retrievedDataProcessor = control.getDataProcessor(TestReforkReasons.TEST_KEY_1);

        assertEquals(dataProcessor, retrievedDataProcessor);
        assertTrue(control.getReforkReasonKeys().contains(TestReforkReasons.TEST_KEY_1));
    }

    @Test ( expected = IllegalArgumentException.class )
    public void nullReforkNeeded()
    {
        control.reforkNeeded(null);

        fail();
    }

    @Test
    public void reforkNeeded()
    {
        final ReforkContextData reforkContextData = context.mock(ReforkContextData.class);
        final ReforkControlChecker checker = context.mock(ReforkControlChecker.class);

        context.checking(new Expectations(){{
            one(checker).checkReforkNeeded(control, reforkContextData);
        }});

        control.setChecker(checker);
        control.reforkNeeded(reforkContextData);
    }

    @Test ( expected = IllegalArgumentException.class )
    public void nullGetDataProcessor()
    {
        control.getDataProcessor(null);

        fail();
    }
}
