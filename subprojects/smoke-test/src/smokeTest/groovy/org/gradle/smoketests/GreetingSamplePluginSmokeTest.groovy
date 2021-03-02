/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.smoketests


import spock.lang.Ignore

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
// https://plugins.gradle.org/plugin/org.samples.greeting
// Plugin is used by the GE build.
@Ignore("https://github.com/gradle/gradle/issues/16243")
class GreetingSamplePluginSmokeTest extends AbstractSmokeTest {
    def 'greeting plugin'() {
        given:
        buildFile << """
            plugins {
                id 'org.samples.greeting' version '1.0'
            }
        """.stripIndent()

        when:
        def result = runner('hello').forwardOutput().build()

        then:
        result.task(':hello').outcome == SUCCESS
    }
}
