/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.reporting

import spock.lang.Specification

class ReportContainerTest extends Specification {
    
    Report createReport(String name) {
        new Report() {
            String getName() {
                name
            }
        }
    }

    ReportContainer createContainer(Report... reports) {
        new ReportContainer(reports)
    }

    def container = createContainer(createReport("a"), createReport("b"), createReport("c"))
    
    def "reports given at construction are available"() {
        when:
        container.configure { a { } }

        then:
        notThrown(MissingPropertyException)
    }

    def "container is immutable"() {
        when:
        container.add(createReport("d"))
        
        then:
        thrown(ReportContainer.ImmutableViolationException)
        
        when:
        container.clear()

        then:
        thrown(ReportContainer.ImmutableViolationException)
    }
}
