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
import spock.lang.Ignore

public class ProjectConfigureEventsErrorIntegrationTest extends AbstractIntegrationSpec {

    def "setup"() {
        settingsFile << "rootProject.name = 'projectConfigure'"
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
        failure.assertHasDescription("A problem occurred configuring root project 'projectConfigure'.")
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
        failure.assertHasDescription("A problem occurred configuring root project 'projectConfigure'.")
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
        failure.assertHasDescription("A problem occurred configuring root project 'projectConfigure'.")
                .assertHasCause("afterEvaluate failure")
                .assertHasFileName("Build file '${buildFile.path}'")
                .assertHasLineNumber(3)
    }

    def "produces reasonable error message when taskGraph.whenReady action fails"() {
        buildFile << """
    gradle.taskGraph.whenReady {
        throw new RuntimeException('broken closure')
    }
    task a
"""

        when:
        fails()

        then:
        failure.assertHasDescription("broken closure")
                .assertHasNoCause()
                .assertHasFileName("Build file '$buildFile'")
                .assertHasLineNumber(3);
    }

    @Ignore
    def "produces reasonable error message when task dependency closure throws exception"() {
        buildFile << """
    task a
    a.dependsOn {
        throw new RuntimeException('broken')
    }
"""
        when:
        fails "a"

        then:
        failure.assertHasDescription("Could not determine the dependencies of task ':a'.")
                .assertHasCause('broken')
                .assertHasFileName("Build file '$buildFile'")
                .assertHasLineNumber(4)
    }
}
