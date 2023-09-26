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
        verifySettings(operation(), settingsFile)
        operation().details.buildPath == ":"
    }

    def "settings set via cmdline flag are exposed"() {
        createDirs("custom", "custom/a")
        def customSettingsDir = file("custom")
        def customSettingsFile = new File(customSettingsDir, "settings.gradle")
        customSettingsFile << """

        include "a"
        """

        when:
        executer.expectDocumentedDeprecationWarning("Specifying custom settings file location has been deprecated. This is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#configuring_custom_build_layout")
        executer.withArguments("--settings-file", customSettingsFile.absolutePath)
        succeeds('help')

        then:
        verifySettings(operation(), customSettingsFile)
        operation().details.buildPath == ":"
    }

    def "composite participants expose their settings details"() {
        createDirs("a", "nested")
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
        verifySettings(operations()[0], settingsFile)
        operations()[0].details.buildPath == ":"
        verifySettings(operations()[1], nestedSettingsFile)
        operations()[1].details.buildPath == ":nested"
    }

    def 'can configure feature preview in settings'() {
        given:
        settingsFile << '''
enableFeaturePreview('GROOVY_COMPILATION_AVOIDANCE')
'''
        expect:
        succeeds('help')
    }

    def 'can create project directories in afterEvaluate'() {
        given:
        settingsFile << '''
        include 'has-no-dir'
        def collectChildren(def obj) {
            [obj] + obj.getChildren().collectMany { collectChildren(it) }
        }
        gradle.settingsEvaluated { settings ->
            collectChildren(settings.rootProject).each { project ->
                project.projectDir.mkdirs()
            }
        }
        '''
        expect:
        succeeds(':has-no-dir:help')
    }

    private List<BuildOperationRecord> operations() {
        buildOperations.all(EvaluateSettingsBuildOperationType)
    }

    private BuildOperationRecord operation() {
        assert operations().size() == 1
        operations()[0]
    }

    private void verifySettings(BuildOperationRecord operation, File settingsFile) {
        assert operation.details.settingsDir == settingsFile.parentFile.absolutePath
        assert operation.details.settingsFile == settingsFile.absolutePath
    }

}
