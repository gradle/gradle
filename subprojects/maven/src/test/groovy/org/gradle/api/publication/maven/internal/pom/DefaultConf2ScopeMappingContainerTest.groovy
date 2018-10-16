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
package org.gradle.api.publication.maven.internal.pom

import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.maven.Conf2ScopeMapping
import spock.lang.Specification

import static java.util.Arrays.asList
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotSame

class DefaultConf2ScopeMappingContainerTest extends Specification {
    private DefaultConf2ScopeMappingContainer conf2ScopeMappingContainer
    Map<Configuration, Conf2ScopeMapping> testMappings
    private final Configuration testConf1 = Mock()
    private final Configuration testConf2 = Mock()
    private final Configuration testConf3 = Mock()
    private static final String TEST_SCOPE_1 = "test"
    private static final String TEST_SCOPE_2 = "test2"
    private static final int TEST_PRIORITY_1 = 10
    private static final int TEST_PRIORITY_2 = 20

    def setup() {
        conf2ScopeMappingContainer = new DefaultConf2ScopeMappingContainer()
        conf2ScopeMappingContainer.addMapping(TEST_PRIORITY_1, testConf1, TEST_SCOPE_1)

        testMappings = createTestMappings()
    }

    def init() {
        when:
        conf2ScopeMappingContainer = new DefaultConf2ScopeMappingContainer()

        then:
        conf2ScopeMappingContainer.skipUnmappedConfs
        conf2ScopeMappingContainer.mappings == [:]

        when:
        conf2ScopeMappingContainer = new DefaultConf2ScopeMappingContainer(testMappings)

        then:
        !conf2ScopeMappingContainer.mappings.is(testMappings)
        conf2ScopeMappingContainer.mappings == testMappings
        assertNotSame(testMappings, conf2ScopeMappingContainer.getMappings())
        assertEquals(testMappings, conf2ScopeMappingContainer.getMappings())
    }

    def equalsAndHashCode() {
        when:
        conf2ScopeMappingContainer = new DefaultConf2ScopeMappingContainer(testMappings)
        def other = new DefaultConf2ScopeMappingContainer(testMappings)

        then:
        conf2ScopeMappingContainer == other
        conf2ScopeMappingContainer.hashCode() == other.hashCode()

        when:
        conf2ScopeMappingContainer.addMapping(10, Mock(Configuration.class), "scope")

        then:
        conf2ScopeMappingContainer != other
    }

    private Map<Configuration, Conf2ScopeMapping> createTestMappings() {
        return [(testConf1): new Conf2ScopeMapping(10, testConf1, "scope")]
    }

    def testGetMapping() {
        def mapping1 = new Conf2ScopeMapping(TEST_PRIORITY_1, testConf1, TEST_SCOPE_1)
        expect:
        conf2ScopeMappingContainer.getMapping([testConf1]) == mapping1
        conf2ScopeMappingContainer.getMapping([testConf2]) == new Conf2ScopeMapping(null, testConf2, null)
        conf2ScopeMappingContainer.getMapping([testConf1, testConf2]) == mapping1
    }

    def mappingWithDifferentPrioritiesDifferentConfsDifferentScopes() {
        when:
        conf2ScopeMappingContainer.addMapping(TEST_PRIORITY_2, testConf2, TEST_SCOPE_2)

        then:
        conf2ScopeMappingContainer.getMapping([testConf1, testConf2]) == new Conf2ScopeMapping(TEST_PRIORITY_2, testConf2, TEST_SCOPE_2)
    }
    
    def mappingWithSamePrioritiesDifferentConfsSameScope() {
        when:
        conf2ScopeMappingContainer.addMapping(TEST_PRIORITY_1, testConf2, TEST_SCOPE_1)
        conf2ScopeMappingContainer.getMapping(asList(testConf1, testConf2))

        then:
        thrown(InvalidUserDataException)
    }

    def mappingWithSamePrioritiesDifferentConfsDifferentScopes() {
        when:
        conf2ScopeMappingContainer.addMapping(TEST_PRIORITY_1, testConf2, TEST_SCOPE_1)
        conf2ScopeMappingContainer.addMapping(TEST_PRIORITY_1, testConf3, TEST_SCOPE_2)
        conf2ScopeMappingContainer.getMapping(asList(testConf1, testConf2, testConf3))

        then:
        thrown(InvalidUserDataException)
    }
}
