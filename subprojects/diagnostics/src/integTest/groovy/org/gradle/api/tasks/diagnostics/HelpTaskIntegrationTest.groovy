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
        output.contains(toPlatformLineSeparators("""Detailed task description for dependencies

Path
     :dependencies

Type
     DependencyReportTask (class org.gradle.api.tasks.diagnostics.DependencyReportTask)

Description
     Displays all dependencies declared in root project '${testDirectory.getName()}'.


BUILD SUCCESSFUL"""))
    }

    def "multipleMatchingTasksOfSameType"() {
        when:
        run "help", "--task", "jar"
        then:
        output.contains(toPlatformLineSeparators("""Detailed task description for jar

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
        output.contains(toPlatformLineSeparators("""Detailed task description for someTask

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
}

