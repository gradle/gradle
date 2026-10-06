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
import spock.lang.Issue

import static org.gradle.integtests.fixtures.TestableBuildOperationRecord.buildOp

class EvaluateSettingsBuildOperationIntegrationTest extends AbstractIntegrationSpec {

    final buildOperations = new BuildOperationsFixture(executer, temporaryFolder)

    def "settings details are exposed"() {
        settingsFile << ""

        when:
        succeeds('help')

        then:
        def loadOps = buildOperations.all(LoadBuildBuildOperationType)
        def evaluationOps = buildOperations.all(EvaluateSettingsBuildOperationType)
        evaluationOps == [
            buildOp(details: [settingsDir: settingsFile.parentFile.absolutePath, settingsFile: settingsFile.absolutePath, buildPath: ":"], displayName: "Evaluate settings", parent: loadOps[0])
        ]
    }

    @Issue("https://github.com/gradle/gradle/issues/38623")
    def "settings are evaluated once when the build root is given explicitly and the root project is relocated"() {
        settingsFile << """
            rootProject.projectDir = file('bar')
        """
        file("bar/build.gradle") << ""

        when:
        executer.withArgument("-p").withArgument(testDirectory.absolutePath)
        succeeds('help')

        then:
        buildOperations.all(LoadBuildBuildOperationType).size() == 1
        buildOperations.all(EvaluateSettingsBuildOperationType).size() == 1
    }

    @Issue("https://github.com/gradle/gradle/issues/38623")
    def "settings details are exposed when a buildSrc directory is the project directory"() {
        settingsFile << ""
        def buildSrc = file("buildSrc")
        buildSrc.file("build.gradle") << ""

        when:
        executer.withArgument("-p").withArgument(buildSrc.absolutePath)
        succeeds('help')

        then:
        def loadOps = buildOperations.all(LoadBuildBuildOperationType)
        def evaluationOps = buildOperations.all(EvaluateSettingsBuildOperationType)
        evaluationOps == [
            buildOp(details: [settingsDir: buildSrc.absolutePath, settingsFile: buildSrc.file("settings.gradle").absolutePath, buildPath: ":"], displayName: "Evaluate settings", parent: loadOps[0])
        ]
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
        def loadOps = buildOperations.all(LoadBuildBuildOperationType)
        def evaluationOps = buildOperations.all(EvaluateSettingsBuildOperationType)
        evaluationOps == [
            buildOp(details: [settingsDir: settingsFile.parentFile.absolutePath, settingsFile: settingsFile.absolutePath, buildPath: ":"], displayName: "Evaluate settings", parent: loadOps[0]),
            buildOp(details: [settingsDir: nestedSettingsFile.parentFile.absolutePath, settingsFile: nestedSettingsFile.absolutePath, buildPath: ":nested"], displayName: "Evaluate settings (:nested)", parent: loadOps[1])
        ]
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

}
