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
package org.gradle.api.artifacts.maven

import org.gradle.api.artifacts.Configuration
import spock.lang.Specification

class Conf2ScopeMappingTest extends Specification {
    private Conf2ScopeMapping conf2ScopeMapping
    private static final String TEST_SCOPE = "somescope"
    private static final Integer TEST_PRIORITY = 10
    private final Configuration configuration = Mock()

    def setup() {
        conf2ScopeMapping = new Conf2ScopeMapping(TEST_PRIORITY, configuration, TEST_SCOPE)
    }

    def init() {
        expect:
        conf2ScopeMapping.priority == TEST_PRIORITY
        conf2ScopeMapping.configuration == configuration
        conf2ScopeMapping.scope == TEST_SCOPE
    }

    def equality() {
        expect:
        conf2ScopeMapping == new Conf2ScopeMapping(TEST_PRIORITY, configuration, TEST_SCOPE)
        conf2ScopeMapping != new Conf2ScopeMapping(TEST_PRIORITY + 10, configuration, TEST_SCOPE)
    }
}
