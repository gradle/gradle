/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

public class ProjectConfigurationFailureIntegrationTest extends AbstractIntegrationSpec {

    def "setup"() {
        settingsFile << "rootProject.name = 'projectConfigure'"
    }

    def "produces reasonable error message when script evaluation fails"() {
        when:
        buildFile << """
    throw new RuntimeException("script failure")
    task test
"""
        then:
        fails('test')
        failure.assertHasDescription("A problem occurred evaluating root project 'projectConfigure'.")
                .assertHasCause("script failure")
                .assertHasFileName("Build file '${buildFile.path}'")
                .assertHasLineNumber(2)
    }

    def "produces reasonable error message when beforeProject action fails"() {
        when:
        settingsFile << """
    gradle.beforeProject {
        throw new RuntimeException("beforeProject failure")
    }
"""
        buildFile << """
    task test
"""
        then:
        fails('test')
        failure.assertHasDescription("An error occurred in a pre-configure action for root project 'projectConfigure'.")
                .assertHasCause("beforeProject failure")
                .assertHasFileName("Settings file '${settingsFile.path}'")
                .assertHasLineNumber(3)
    }

    def "produces reasonable error message when afterProject action fails"() {
        when:
        settingsFile << """
    gradle.afterProject {
        throw new RuntimeException("afterProject failure")
    }
"""
        buildFile << """
    task test
"""
        then:
        fails('test')
        failure.assertHasDescription("An error occurred in a post-configure action for root project 'projectConfigure'.")
                .assertHasCause("afterProject failure")
                .assertHasFileName("Settings file '${settingsFile.path}'")
                .assertHasLineNumber(3)
    }

    def "produces reasonable error message when afterEvaluate action fails"() {
        when:
        buildFile << """
    project.afterEvaluate {
        throw new RuntimeException("afterEvaluate failure")
    }
    task test
"""
        then:
        fails('test')
        failure.assertHasDescription("An error occurred in a post-configure action for root project 'projectConfigure'.")
                .assertHasCause("afterEvaluate failure")
                .assertHasFileName("Build file '${buildFile.path}'")
                .assertHasLineNumber(3)
    }
}
