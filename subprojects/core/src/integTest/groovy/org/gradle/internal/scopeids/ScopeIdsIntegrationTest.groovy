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

package org.gradle.internal.scopeids

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ScopeIdsFixture
import org.gradle.util.TextUtil
import org.junit.Rule

class ScopeIdsIntegrationTest extends AbstractIntegrationSpec {

    // Note: this fixture implies some assertions that are important for this test
    @Rule
    public final ScopeIdsFixture scopeIds = new ScopeIdsFixture(executer, temporaryFolder)

    def "advertises ids"() {
        expect:
        succeeds "help"
    }

    def "buildSrc inherits same ids"() {
        when:
        file("buildSrc/build.gradle").text = ""

        then:
        succeeds "help"
        scopeIds.lastBuildPaths() == [":", ":buildSrc"]
    }

    def "composite participants inherit the same ids"() {
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
        scopeIds.lastBuildPaths() == [":", ":a", ":a:buildSrc", ":b", ":b:buildSrc"]
    }

    def "gradle-build builds with different root does not inherit workspace id"() {
        given:
        // GradleBuild launched builds with a different root dir
        // are not considered to be of the same workspace
        scopeIds.disableConsistentWorkspaceIdCheck = true

        when:
        file("other/settings.gradle").touch()
        buildScript """
            task t(type: GradleBuild) {
                dir = file("other")
            }
        """

        then:
        succeeds "t"
        scopeIds.lastBuildPaths() == [":", ":other"]
        def ids = scopeIds.idsOfBuildTree(0)
        ids[":"].workspace != ids[":other"].workspace
    }

    def "gradle-build builds with different gradle user home does not inherit user id"() {
        given:
        scopeIds.disableConsistentUserIdCheck = true
        file("other-home/init.d/id.gradle") << scopeIds.initScriptContent()

        when:
        settingsFile << "rootProject.name = 'root'"
        buildScript """
            task t(type: GradleBuild) {
                startParameter.gradleUserHomeDir = new File("${TextUtil.normaliseFileSeparators(file("other-home").absolutePath)}")
            }
        """

        then:
        succeeds "t"
        def buildPaths = scopeIds.lastBuildPaths()
        buildPaths.size() == 2
        def ids = scopeIds.idsOfBuildTree(0)
        ids[":"].user != ids[buildPaths[1]].user
    }

    def "gradle-build with same root and user dir inherits all"() {
        when:
        settingsFile << "rootProject.name = 'root'"
        buildScript """
            task t(type: GradleBuild) {}
        """

        then:
        succeeds "t"
        def buildPaths = scopeIds.lastBuildPaths()
        buildPaths.size() == 2
    }

    def "different project roots have different workspace ids"() {
        when:
        file("a/settings.gradle") << ""
        file("b/settings.gradle") << ""
        succeeds("help", "-p", "a")
        succeeds("help", "-p", "b")

        then:
        scopeIds.workspaceIds.unique().size() == 2
        scopeIds.userIds.unique().size() == 1
    }

    def "changing gradle user home changes user id"() {
        when:
        file("build.gradle") << ""
        succeeds("help")
        file("g/init.d/i.gradle") << scopeIds.initScriptContent()
        executer.withGradleUserHomeDir(file("g"))
        succeeds("help")

        then:
        scopeIds.workspaceIds.unique().size() == 1
        scopeIds.userIds.unique().size() == 2
    }

}
