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

class ProjectConfigureEventsErrorIntegrationTest extends AbstractIntegrationSpec {

    def "setup"() {
        settingsFile << "rootProject.name = 'projectConfigure'"
    }

    def "produces reasonable error message when Gradle.beforeProject closure fails"() {
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

    def "produces reasonable error message when Gradle.beforeProject action fails"() {
        when:
        settingsFile << """
    def action = {
        throw new RuntimeException("beforeProject failure")
    } as Action
    gradle.beforeProject(action)
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

    def "produces reasonable error message when Gradle.afterProject closure fails"() {
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

    def "produces reasonable error message when Gradle.afterProject action fails"() {
        when:
        settingsFile << """
    def action = {
        throw new RuntimeException("afterProject failure")
    } as Action
    gradle.afterProject(action)
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

    def "produces reasonable error message when ProjectEvaluationListener.beforeEvaluate fails"() {
        when:
        settingsFile << """
    class ListenerImpl implements ProjectEvaluationListener {
        void beforeEvaluate(Project project) {
            throw new RuntimeException("afterProject failure")
        }
        void afterEvaluate(Project project, ProjectState state) {}
    }
    gradle.addProjectEvaluationListener(new ListenerImpl())
"""
        buildFile << """
    task test
"""
        then:
        fails('test')
        failure.assertHasDescription("A problem occurred configuring root project 'projectConfigure'.")
                .assertHasCause("afterProject failure")
                .assertHasFileName("Settings file '${settingsFile.path}'")
                .assertHasLineNumber(4)
    }

    def "produces reasonable error message when ProjectEvaluationListener.afterEvaluate fails"() {
        when:
        settingsFile << """
    class ListenerImpl implements ProjectEvaluationListener {
        void beforeEvaluate(Project project) { }
        void afterEvaluate(Project project, ProjectState state) {
            throw new RuntimeException("afterProject failure")
        }
    }
    gradle.addProjectEvaluationListener(new ListenerImpl())
"""
        buildFile << """
    task test
"""
        then:
        fails('test')
        failure.assertHasDescription("A problem occurred configuring root project 'projectConfigure'.")
                .assertHasCause("afterProject failure")
                .assertHasFileName("Settings file '${settingsFile.path}'")
                .assertHasLineNumber(5)
    }

    def "produces reasonable error message when Project.afterEvaluate closure fails"() {
        when:
        buildFile """
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

    def "produces reasonable error message when Project.afterEvaluate action fails"() {
        when:
        buildFile """
    def action = {
        throw new RuntimeException("afterEvaluate failure")
    } as Action
    project.afterEvaluate(action)
    task test
"""
        then:
        fails('test')
        failure.assertHasDescription("A problem occurred configuring root project 'projectConfigure'.")
                .assertHasCause("afterEvaluate failure")
                .assertHasFileName("Build file '${buildFile.path}'")
                .assertHasLineNumber(3)
    }

    def "produces reasonable error message when both project configuration and Project.afterEvaluate action fails"() {
        when:
        buildFile """
    task test
    project.afterEvaluate {
        throw new RuntimeException("afterEvaluate failure")
    }
    throw new RuntimeException("configure")
"""
        then:
        fails('test')
        failure.assertHasFailures(2)
        failure.assertHasDescription("A problem occurred evaluating root project 'projectConfigure'.")
                .assertHasCause("configure")
                .assertHasFileName("Build file '${buildFile}'")
                .assertHasLineNumber(6)
        failure.assertHasDescription("A problem occurred configuring root project 'projectConfigure'.")
                .assertHasCause("afterEvaluate failure")
                .assertHasFileName("Build file '${buildFile}'")
                .assertHasLineNumber(4)
    }
}
