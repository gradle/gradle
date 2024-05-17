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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

class ProjectHierarchyCustomizationIntegrationTest extends AbstractIntegrationSpec {
    @Issue("https://github.com/gradle/gradle/issues/18726")
    def "can customize name of child project before customizing the name of parent project"() {
        createDirs("modules", "modules/projectA", "modules/projectA/projectB")
        settingsFile << """
            include("modules:projectA:projectB")

            project(':modules:projectA:projectB').name = 'project-b'
            project(':modules:projectA').name = 'project-a'
        """

        // This tests current behaviour, not desired behaviour
        buildFile << """
            project(":modules:project-a") {
                task test { }
            }
            // should be 'project-a', not 'projectA'
            project(":modules:projectA:project-b") {
                task test { }
            }
        """

        when:
        run("test")

        then:
        result.assertTasksExecuted(":modules:project-a:test", ":modules:projectA:project-b:test")
    }
}
