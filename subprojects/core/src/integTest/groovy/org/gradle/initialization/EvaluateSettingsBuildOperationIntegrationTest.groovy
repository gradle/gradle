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

package org.gradle.initialization

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.operations.trace.BuildOperationRecord

class EvaluateSettingsBuildOperationIntegrationTest extends AbstractIntegrationSpec {

    final buildOperations = new BuildOperationsFixture(executer, temporaryFolder)

    def "settings details are exposed"() {
        settingsFile << ""

        when:
        succeeds('help')

        then:
        operations().size() == 1
        operations()[0].details.settingsDir == settingsFile.parentFile.absolutePath
        operations()[0].details.settingsFile == settingsFile.absolutePath
    }

    def "settings with master folder are exposed"() {

        def customSettingsFile = file("master/settings.gradle")
        customSettingsFile << """
        includeFlat "a"
        """

        def projectDirectory = testDirectory.createDir("a")

        when:
        executer.withSearchUpwards()
        projectDir(projectDirectory)
        succeeds('help')

        then:
        operations()[0].details.settingsDir == customSettingsFile.parentFile.absolutePath
        operations()[0].details.settingsFile == customSettingsFile.absolutePath
    }

    def "settings set via cmdline flag are exposed"() {
        def customSettingsDir = file("custom")
        customSettingsDir.mkdirs()
        def customSettingsFile = new File(customSettingsDir, "settings.gradle")
        customSettingsFile << """
        
        include "a"
        """

        when:
        executer.withArguments("--settings-file", customSettingsFile.absolutePath)
        succeeds('help')

        then:
        operations()[0].details.settingsDir == customSettingsDir.absolutePath
        operations()[0].details.settingsFile == customSettingsFile.absolutePath
    }

    def "composite participants expose their settings details"() {
        settingsFile << """
            include "a"
            includeBuild "nested"
            
            rootProject.name = "root"
            rootProject.buildFileName = 'root.gradle'
            
        """

        def nestedSettingsFile = file("nested/settings.gradle")
        nestedSettingsFile << """
            rootProject.name = "nested"    
        """
        file("nested/build.gradle") << """
        group = "org.acme"
        version = "1.0"
        """

        when:
        succeeds('help')

        then:
        operations().size() == 2
        operations()[0].details.settingsDir == nestedSettingsFile.parentFile.absolutePath
        operations()[0].details.settingsFile == nestedSettingsFile.absolutePath

        operations()[1].details.settingsDir == settingsFile.parentFile.absolutePath
        operations()[1].details.settingsFile == settingsFile.absolutePath
    }

    private List<BuildOperationRecord> operations() {
        buildOperations.all(EvaluateSettingsBuildOperationType)
    }

}
