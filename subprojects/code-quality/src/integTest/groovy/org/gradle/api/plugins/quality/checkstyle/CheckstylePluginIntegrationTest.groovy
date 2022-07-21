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
package org.gradle.api.plugins.quality.checkstyle
import org.gradle.integtests.fixtures.WellBehavedPluginTest
import org.gradle.util.internal.ToBeImplemented
import spock.lang.Issue

class CheckstylePluginIntegrationTest extends WellBehavedPluginTest {
    @Override
    String getMainTask() {
        return "check"
    }

    def setup() {
        buildFile << """
            apply plugin: 'groovy'
        """
    }

    @Issue("https://github.com/gradle/gradle/issues/21301")
    @ToBeImplemented
    def "can pass a URL in configProperties"() {
        given:
        buildFile """
            apply plugin: 'checkstyle'

            dependencies { implementation localGroovy() }
            repositories { mavenCentral() }

            checkstyle {
                configProperties["some"] = new URL("https://gradle.org/")
            }
        """
        file('src/main/java/Some.java') << """
            public class Some {}
        """

        when:
        fails 'check'

        then:
        failureHasCause("Could not serialize unit of work.")
    }
}
