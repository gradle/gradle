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
     DependencyReportTask (org.gradle.api.tasks.diagnostics.DependencyReportTask)

Options
     --configuration     The configuration to generate the report for.

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
     Help (org.gradle.configuration.Help)

Options
     --task     The task, detailed help is requested for.

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
     Task (org.gradle.api.Task)

Descriptions
     (:hello) hello task from root
     (:someproj:hello) hello task from someproj"""))
    }


    def "matchingTasksOfSameType"() {
        setup:
        settingsFile << "include ':subproj1'"
        buildFile << "allprojects{ apply plugin:'java'}"
        when:
        run "help", "--task", ":jar"
        then:
        output.contains(toPlatformLineSeparators("""Detailed task information for :jar

Path
     :jar

Type
     Jar (org.gradle.api.tasks.bundling.Jar)

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
     Jar (org.gradle.api.tasks.bundling.Jar)

Description
     Assembles a jar archive containing the main classes.


BUILD SUCCESSFUL"""))

    }

    def "multipleMatchingTasksOfDifferentType"() {
        setup:
        settingsFile << "include ':subproj1'"
        buildFile << """task someTask(type:Jar){
            description = "an archiving operation"
        }

        project(":subproj1"){
            task someTask(type:Copy){
                description = "a copy operation"
            }
        }"""

        when:
        run "help", "--task", "someTask"
        then:
        output.contains(toPlatformLineSeparators("""Detailed task information for someTask

Path
     :subproj1:someTask

Type
     Copy (org.gradle.api.tasks.Copy)

Description
     a copy operation

----------------------

Path
     :someTask

Type
     Jar (org.gradle.api.tasks.bundling.Jar)

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
     Task (org.gradle.api.Task)

Description
     a description"""))

    }

    def "prints hint when using invalid commandlineoptions"() {
        when:
        fails "help", "--tasssk", "help"

        then:
        failure.assertHasDescription("Problem configuring task :help from command line.")
        failure.assertHasCause("Unknown command-line option '--tasssk'.")
        failure.assertHasResolution("Run gradle help --task :help to get task usage details. Run with --info or --debug option to get more log output.")
    }

    def "listsEnumAndBooleanCmdOptionValues"() {
        when:
        run "help", "--task", "hello"
        then:
        output.contains(toPlatformLineSeparators("""Detailed task information for hello

Paths
     :hello
     :proj1:hello
     :proj2:hello

Type
     CustomTask (CustomTask)

Options
     --booleanValue     Configures a boolean flag in CustomTask.

     --enumValue     Configures an enum value in CustomTask.
                     Available values are:
                          ABC
                          DEF
                          GHIJKL

Description
     -"""))
    }

    def "listsCommonDynamicAvailableValues"() {
        when:
        run "help", "--task", "hello"
        then:
        output.contains(toPlatformLineSeparators("""Detailed task information for hello

Paths
     :sub1:hello
     :sub2:hello

Type
     CustomTask (CustomTask)

Options
     --stringValue     Configures a string value in CustomTask.
                       Available values are:
                            optionA
                            optionB

Description
     -"""))
    }
}