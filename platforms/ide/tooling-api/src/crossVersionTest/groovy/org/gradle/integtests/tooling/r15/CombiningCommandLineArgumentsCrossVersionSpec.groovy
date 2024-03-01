/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.integtests.tooling.r15


import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import spock.lang.Issue

class CombiningCommandLineArgumentsCrossVersionSpec extends ToolingApiSpecification {

    @Issue("GRADLE-2635")
    def "can configure build file name and logging"() {
        file('buildX.gradle') << "logger.info('info message')"

        when:
        def out = withBuild { it.withArguments('-b', 'buildX.gradle', '-i') }.standardOutput

        then:
        out.contains('info message')
    }

    //below was working as expected
    //the test was added to validate the behavior that was questioned on forums
    def "supports gradle.properties and changed build file"() {
        file('gradle.properties') << "systemProp.foo=bar"
        file('buildX.gradle') << "logger.lifecycle('sys property: ' + System.properties['foo'])"

        when:
        def out = withBuild { it.withArguments('-b', 'buildX.gradle') }.standardOutput

        then:
        out.contains('sys property: bar')
    }
}
