/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.testing.testsuites.transparency

class DefaultSuiteTransparencyIntegrationTest extends AbstractTestSuitesTransparencyIntegrationTest {
    def "default test suite transparency can NOT be changed to #level"() {
        buildFile << """
            testing.suites.test.transparencyLevel($level)
        """.stripIndent()

        expect: "the tests will fail"
        fails("test")
        result.assertHasErrorOutput("Can\'t set ${level - 'ProjectTransparencyLevel.'} transparency on the default test suite!")

        where:
        level << ['ProjectTransparencyLevel.PROJECT_CLASSES_ONLY', 'ProjectTransparencyLevel.CONSUMER', 'ProjectTransparencyLevel.TEST_CONSUMER', 'ProjectTransparencyLevel.INTERNAL_CONSUMER']
    }
}
