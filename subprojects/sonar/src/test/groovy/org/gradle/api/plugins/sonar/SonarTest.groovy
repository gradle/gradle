/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.plugins.sonar

import org.gradle.util.HelperUtil

import spock.lang.Specification
import spock.lang.Issue

class SonarTest extends Specification {
    Sonar task = HelperUtil.createTask(Sonar)

    @Issue("GRADLE-1499")
    def "can configure project properties"() {
        when:
        task.projectProperties = [one: "1", two: "2"]
        task.projectProperties three: "3", four: "4"
        task.projectProperty "five", "5"

        then:
        task.projectProperties == [one: "1", two: "2", three: "3", four: "4", five: "5"]
    }

    def "can configure global properties"() {
        when:
        task.globalProperties = [one: "1", two: "2"]
        task.globalProperties three: "3", four: "4"
        task.globalProperty "five", "5"

        then:
        task.globalProperties == [one: "1", two: "2", three: "3", four: "4", five: "5"]
    }
}
