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

package org.gradle.configuration

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationNotificationsFixture
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.test.fixtures.server.http.HttpServer
import org.junit.Rule

import static org.gradle.util.internal.TextUtil.normaliseFileSeparators

class ApplyScriptPluginBuildOperationIntegrationTest extends AbstractIntegrationSpec {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def "captures gradle script events"() {
        given:
        def otherScript2 = file("script2.gradle") << ""
        def otherScript1 = file("script1.gradle") << """
            apply from: "${normaliseFileSeparators(otherScript2.absolutePath)}"
        """
        def initScript = file("init.gradle") << """
            apply from: "${normaliseFileSeparators(otherScript1.absolutePath)}"
        """

        when:
        succeeds "help", "-I", initScript.absolutePath

        then:
        def ops = operations.all(ApplyScriptPluginBuildOperationType) {
            it.details.targetType == "gradle" && it.details.buildPath == ":" && !it.details.file.endsWith(BuildOperationNotificationsFixture.FIXTURE_SCRIPT_NAME)
        }
        ops.size() == 3

        ops[0].details.file == initScript.absolutePath
        ops[1].details.file == otherScript1.absolutePath
        ops[2].details.file == otherScript2.absolutePath

        operations.search(ops[0], ApplyScriptPluginBuildOperationType).size() == 2
        operations.search(ops[1], ApplyScriptPluginBuildOperationType).size() == 1
        operations.search(ops[2], ApplyScriptPluginBuildOperationType).size() == 0
    }

    def "captures settings script events"() {
        given:
        def otherScript2 = file("script2.gradle") << ""
        def otherScript1 = file("script1.gradle") << """
            apply from: "${normaliseFileSeparators(otherScript2.absolutePath)}"
        """
        settingsFile << """
            apply from: "${normaliseFileSeparators(otherScript1.absolutePath)}"
        """

        when:
        succeeds "help"

        then:
        def ops = operations.all(ApplyScriptPluginBuildOperationType) {
            it.details.targetType == "settings" && it.details.buildPath == ":"
        }
        ops.size() == 3

        ops[0].details.file == settingsFile.absolutePath
        ops[1].details.file == otherScript1.absolutePath
        ops[2].details.file == otherScript2.absolutePath

        operations.search(ops[0], ApplyScriptPluginBuildOperationType).size() == 2
        operations.search(ops[1], ApplyScriptPluginBuildOperationType).size() == 1
        operations.search(ops[2], ApplyScriptPluginBuildOperationType).size() == 0
    }

    def "identifies build of application target"() {
        given:
        def subBuildSettingsFile = file("subBuild/settings.gradle")
        subBuildSettingsFile << "rootProject.name = 'subBuild'"
        settingsFile << """
            includeBuild 'subBuild'
        """

        when:
        succeeds "help"

        then:
        def ops = operations.all(ApplyScriptPluginBuildOperationType) {
            it.details.targetType == "settings"
        }
        ops.size() == 2
        with(ops[0]) {
            details.file == settingsFile.absolutePath
            details.buildPath == ":"
        }
        with(ops[1]) {
            details.file == subBuildSettingsFile.absolutePath
            details.buildPath == ":subBuild"
        }
    }

    def "captures project script events"() {
        given:
        def otherScript2 = file("script2.gradle") << ""
        def otherScript1 = file("script1.gradle") << """
            apply from: "${normaliseFileSeparators(otherScript2.absolutePath)}"
        """
        buildFile << """
            apply from: "${normaliseFileSeparators(otherScript1.absolutePath)}"
        """

        when:
        succeeds "help"

        then:
        def ops = operations.all(ApplyScriptPluginBuildOperationType) {
            it.details.targetType == "project" &&
                it.details.buildPath == ":" &&
                it.details.targetPath == ":"
        }
        ops.size() == 3

        ops[0].details.file == buildFile.absolutePath
        ops[1].details.file == otherScript1.absolutePath
        ops[2].details.file == otherScript2.absolutePath

        operations.search(ops[0], ApplyScriptPluginBuildOperationType).size() == 2
        operations.search(ops[1], ApplyScriptPluginBuildOperationType).size() == 1
        operations.search(ops[2], ApplyScriptPluginBuildOperationType).size() == 0
    }

    def "captures for arbitrary targets"() {
        given:
        def script = file("script.gradle") << ""
        buildScript """
            apply from: "${normaliseFileSeparators(script.absolutePath)}", to: new Object() {}
        """

        when:
        succeeds "help"

        then:
        def op = operations.only(ApplyScriptPluginBuildOperationType) {
            it.details.targetType == null
        }

        op.details.file == script.absolutePath
        op.details.targetPath == null
        op.details.buildPath == null
    }

    @Rule
    HttpServer httpServer = new HttpServer()


    def "captures for http scripts"() {
        given:
        println testDirectory.absolutePath
        httpServer.start()
        def script = file("script.gradle") << ""
        httpServer.allowGetOrHead("/script.gradle", script)
        buildScript """
            apply from: "${httpServer.uri}/script.gradle"
        """

        when:
        succeeds "help"

        then:
        def op = operations.only(ApplyScriptPluginBuildOperationType) {
            it.details.targetType == "project" &&
                it.details.uri != null
        }

        op.details.file == null
        op.details.uri == "${httpServer.uri}/script.gradle"
        op.details.targetPath == ":"
        op.details.buildPath == ":"
    }

}
