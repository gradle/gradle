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

import org.gradle.caching.internal.origin.OriginMetadata
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildCacheKeyFixture
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.integtests.fixtures.OriginFixture
import org.gradle.integtests.fixtures.ScopeIdsFixture
import org.junit.Rule

class BuildCacheOutputOriginIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture {

    @Rule
    public final ScopeIdsFixture scopeIds = new ScopeIdsFixture(executer, temporaryFolder)

    @Rule
    public final OriginFixture outputOrigin = new OriginFixture(executer, temporaryFolder)

    @Delegate
    @Rule
    public final BuildCacheKeyFixture buildCacheKeyFixture = new BuildCacheKeyFixture(executer, temporaryFolder)

    String getBuildInvocationId() {
        scopeIds.buildInvocationId.asString()
    }

    String originBuildInvocationId(String taskPath) {
        outputOrigin.originId(taskPath)
    }

    OriginMetadata origin(String taskPath) {
        outputOrigin.origin(taskPath)
    }

    def setup() {
        executer.beforeExecute {
            it.withBuildCacheEnabled()
        }
    }

    def "exposes origin build id when reusing cached outputs"() {
        given:
        buildScript """
            apply plugin: "base"
            def write = tasks.create("write", WriteProperties) {
                destinationFile = file("build/out.properties")
                properties = [v: 1]
            }
        """

        when:
        succeeds "write"

        then:
        executedAndNotSkipped ":write"
        def firstBuildId = buildInvocationId
        def firstBuildCacheKey = buildCacheKey(":write")
        originBuildInvocationId(":write") == null

        when:
        succeeds "clean", "write"

        then:
        skipped ":write"
        def secondBuildId = buildInvocationId
        firstBuildId != secondBuildId
        with(origin(":write")) {
            buildInvocationId == firstBuildId
            buildCacheKey == firstBuildCacheKey
        }

        when:
        buildFile << """
            write.property("changed", "now")
        """
        succeeds "clean", "write"

        then:
        executedAndNotSkipped ":write"
        def thirdBuildId = buildInvocationId
        def thirdBuildCacheKey = buildCacheKey(":write")
        firstBuildId != thirdBuildId
        secondBuildId != thirdBuildId
        thirdBuildCacheKey != firstBuildCacheKey
        originBuildInvocationId(":write") == null

        when:
        succeeds "clean", "write"

        then:
        skipped ":write"
        with(origin(":write")) {
            buildInvocationId == thirdBuildId
            buildCacheKey == thirdBuildCacheKey
        }

        when:
        succeeds  "write"

        then:
        skipped ":write"
        originBuildInvocationId(":write") == thirdBuildId

        when:
        succeeds  "write"

        then:
        skipped ":write"
        originBuildInvocationId(":write") == thirdBuildId

        when:
        succeeds  "clean", "write"

        then:
        skipped ":write"
        originBuildInvocationId(":write") == thirdBuildId
    }

}
