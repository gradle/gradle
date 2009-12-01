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
import java.util.Map;

/**
 * @author Tom Eyckmans
 */
public class ReforkItemConfigsTest {

    private JUnit4Mockery context = new JUnit4Mockery();

    private ReforkReasonConfigs reforkReasonConfigs;

    @Before
    public void setUp() throws Exception
    {
        reforkReasonConfigs = new ReforkReasonConfigs();
    }

    @Test
    public void defaultGetKeys()
    {
        final List<ReforkReasonKey> keys = reforkReasonConfigs.getKeys();

        assertNotNull(keys);
        assertTrue(keys.isEmpty());
    }

    @Test
    public void defaultGetConfigs()
    {
        final Map<ReforkReasonKey, ReforkReasonConfig> configs = reforkReasonConfigs.getConfigs();

        assertNotNull(configs);
        assertTrue(configs.isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void addNullReforkReasonConfig()
    {
        reforkReasonConfigs.addOrUpdateReforkReasonConfig(null);

        fail();
    }

    @Test(expected = IllegalArgumentException.class)
    public void addKeyLessReforkReasonConfig()
    {
        final ReforkReasonConfig config = context.mock(ReforkReasonConfig.class);

        context.checking(new Expectations(){{
            one(config).getKey();will(returnValue(null));
        }});

        reforkReasonConfigs.addOrUpdateReforkReasonConfig(config);

        fail();
    }

    @Test
    public void addOkReforkReasonConfig()
    {
        final ReforkReasonConfig config = context.mock(ReforkReasonConfig.class);

        context.checking(new Expectations(){{
            one(config).getKey();will(returnValue(TestReforkReasons.TEST_KEY_1));
        }});

        reforkReasonConfigs.addOrUpdateReforkReasonConfig(config);

        assertTrue(reforkReasonConfigs.getKeys().contains(TestReforkReasons.TEST_KEY_1));
        assertEquals(config, reforkReasonConfigs.getConfigs().get(TestReforkReasons.TEST_KEY_1));
    }

}
