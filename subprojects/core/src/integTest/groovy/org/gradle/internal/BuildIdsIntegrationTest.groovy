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

package org.gradle.internal

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildIdsFixture
import org.junit.Rule

class BuildIdsIntegrationTest extends AbstractIntegrationSpec {

    // Note: this fixture implies some assertions that are important for this test
    @Rule
    public final BuildIdsFixture buildIdFixture = new BuildIdsFixture(executer, temporaryFolder)

    def "advertises build id"() {
        expect:
        succeeds "help"
    }

    def "advertises build in buildSrc"() {
        when:
        file("buildSrc/build.gradle").text = ""

        then:
        succeeds "help"
        buildIdFixture.lastBuildPaths() == [":", ":buildSrc"]
    }

    def "advertises build in composite participants"() {
        when:
        settingsFile << """
            includeBuild "a"
            includeBuild "b"
        """

        file("a/settings.gradle") << ""
        file("b/settings.gradle") << ""

        file("a/build.gradle") << "task t {}"
        file("a/buildSrc/build.gradle") << ""
        file("b/build.gradle") << "task t {}"
        file("b/buildSrc/build.gradle") << ""

        buildScript """
            task t {
                dependsOn gradle.includedBuild("a").task(":t")
                dependsOn gradle.includedBuild("b").task(":t")
            }
        """

        then:
        succeeds "t"
        buildIdFixture.lastBuildPaths() == [":", ":a", ":a:buildSrc", ":b", ":b:buildSrc"]
    }

    def "gradle launcher builds inherit build id"() {
        when:
        buildScript """
            task t(type: GradleBuild) {
                dir = file("other")
                startParameter.searchUpwards = false
            }
        """

        then:
        succeeds "t"
        buildIdFixture.lastBuildPaths() == [":", ":other"]
    }

    def "can access build ID from project"() {
        when:
        buildScript """
            println "buildId: " + gradle.buildIds.buildId
        """

        then:
        succeeds("help")

        and:
        output.contains("buildId: ${buildIdFixture.buildId}")
    }


    def "can access build ID from settings"() {
        when:
        settingsFile << """
            println "buildId: " + gradle.buildIds.buildId
        """

        then:
        succeeds("help")

        and:
        output.contains("buildId: ${buildIdFixture.buildId}")
    }
}
