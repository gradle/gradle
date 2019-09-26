/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.instantexecution

import org.gradle.testing.jacoco.plugins.fixtures.JavaProjectUnderTest

class InstantExecutionJacocoIntegrationTest extends AbstractInstantExecutionIntegrationTest {

    def "can use jacoco"() {

        given:
        new JavaProjectUnderTest(testDirectory).writeBuildScript().writeSourceFiles()
        def htmlReportDir = file("build/reports/jacoco/test/html")

        expect:
        instantRun 'test', 'jacocoTestReport'
        htmlReportDir.assertIsDir()

        when:
        succeeds 'clean'
        htmlReportDir.assertDoesNotExist()

        then:
        instantRun 'test', 'jacocoTestReport'
        htmlReportDir.assertIsDir()
    }
}
