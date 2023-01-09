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
import org.gradle.integtests.fixtures.OriginFixture
import org.gradle.integtests.fixtures.ScopeIdsFixture
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.junit.Rule

class IncrementalBuildOutputOriginIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    public final ScopeIdsFixture scopeIds = new ScopeIdsFixture(executer, temporaryFolder)

    @Rule
    public final OriginFixture originBuildInvocationId = new OriginFixture(executer, temporaryFolder)

    String getBuildInvocationId() {
        scopeIds.buildInvocationId.asString()
    }

    String originBuildInvocationId(String taskPath) {
        originBuildInvocationId.originId(taskPath)
    }

    def "exposes origin build id when reusing outputs"() {
        given:
        buildScript """
            def write = tasks.create("write", WriteProperties) {
                destinationFile = file("out.properties")
                properties = [v: 1]
            }
        """

        when:
        succeeds "write"

        then:
        executedAndNotSkipped ":write"
        def firstBuildId = buildInvocationId
        originBuildInvocationId(":write") == null

        when:
        succeeds "write"

        then:
        executed ":write"
        def secondBuildId = buildInvocationId
        firstBuildId != secondBuildId
        originBuildInvocationId(":write") == firstBuildId

        when:
        buildFile << """
            write.property("changed", "now")
        """
        succeeds "write"

        then:
        executedAndNotSkipped ":write"
        def thirdBuildId = buildInvocationId
        firstBuildId != thirdBuildId
        secondBuildId != thirdBuildId
        originBuildInvocationId(":write") == null

        when:
        succeeds "write"

        then:
        executed ":write"
        originBuildInvocationId(":write") == thirdBuildId
    }

    def "tracks different tasks"() {
        given:
        buildScript """
            def w1 = tasks.create("w1", WriteProperties) {
                destinationFile = file("w1.properties")
                properties = [v: 1]
            }
            def w2 = tasks.create("w2", WriteProperties) {
                destinationFile = file("w2.properties")
                properties = [v: 1]
            }

            tasks.create("w").dependsOn w1, w2
        """

        when:
        succeeds "w"
        def firstBuildId = buildInvocationId

        then:
        originBuildInvocationId(":w1") == null
        originBuildInvocationId(":w2") == null

        when:
        succeeds "w"

        then:
        originBuildInvocationId(":w1") == firstBuildId
        originBuildInvocationId(":w2") == firstBuildId

        when:
        buildFile << """
            w1.property("now", "changed")
        """
        succeeds "w"
        def secondBuildId = buildInvocationId

        then:
        originBuildInvocationId(":w1") == null
        originBuildInvocationId(":w2") == firstBuildId

        when:
        succeeds "w"

        then:
        originBuildInvocationId(":w1") == secondBuildId
        originBuildInvocationId(":w2") == firstBuildId
    }

    @UnsupportedWithConfigurationCache(because = "buildSrc is skipped")
    def "buildSrc tasks advertise build id"() {
        given:
        file("buildSrc/build.gradle").text = """
            tasks.create("w", WriteProperties) {
                destinationFile = file("w.properties")
                properties = [v: 1]
            }
            jar.dependsOn "w"
        """

        when:
        succeeds("help")
        def origin = getBuildInvocationId()

        then:
        originBuildInvocationId(":buildSrc:w") == null

        when:
        succeeds("help")

        then:
        originBuildInvocationId(":buildSrc:w") == origin
    }

    def "composite participant tasks advertise build id"() {
        given:
        ["a", "b"].each {
            file("$it/build.gradle").text = """
                tasks.create("w", WriteProperties) {
                    destinationFile = file("w.properties")
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
        def origin = getBuildInvocationId()
        succeeds("w")

        then:
        originBuildInvocationId(":a:w") == origin
        originBuildInvocationId(":b:w") == origin

        when:
        succeeds "w", "-p", "a"

        then:
        skipped(":w")
        originBuildInvocationId(":w") == origin
    }
}
