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
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

class NameValidationIntegrationTest extends AbstractIntegrationSpec {

    def "project names must not contain forbidden characters"() {
        given:
        settingsFile << "rootProject.name = 'this::is::a::namespace'"
        buildFile << ""

        when:
        fails 'help'

        then:
        assertFailureContainsForbiddenCharacterMessage('project name', 'this::is::a::namespace',
            " Set the 'rootProject.name' or adjust the 'include' statement (see ${settingsDslUrl} for more details).")
    }

    def "subproject names must not contain forbidden characters"() {
        given:
        settingsFile << "include 'folder:name|with|pipes'"

        when:
        fails 'help'

        then:
        assertFailureContainsForbiddenCharacterMessage('project name', 'name|with|pipes',
            " Set the 'rootProject.name' or adjust the 'include' statement (see ${settingsDslUrl} for more details).")
    }

    private getSettingsDslUrl() {
        documentationRegistry.getDslRefForProperty("org.gradle.api.initialization.Settings", "include(java.lang.String[])")
    }

    def "task names must not contain forbidden characters"() {
        given:
        buildFile << "task 'this/is/a/hierarchy'"

        when:
        fails 'this/is/a/hierarchy'

        then:
        assertFailureContainsForbiddenCharacterMessage('task name',"this/is/a/hierarchy")
    }

    def "configuration names must not contain forbidden characters"() {
        given:
        buildFile << "configurations { 'some/really.\\\\strange name:' {} }"

        when:
        fails 'help'

        then:
        assertFailureContainsForbiddenCharacterMessage('Configuration name', "some/really.\\strange name:")
    }

    def "project names must not contain start with ."() {
        given:
        settingsFile << "rootProject.name = '.problematic-name'"
        buildFile << ""

        when:
        fails 'help'

        then:
        assertFailureContainsForbiddenStartOrEndCharacterMessage('project name', '.problematic-name',
            " Set the 'rootProject.name' or adjust the 'include' statement (see ${settingsDslUrl} for more details).")
    }

    def "project names must not end with ."() {
        given:
        settingsFile << "rootProject.name = 'problematic-name.'"
        buildFile << ""

        when:
        fails 'help'

        then:
        assertFailureContainsForbiddenStartOrEndCharacterMessage('project name', 'problematic-name.',
            " Set the 'rootProject.name' or adjust the 'include' statement (see ${settingsDslUrl} for more details).")
    }

    @Requires(UnitTestPreconditions.UnixDerivative) // all forbidden characters are illegal on Windows
    def "does not fail when project name overrides an invalid folder name"() {
        given:
        def buildFolder = file(".folder: name")
        inDirectory(buildFolder)
        buildFolder.file('settings.gradle') << "rootProject.name = 'customName'"
        buildFolder.file("build.gradle") << "println rootProject.name"

        when:
        succeeds 'help'

        then:
        output.contains("customName")
    }

    @Requires(UnitTestPreconditions.UnixDerivative) // all forbidden characters are illegal on Windows
    def "does not assign an invalid project name from folder names"() {
        given:
        def buildFolder = file(".folder: name.")
        inDirectory(buildFolder)
        buildFolder.file("build.gradle") << "println rootProject.name"

        when:
        fails 'help'

        then:
        assertFailureContainsForbiddenCharacterMessage('project name', '.folder: name.')
    }

    void assertFailureContainsForbiddenCharacterMessage(String nameDescription, String deprecatedName, String suggestion = '') {
        assertFailureDescriptionOrCauseContains("The $nameDescription '$deprecatedName' must not contain any of the following characters: [/, \\, :, <, >, \", ?, *, |].", suggestion)
    }

    void assertFailureContainsForbiddenStartOrEndCharacterMessage(String nameDescription, String deprecatedName, String suggestion = '') {
        assertFailureDescriptionOrCauseContains("The $nameDescription '$deprecatedName' must not start or end with a '.'.", suggestion)
    }

    void assertFailureDescriptionOrCauseContains(String... messages) {
        try {
            messages.each { failureDescriptionContains(it) }
        } catch (AssertionError ignore) {
            messages.each { failureCauseContains(it) }
        }
    }
}
