/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junit

import org.junit.runner.Description
import spock.lang.Specification

class JUnitTestEventAdapterTest extends Specification {
    def 'can recognize JUnit4 description #description'() {
        expect:
        JUnitTestEventAdapter.methodName(description) == methodName
        JUnitTestEventAdapter.className(description) == className

        where:
        description                 | className                   | methodName
        'ok(org.gradle.Test)'       | 'org.gradle.Test'           | 'ok'
        'ok(org.gradle.Test)[0]'    | 'org.gradle.Test'           | 'ok'
        'this is not a description' | 'this is not a description' | null
    }

    def 'recognises class-level descriptions in #scenario'() {
        expect:
        JUnitTestEventAdapter.isClassLevelDescription(description) == expected

        where:
        scenario                                      | description                                                              | expected
        'class-level with backing class (JUnit 4.6+)' | Description.createSuiteDescription(JUnitTestEventAdapterTest)            | true
        'method-level with backing class'             | Description.createTestDescription(JUnitTestEventAdapterTest, 'method')   | false
        'class-level fallback (no backing class)'     | Description.createSuiteDescription('com.example.LegacyRunnerSpec')       | true
        'method-level fallback (no backing class)'    | Description.createTestDescription('com.example.LegacyRunnerSpec', 'm')   | false
    }
}
