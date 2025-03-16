/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.performance.fixture

import org.gradle.performance.AbstractGradleVsMavenPerformanceTest

class IdProviderGradleVsMavenPerformanceTest extends AbstractGradleVsMavenPerformanceTest {

    def "if no test id is set, the test method name is used"() {
        when:
        runner.testGroup = "group"

        then:
        runner.testId == "if no test id is set, the test method name is used"
    }

    def "if test id is set, it is not replaced"() {
        when:
        runner.testGroup = "group"
        runner.testId = "Another id"

        then:
        runner.testId == "Another id"
    }
}
