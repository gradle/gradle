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
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import org.gradle.api.testing.execution.Pipeline;
import org.apache.commons.lang.SerializationUtils;

import java.util.List;
import java.util.Map;

/**
 * @author Tom Eyckmans
 */
public class DefaultReforkContextDataTest {

    private JUnit4Mockery context = new JUnit4Mockery();

    private DefaultReforkContextData contextData;

    @Before
    public void setUp() throws Exception
    {
        context.setImposteriser(ClassImposteriser.INSTANCE);

        contextData = new DefaultReforkContextData();
    }

    @Test
    public void defaultReasonKeys()
    {
        final List<ReforkReasonKey> reasonKeys = contextData.getReasonKeys();

        assertNotNull(reasonKeys);
        assertTrue(reasonKeys.isEmpty());
        assertTrue(contextData.isEmpty());
    }

    @Test
    public void defaultReasonData()
    {
        final Map<ReforkReasonKey, Object> reasonData = contextData.getReasonData();

        assertNotNull(reasonData);
        assertTrue(reasonData.isEmpty());
        assertTrue(contextData.isEmpty());
    }

    @Test ( expected = IllegalArgumentException.class )
    public void addNullReasonKeyReasonData()
    {
        contextData.addReasonData(null, 1L);

        fail();
    }

    @Test ( expected = IllegalArgumentException.class )
    public void getNullReasonKeyReasonData()
    {
        contextData.getReasonData(null);

        fail();
    }

    @Test
    public void addReasonData()
    {
        final ReforkReasonKey reasonKey = TestReforkReasons.TEST_KEY_1;
        final Long reasonData = 1L;

        contextData.addReasonData(reasonKey, reasonData);

        assertTrue(contextData.getReasonKeys().contains(reasonKey));
        assertEquals(reasonData, contextData.getReasonData(reasonKey));
    }

    @Test
    public void pipeline()
    {
        final Pipeline pipeline = context.mock(QueueingPipeline.class);

        contextData.setPipeline(pipeline);

        assertEquals(pipeline, contextData.getPipeline());
    }

    @Test
    public void forkId()
    {
        final int forkId = 1;

        contextData.setForkId(forkId);

        assertEquals(forkId, contextData.getForkId());
    }

    @Test
    public void serializationTest()
    {
        final ReforkReasonKey reasonKey = TestReforkReasons.TEST_KEY_1;
        final Long reasonData = 1L;
        final Pipeline pipeline = context.mock(QueueingPipeline.class);
        final int forkId = 1;

        contextData.setPipeline(pipeline);
        contextData.setForkId(forkId);
        contextData.addReasonData(reasonKey, reasonData);

        final byte[] objectBytes = SerializationUtils.serialize(contextData);

        final ReforkContextData deserialized = (ReforkContextData) SerializationUtils.deserialize(objectBytes);

        assertNull(deserialized.getPipeline());
        assertFalse(deserialized.getForkId() == forkId);
        assertEquals(reasonData, deserialized.getReasonData(reasonKey));
    }

}
