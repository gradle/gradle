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

import org.gradle.util.GradleVersion

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

// https://plugins.gradle.org/plugin/org.samples.greeting
// Plugin is used by the GE build.
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

        expectDeprecationWarnings(
            result,
            "Property 'greeting' is not annotated with an input or output annotation. " +
                "This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0. " +
                "Execution optimizations are disabled due to the failed validation. " +
                "See https://docs.gradle.org/${GradleVersion.current().version}/userguide/more_about_tasks.html#sec:up_to_date_checks for more details."
        )
    }
}
