/*
 * Copyright 2026 the original author or authors.
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

class UnknownProjectSuggestionsIntegrationTest extends AbstractIntegrationSpec {

    def "suggests close project paths for absolute project() in build script"() {
        createDirs("app", "app/child", "lib")
        settingsFile << "include 'app', 'app:child', 'lib'"
        buildFile << "project(':app:chld')"
        def message = "Project with path ':app:chld' could not be found in root project '${testDirectory.name}'. Some candidates are: ':app:child'."

        expect:
        fails("help")
        failureCauseContains(message)
        failureCauseContains("Project not found")
    }

    def "suggests close project paths for relative project() in build script"() {
        createDirs("app", "app/child", "lib")
        settingsFile << "include 'app', 'app:child', 'lib'"
        file("app/build.gradle") << "project('chld')"
        def message = "Project with path 'chld' could not be found in project ':app'. Some candidates are: ':app:child'."

        expect:
        fails("help")
        failureCauseContains(message)
        failureCauseContains("Project not found")
    }

    def "does not suggest distant project paths for project() in build script"() {
        createDirs("app")
        settingsFile << "include 'app'"
        buildFile << "project(':unrelated')"
        def message = "Project with path ':unrelated' could not be found in root project '${testDirectory.name}'."

        expect:
        fails("help")
        failureCauseContains(message)
        failure.assertNotOutput("Some candidates are")
        failureCauseContains("Project not found")
    }

    def "suggests close project paths for project() in settings"() {
        createDirs("foo")
        settingsFile << """
            include 'foo'
            project(':fooo')
        """
        def message = "Project with path ':fooo' could not be found. Some candidates are: ':foo'."

        expect:
        fails("help")
        failureCauseContains(message)
        failureCauseContains("Project not found")
    }

    def "suggests absolute path for relative project() in settings"() {
        createDirs("foo")
        settingsFile << """
            include 'foo'
            project('foo')
        """
        def message = "Project with path 'foo' could not be found. Some candidates are: ':foo'."

        expect:
        fails("help")
        failureCauseContains(message)
        failureCauseContains("Project not found")
    }

    def "recovered lookup of an unknown project leaves no problem"() {
        enableProblemsApiCheck()
        createDirs("app")
        settingsFile << """
            include 'app'
            try {
                project(':ap')
            } catch (UnknownProjectException e) {
                println("recovered in settings")
            }
        """
        buildFile << """
            try {
                project(':ap')
            } catch (UnknownProjectException e) {
                println("recovered in build script")
            }
            try {
                dependencies.project(path: ':ap')
            } catch (UnknownProjectException e) {
                println("recovered in dependencies")
            }
        """

        expect:
        succeeds("help")
        outputContains("recovered in settings")
        outputContains("recovered in build script")
        outputContains("recovered in dependencies")
        receivedProblems.empty
    }
}
