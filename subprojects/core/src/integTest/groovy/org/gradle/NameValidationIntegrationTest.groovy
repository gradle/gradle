/*
 * Copyright 2017 the original author or authors.
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

package org.gradle

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import static org.gradle.internal.os.OperatingSystem.WINDOWS
import static org.gradle.internal.os.OperatingSystem.current

class NameValidationIntegrationTest extends AbstractIntegrationSpec {

    def "project names should not contain forbidden characters"() {
        when:
        settingsFile << "rootProject.name = 'this::is::a::namespace'"
        buildFile << ""

        then:
        executer.expectDeprecationWarning()
        succeeds 'help'
        assertPrintsCorrectDeprecationMessage('this::is::a::namespace')
    }

    def "subproject names should not contain forbidden characters"() {
        when:
        settingsFile << "include 'folder:name with spaces'"

        then:
        executer.expectDeprecationWarning()
        succeeds 'help'
        assertPrintsCorrectDeprecationMessage('name with spaces')
     }

    def "task names should not contain forbidden characters"() {
        when:
        buildFile << "task 'this/is/a/hierarchy'"

        then:
        executer.expectDeprecationWarning()
        succeeds 'this/is/a/hierarchy'
        assertPrintsCorrectDeprecationMessage("this/is/a/hierarchy")
    }

    def "configuration names should not contain forbidden characters"() {
        when:
        buildFile << "configurations { 'some/really.\\\\strange name:' {} }"

        then:
        executer.expectDeprecationWarning()
        succeeds 'help'
        assertPrintsCorrectDeprecationMessage("some/really.\\strange name:")
    }

    def "does not assign an invalid project name from folder name"() {
        given:
        def buildFolder = file(current() == WINDOWS ? "folder  name" : "folder: name")
        inDirectory(buildFolder)

        when:
        buildFolder.file("build.gradle") << "println rootProject.name"

        then:
        succeeds 'help'
        output.contains("folder__name")
    }

    def assertPrintsCorrectDeprecationMessage(String deprecatedName) {
        output.contains("The name '$deprecatedName' contains at least one of the following characters: [ , /, \\, :]. This has been deprecated and is scheduled to be removed in Gradle 5.0")
    }
}
