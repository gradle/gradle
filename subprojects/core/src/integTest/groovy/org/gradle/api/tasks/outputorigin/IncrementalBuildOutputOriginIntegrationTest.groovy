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

package org.gradle.api.tasks.outputorigin

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ScopeIdsFixture
import org.gradle.integtests.fixtures.TaskOutputOriginBuildIdFixture
import org.gradle.internal.id.UniqueId
import org.junit.Rule

class IncrementalBuildOutputOriginIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    public final ScopeIdsFixture buildIdFixture = new ScopeIdsFixture(executer, temporaryFolder)

    @Rule
    public final TaskOutputOriginBuildIdFixture originBuildIdFixture = new TaskOutputOriginBuildIdFixture(executer, temporaryFolder)

    UniqueId getBuildId() {
        buildIdFixture.buildId
    }

    UniqueId originBuildId(String taskPath) {
        originBuildIdFixture.originId(taskPath)
    }

    def "exposes origin build id when reusing outputs"() {
        given:
        buildScript """
            def write = tasks.create("write", WriteProperties) {
                outputFile = "out.properties"
                properties = [v: 1]
            }
        """

        when:
        succeeds "write"

        then:
        executedAndNotSkipped ":write"
        def firstBuildId = buildId
        originBuildId(":write") == null

        when:
        succeeds "write"

        then:
        executed ":write"
        def secondBuildId = buildId
        firstBuildId != secondBuildId
        originBuildId(":write") == firstBuildId

        when:
        buildFile << """
            write.property("changed", "now")
        """
        succeeds "write"

        then:
        executedAndNotSkipped ":write"
        def thirdBuildId = buildId
        firstBuildId != thirdBuildId
        secondBuildId != thirdBuildId
        originBuildId(":write") == null

        when:
        succeeds "write"

        then:
        executed ":write"
        originBuildId(":write") == thirdBuildId
    }

    def "tracks different tasks"() {
        given:
        buildScript """
            def w1 = tasks.create("w1", WriteProperties) {
                outputFile = "w1.properties"
                properties = [v: 1]
            }
            def w2 = tasks.create("w2", WriteProperties) {
                outputFile = "w2.properties"
                properties = [v: 1]
            }
            
            tasks.create("w").dependsOn w1, w2
        """

        when:
        succeeds "w"
        def firstBuildId = buildId

        then:
        originBuildId(":w1") == null
        originBuildId(":w2") == null

        when:
        succeeds "w"

        then:
        originBuildId(":w1") == firstBuildId
        originBuildId(":w2") == firstBuildId

        when:
        buildFile << """
            w1.property("now", "changed")
        """
        succeeds "w"
        def secondBuildId = buildId

        then:
        originBuildId(":w1") == null
        originBuildId(":w2") == firstBuildId

        when:
        succeeds "w"

        then:
        originBuildId(":w1") == secondBuildId
        originBuildId(":w2") == firstBuildId
    }

    def "buildSrc tasks advertise build id"() {
        given:
        file("buildSrc/build.gradle").text = """
            tasks.create("w", WriteProperties) {
                outputFile = "w.properties"
                properties = [v: 1]
            }
            build.dependsOn "w"
        """

        when:
        succeeds("help")
        def origin = getBuildId()

        then:
        originBuildId(":buildSrc:w") == null

        when:
        succeeds("help")

        then:
        originBuildId(":buildSrc:w") == origin
    }

    def "composite participant tasks advertise build id"() {
        given:
        ["a", "b"].each {
            file("$it/build.gradle").text = """
                tasks.create("w", WriteProperties) {
                    outputFile = "w.properties"
                    properties = [v: 1]
                }
            """
            file("$it/settings.gradle") << ""
        }

        settingsFile << """
            includeBuild "a"
            includeBuild "b"
        """

        buildScript """
            tasks.create("w").dependsOn gradle.includedBuild("a").task(":w"), gradle.includedBuild("b").task(":w")
        """

        when:
        succeeds("w")
        def origin = getBuildId()
        succeeds("w")

        then:
        originBuildId(":a:w") == origin
        originBuildId(":b:w") == origin

        when:
        succeeds "w", "-p", "a"

        then:
        skipped(":w")
        originBuildId(":w") == origin
    }
}
