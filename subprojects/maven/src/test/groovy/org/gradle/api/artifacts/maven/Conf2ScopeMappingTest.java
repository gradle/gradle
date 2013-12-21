/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.artifacts.maven;

import org.gradle.api.artifacts.Configuration;
import org.gradle.util.JUnit4GroovyMockery;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.*;

@RunWith(JUnit4.class)
public class Conf2ScopeMappingTest {
    private final JUnit4Mockery context = new JUnit4GroovyMockery();
    private Conf2ScopeMapping conf2ScopeMapping;
    private static final String TEST_SCOPE = "somescope";
    private static final Integer TEST_PRIORITY = 10;
    private final Configuration configuration = context.mock(Configuration.class);

    @Before
    public void setUp() {
        conf2ScopeMapping = new Conf2ScopeMapping(TEST_PRIORITY, configuration, TEST_SCOPE);
    }

    @Test
    public void init() {
        assertEquals(TEST_PRIORITY, conf2ScopeMapping.getPriority());
        assertEquals(configuration, conf2ScopeMapping.getConfiguration());
        assertEquals(TEST_SCOPE, conf2ScopeMapping.getScope());
    }

    @Test
    public void equality() {
        assertTrue(conf2ScopeMapping.equals(new Conf2ScopeMapping(TEST_PRIORITY, configuration, TEST_SCOPE)));
        assertFalse(conf2ScopeMapping.equals(new Conf2ScopeMapping(TEST_PRIORITY + 10, configuration, TEST_SCOPE)));
    }

    @Test
    public void hashcode() {
        assertEquals(conf2ScopeMapping.hashCode(), new Conf2ScopeMapping(TEST_PRIORITY, configuration, TEST_SCOPE).hashCode());
    }
}
