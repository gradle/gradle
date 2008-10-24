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
package org.gradle.api.internal.dependencies.maven.dependencies;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.dependencies.maven.Conf2ScopeMapping;

import java.util.Map;
import java.util.HashMap;

/**
 * @author Hans Dockter
 */
public class DefaultConf2ScopeMappingContainerTest {
    private DefaultConf2ScopeMappingContainer conf2ScopeMappingContainer;
    private static final String TEST_CONF_1 = "testCompile";
    private static final String TEST_CONF_2 = "testCompile2";
    private static final String TEST_CONF_3 = "testCompile3";
    private static final String TEST_SCOPE_1 = "test";
    private static final String TEST_SCOPE_2 = "test2";
    private static final int TEST_PRIORITY_1 = 10;
    private static final int TEST_PRIORITY_2 = 20;

    @Before
    public void setUp() {
        conf2ScopeMappingContainer = new DefaultConf2ScopeMappingContainer();
        conf2ScopeMappingContainer.addMapping(TEST_PRIORITY_1, TEST_CONF_1, TEST_SCOPE_1);
    }

    @Test
    public void init() {
        conf2ScopeMappingContainer = new DefaultConf2ScopeMappingContainer();
        assertTrue(conf2ScopeMappingContainer.isSkipUnmappedConfs());
        assertEquals(0, conf2ScopeMappingContainer.getMappings().size());
        Map<String, Conf2ScopeMapping> testMappings = createTestMappings();
        conf2ScopeMappingContainer = new DefaultConf2ScopeMappingContainer(testMappings);
        assertNotSame(testMappings, conf2ScopeMappingContainer.getMappings());
        assertEquals(testMappings, conf2ScopeMappingContainer.getMappings());
    }

    @Test
    public void equalsAndHashCode() {
        Map<String, Conf2ScopeMapping> testMappings = createTestMappings();
        conf2ScopeMappingContainer = new DefaultConf2ScopeMappingContainer(testMappings);
        assertTrue(conf2ScopeMappingContainer.equals(new DefaultConf2ScopeMappingContainer(testMappings)));
        assertEquals(conf2ScopeMappingContainer.hashCode(), new DefaultConf2ScopeMappingContainer(testMappings).hashCode());
        conf2ScopeMappingContainer.addMapping(10, "conf", "scope");
        assertFalse(conf2ScopeMappingContainer.equals(new DefaultConf2ScopeMappingContainer(testMappings)));
    }

    private Map<String, Conf2ScopeMapping> createTestMappings() {
        Map<String, Conf2ScopeMapping> testMappings = new HashMap<String, Conf2ScopeMapping>() {{
            put("key", new Conf2ScopeMapping(10, "conf", "scope"));
        }};
        return testMappings;
    }

    @Test
    public void addGetMapping() {
        assertEquals(new Conf2ScopeMapping(TEST_PRIORITY_1, TEST_CONF_1, TEST_SCOPE_1),
                conf2ScopeMappingContainer.getMapping(TEST_CONF_1));
    }

    @Test
    public void singleMapping() {
        assertEquals(TEST_SCOPE_1, conf2ScopeMappingContainer.getScope(TEST_CONF_1));      
    }

    @Test
    public void unmappedConfiguration() {
        assertNull(conf2ScopeMappingContainer.getScope(TEST_CONF_2));      
    }

    @Test
    public void mappingWithDifferentPrioritiesDifferentConfsDifferentScopes() {
        conf2ScopeMappingContainer.addMapping(TEST_PRIORITY_2, TEST_CONF_2, TEST_SCOPE_2);
        assertEquals(TEST_SCOPE_2, conf2ScopeMappingContainer.getScope(TEST_CONF_1, TEST_CONF_2));
    }
    
    @Test
    public void mappingWithSamePrioritiesDifferentConfsSameScope() {
        conf2ScopeMappingContainer.addMapping(TEST_PRIORITY_1, TEST_CONF_2, TEST_SCOPE_1);
        assertEquals(TEST_SCOPE_1, conf2ScopeMappingContainer.getScope(TEST_CONF_1, TEST_CONF_2));
    }

    @Test(expected = InvalidUserDataException.class)
    public void mappingWithSamePrioritiesDifferentConfsDifferentScopes() {
        conf2ScopeMappingContainer.addMapping(TEST_PRIORITY_1, TEST_CONF_2, TEST_SCOPE_1);
        conf2ScopeMappingContainer.addMapping(TEST_PRIORITY_1, TEST_CONF_3, TEST_SCOPE_2);
        conf2ScopeMappingContainer.getScope(TEST_CONF_1, TEST_CONF_2, TEST_CONF_3);
    }
}
