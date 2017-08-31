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

package org.gradle.api.reporting.internal

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class TaskGeneratedReportIntegrationTest extends AbstractIntegrationSpec {

    def "renders deprecation message when setting report destination as Object"() {
        given:
        buildFile << """
            apply plugin: 'java'
            
            test {
                reports.junitXml.destination = 'foo'
            }
        """

        when:
        executer.expectDeprecationWarning()
        succeeds('help')

        then:
        output.contains("The ConfigurableReport.setDestination(Object) method has been deprecated and is scheduled to be removed in Gradle 5.0. Please use the method ConfigurableReport.setDestination(File) instead.")
    }
}
