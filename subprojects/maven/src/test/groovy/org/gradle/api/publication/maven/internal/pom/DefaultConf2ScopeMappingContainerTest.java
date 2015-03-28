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
package org.gradle.api.publication.maven.internal.pom;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.maven.Conf2ScopeMapping;
import org.gradle.util.JUnit4GroovyMockery;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashMap;
import java.util.Map;

import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.*;

@RunWith(JUnit4.class)
public class DefaultConf2ScopeMappingContainerTest {
    private final JUnit4Mockery context = new JUnit4GroovyMockery();
    private DefaultConf2ScopeMappingContainer conf2ScopeMappingContainer;
    private final Configuration testConf1 = context.mock(Configuration.class);
    private final Configuration testConf2 = context.mock(Configuration.class);
    private final Configuration testConf3 = context.mock(Configuration.class);
    private static final String TEST_SCOPE_1 = "test";
    private static final String TEST_SCOPE_2 = "test2";
    private static final int TEST_PRIORITY_1 = 10;
    private static final int TEST_PRIORITY_2 = 20;

    @Before
    public void setUp() {
        conf2ScopeMappingContainer = new DefaultConf2ScopeMappingContainer();
        conf2ScopeMappingContainer.addMapping(TEST_PRIORITY_1, testConf1, TEST_SCOPE_1);
    }

    @Test
    public void init() {
        conf2ScopeMappingContainer = new DefaultConf2ScopeMappingContainer();
        assertTrue(conf2ScopeMappingContainer.isSkipUnmappedConfs());
        assertEquals(0, conf2ScopeMappingContainer.getMappings().size());
        Map<Configuration, Conf2ScopeMapping> testMappings = createTestMappings();
        conf2ScopeMappingContainer = new DefaultConf2ScopeMappingContainer(testMappings);
        assertNotSame(testMappings, conf2ScopeMappingContainer.getMappings());
        assertEquals(testMappings, conf2ScopeMappingContainer.getMappings());
    }

    @Test
    public void equalsAndHashCode() {
        Map<Configuration, Conf2ScopeMapping> testMappings = createTestMappings();
        conf2ScopeMappingContainer = new DefaultConf2ScopeMappingContainer(testMappings);
        assertTrue(conf2ScopeMappingContainer.equals(new DefaultConf2ScopeMappingContainer(testMappings)));
        assertEquals(conf2ScopeMappingContainer.hashCode(), new DefaultConf2ScopeMappingContainer(testMappings).hashCode());
        conf2ScopeMappingContainer.addMapping(10, context.mock(Configuration.class), "scope");
        assertFalse(conf2ScopeMappingContainer.equals(new DefaultConf2ScopeMappingContainer(testMappings)));
    }

    private Map<Configuration, Conf2ScopeMapping> createTestMappings() {
        Map<Configuration, Conf2ScopeMapping> testMappings = new HashMap<Configuration, Conf2ScopeMapping>() {{
            Configuration configuration = context.mock(Configuration.class);
            put(configuration, new Conf2ScopeMapping(10, configuration, "scope"));
        }};
        return testMappings;
    }

    @Test
    public void addGetMapping() {
        assertEquals(new Conf2ScopeMapping(TEST_PRIORITY_1, testConf1, TEST_SCOPE_1),
                conf2ScopeMappingContainer.getMapping(asList(testConf1)));
    }

    @Test
    public void singleMappedConfiguration() {
        assertThat(conf2ScopeMappingContainer.getMapping(asList(testConf1)), equalTo(
                new Conf2ScopeMapping(TEST_PRIORITY_1, testConf1, TEST_SCOPE_1)));
    }

    @Test
    public void unmappedConfiguration() {
        assertThat(conf2ScopeMappingContainer.getMapping(asList(testConf2)), equalTo(
                new Conf2ScopeMapping(null, testConf2, null)));
    }

    @Test
    public void mappedConfigurationAndUnmappedConfiguration() {
        assertThat(conf2ScopeMappingContainer.getMapping(asList(testConf1, testConf2)), equalTo(
                new Conf2ScopeMapping(TEST_PRIORITY_1, testConf1, TEST_SCOPE_1)));
    }

    @Test
    public void mappingWithDifferentPrioritiesDifferentConfsDifferentScopes() {
        conf2ScopeMappingContainer.addMapping(TEST_PRIORITY_2, testConf2, TEST_SCOPE_2);
        assertThat(conf2ScopeMappingContainer.getMapping(asList(testConf1, testConf2)), equalTo(
                new Conf2ScopeMapping(TEST_PRIORITY_2, testConf2, TEST_SCOPE_2)));
    }
    
    @Test(expected = InvalidUserDataException.class)
    public void mappingWithSamePrioritiesDifferentConfsSameScope() {
        conf2ScopeMappingContainer.addMapping(TEST_PRIORITY_1, testConf2, TEST_SCOPE_1);
        conf2ScopeMappingContainer.getMapping(asList(testConf1, testConf2));
    }

    @Test(expected = InvalidUserDataException.class)
    public void mappingWithSamePrioritiesDifferentConfsDifferentScopes() {
        conf2ScopeMappingContainer.addMapping(TEST_PRIORITY_1, testConf2, TEST_SCOPE_1);
        conf2ScopeMappingContainer.addMapping(TEST_PRIORITY_1, testConf3, TEST_SCOPE_2);
        conf2ScopeMappingContainer.getMapping(asList(testConf1, testConf2, testConf3));
    }
}
