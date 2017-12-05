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
import org.gradle.util.GradleVersion
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

class NameValidationIntegrationTest extends AbstractIntegrationSpec {

    def "project names should not contain forbidden characters"() {
        given:
        settingsFile << "rootProject.name = 'this::is::a::namespace'"
        buildFile << ""
        executer.expectDeprecationWarning()

        when:
        succeeds 'help'

        then:
        assertPrintsForbiddenCharacterDeprecationMessage('project name', 'this::is::a::namespace',
            " Set the 'rootProject.name' or adjust the 'include' statement (see https://docs.gradle.org/${GradleVersion.current().version}/dsl/org.gradle.api.initialization.Settings.html#org.gradle.api.initialization.Settings:include(java.lang.String[]) for more details).")
    }

    def "subproject names should not contain forbidden characters"() {
        given:
        settingsFile << "include 'folder:name with spaces'"
        executer.expectDeprecationWarning()

        when:
        succeeds 'help'

        then:
        assertPrintsForbiddenCharacterDeprecationMessage('project name', 'name with spaces',
            " Set the 'rootProject.name' or adjust the 'include' statement (see https://docs.gradle.org/${GradleVersion.current().version}/dsl/org.gradle.api.initialization.Settings.html#org.gradle.api.initialization.Settings:include(java.lang.String[]) for more details).")
    }

    def "task names should not contain forbidden characters"() {
        given:
        buildFile << "task 'this/is/a/hierarchy'"
        executer.expectDeprecationWarning()

        when:
        succeeds 'this/is/a/hierarchy'

        then:
        assertPrintsForbiddenCharacterDeprecationMessage('task name', "this/is/a/hierarchy")
    }

    def "configuration names should not contain forbidden characters"() {
        given:
        buildFile << "configurations { 'some/really.\\\\strange name:' {} }"
        executer.expectDeprecationWarning()

        when:
        succeeds 'help'

        then:
        assertPrintsForbiddenCharacterDeprecationMessage('name', "some/really.\\strange name:")
    }

    def "project names should not contain start with ."() {
        given:
        settingsFile << "rootProject.name = '.problematic-name'"
        buildFile << ""
        executer.expectDeprecationWarning()

        when:
        succeeds 'help'

        then:
        assertPrintsForbiddenStartOrEndCharacterDeprecationMessage('project name', '.problematic-name',
            " Set the 'rootProject.name' or adjust the 'include' statement (see https://docs.gradle.org/${GradleVersion.current().version}/dsl/org.gradle.api.initialization.Settings.html#org.gradle.api.initialization.Settings:include(java.lang.String[]) for more details).")
    }

    def "project names should not end with ."() {
        given:
        settingsFile << "rootProject.name = 'problematic-name.'"
        buildFile << ""
        executer.expectDeprecationWarning()

        when:
        succeeds 'help'

        then:
        assertPrintsForbiddenStartOrEndCharacterDeprecationMessage('project name', 'problematic-name.',
            " Set the 'rootProject.name' or adjust the 'include' statement (see https://docs.gradle.org/${GradleVersion.current().version}/dsl/org.gradle.api.initialization.Settings.html#org.gradle.api.initialization.Settings:include(java.lang.String[]) for more details).")
    }

    def "does not assign an invalid project name from folder names"() {
        given:
        executer.expectDeprecationWarning()
        def buildFolder = file(".folder  name")
        inDirectory(buildFolder)
        buildFolder.file("build.gradle") << "println rootProject.name"

        when:
        succeeds 'help'

        then:
        //output.contains("_folder__name")
        assertPrintsForbiddenCharacterDeprecationMessage('project name', '.folder  name',
            " Set the 'rootProject.name' or adjust the 'include' statement (see https://docs.gradle.org/${GradleVersion.current().version}/dsl/org.gradle.api.initialization.Settings.html#org.gradle.api.initialization.Settings:include(java.lang.String[]) for more details).")
    }

    def "does not print deprecation warning when project name overrides an invalid folder name"() {
        given:
        def buildFolder = file(".folder  name")
        inDirectory(buildFolder)
        buildFolder.file('settings.gradle') << "rootProject.name = 'customName'"
        buildFolder.file("build.gradle") << "println rootProject.name"

        when:
        succeeds 'help'

        then:
        output.contains("customName")
    }

    @Requires(TestPrecondition.UNIX_DERIVATIVE)
    def "does not assign an invalid project name from unix folder names"() {
        given:
        executer.expectDeprecationWarning()
        def buildFolder = file(".folder: name.")
        inDirectory(buildFolder)
        buildFolder.file("build.gradle") << "println rootProject.name"

        when:
        succeeds 'help'

        then:
        //output.contains("_folder__name_")
        assertPrintsForbiddenCharacterDeprecationMessage('project name', '.folder: name.')
    }

    void assertPrintsForbiddenCharacterDeprecationMessage(String nameDescription, String deprecatedName, String suggestion = '') {
        assert result.deprecationReport.contains("The $nameDescription '$deprecatedName' contains at least one of the following characters: [ , /, \\, :, <, >, \", ?, *, |]. This has been deprecated and is scheduled to be removed in Gradle 5.0.$suggestion")
    }

    void assertPrintsForbiddenStartOrEndCharacterDeprecationMessage(String nameDescription, String deprecatedName, String suggestion = '') {
        assert result.deprecationReport.contains("The $nameDescription '$deprecatedName' starts or ends with a '.'. This has been deprecated and is scheduled to be removed in Gradle 5.0.$suggestion")
    }
}
