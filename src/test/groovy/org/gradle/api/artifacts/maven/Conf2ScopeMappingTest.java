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

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.gradle.api.artifacts.Configuration;
import org.gradle.util.HelperUtil;

/**
 * @author Hans Dockter
 */
public class Conf2ScopeMappingTest {
    private Conf2ScopeMapping conf2ScopeMapping;
    private static final String TEST_SCOPE = "somescope";
    private static final Integer TEST_PRIORITY = 10;
    private static final Configuration TEST_CONF = HelperUtil.createConfiguration("someconf");

    @Before
    public void setUp() {
        conf2ScopeMapping = new Conf2ScopeMapping(TEST_PRIORITY, TEST_CONF, TEST_SCOPE);
    }

    @Test
    public void init() {
        assertEquals(TEST_PRIORITY, conf2ScopeMapping.getPriority());
        assertEquals(TEST_CONF, conf2ScopeMapping.getConfiguration());
        assertEquals(TEST_SCOPE, conf2ScopeMapping.getScope());
    }

    @Test
    public void equality() {
        assertTrue(conf2ScopeMapping.equals(new Conf2ScopeMapping(TEST_PRIORITY, TEST_CONF, TEST_SCOPE)));
        assertFalse(conf2ScopeMapping.equals(new Conf2ScopeMapping(TEST_PRIORITY + 10, TEST_CONF, TEST_SCOPE)));
    }

    @Test
    public void hashcode() {
        assertEquals(conf2ScopeMapping.hashCode(), new Conf2ScopeMapping(TEST_PRIORITY, TEST_CONF, TEST_SCOPE).hashCode());
    }
}
