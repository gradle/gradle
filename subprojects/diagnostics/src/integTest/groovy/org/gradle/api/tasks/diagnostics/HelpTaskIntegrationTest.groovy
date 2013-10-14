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
package org.gradle.api.tasks.diagnostics

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule

import static org.gradle.util.TextUtil.toPlatformLineSeparators

class HelpTaskIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    public final TestResources resources = new TestResources(temporaryFolder)

    def "can print help for implicit tasks"() {
        when:
        run "help", "--task", "dependencies"
        then:
        output.contains(toPlatformLineSeparators("""Detailed task information for dependencies

Path
     :dependencies

Type
     DependencyReportTask (class org.gradle.api.tasks.diagnostics.DependencyReportTask)

Description
     Displays all dependencies declared in root project '${testDirectory.getName()}'.


BUILD SUCCESSFUL"""))
    }

    def "can print help for placeholder added tasks"() {
        when:
        run "help", "--task", "help"
        then:
        output.contains(toPlatformLineSeparators("""Detailed task information for help

Path
     :help

Type
     Help (class org.gradle.configuration.Help)

Description
     Displays a help message


BUILD SUCCESSFUL"""))
    }

    def "help for tasks same type different descriptions"() {
        setup:
        settingsFile.text = """
include ":someproj"
"""
        buildFile.text = """
        task hello {
            description = "hello task from root"
        }
        project(":someproj"){
            task hello {
                description = "hello task from someproj"
            }
        }
"""
        when:
        run "help", "--task", "hello"
        then:
        output.contains(toPlatformLineSeparators("""Detailed task information for hello

Paths
     :hello
     :someproj:hello

Type
     DefaultTask (class org.gradle.api.DefaultTask)

Descriptions
     (:hello) hello task from root
     (:someproj:hello) hello task from someproj"""))
    }

    def "matchingTasksOfSameType"() {
        when:
        run "help", "--task", ":jar"
        then:
        output.contains(toPlatformLineSeparators("""Detailed task information for :jar

Path
     :jar

Type
     Jar (class org.gradle.api.tasks.bundling.Jar)

Description
     Assembles a jar archive containing the main classes.


BUILD SUCCESSFUL"""))

        when:
        run "help", "--task", "jar"
        then:
        output.contains(toPlatformLineSeparators("""Detailed task information for jar

Paths
     :jar
     :subproj1:jar

Type
     Jar (class org.gradle.api.tasks.bundling.Jar)

Description
     Assembles a jar archive containing the main classes.


BUILD SUCCESSFUL"""))

    }

    def "multipleMatchingTasksOfDifferentType"() {
        when:
        run "help", "--task", "someTask"
        then:
        output.contains(toPlatformLineSeparators("""Detailed task information for someTask

Path
     :subproj1:someTask

Type
     Copy (class org.gradle.api.tasks.Copy)

Description
     a copy operation

----------------------

Path
     :someTask

Type
     Jar (class org.gradle.api.tasks.bundling.Jar)

Description
     an archiving operation

----------------------

BUILD SUCCESSFUL"""))
    }

    def "error message contains possible candidates"() {
        buildFile.text = """
        task aTask
"""
        when:
        fails "help", "--task", "bTask"
        then:
        errorOutput.contains(" Task 'bTask' not found in root project '${testDirectory.getName()}'. Some candidates are: 'aTask', 'tasks'")
    }

    def "tasks can be defined by camelCase matching"() {
        buildFile.text = """
        task someCamelCaseTask{
            description = "a description"
        }"""
        when:
        run "help", "--task", "sCC"
        then:
        output.contains(toPlatformLineSeparators("""Detailed task information for sCC

Path
     :someCamelCaseTask

Type
     DefaultTask (class org.gradle.api.DefaultTask)

Description
     a description"""))

    }
}