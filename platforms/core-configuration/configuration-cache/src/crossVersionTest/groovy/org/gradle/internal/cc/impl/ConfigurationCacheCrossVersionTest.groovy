/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.cc.impl

import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.CrossVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetVersions
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.util.GradleVersion

import javax.annotation.Nullable

import static org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheOption.LONG_OPTION

@TargetVersions("6.5-rc-1+")
class ConfigurationCacheCrossVersionTest extends CrossVersionIntegrationSpec {

    GradleExecuter previousExecuter
    BuildOperationsFixture previousFixture

    GradleExecuter currentExecuter
    BuildOperationsFixture currentFixture

    def setup() {
        previousExecuter = version(previous)
        previousFixture = new BuildOperationsFixture(previousExecuter, temporaryFolder)

        currentExecuter = version(current)
        currentFixture = new BuildOperationsFixture(currentExecuter, temporaryFolder)
    }

    void runPrevious() {
        previousExecuter.withArguments(argFor(previous.version), 'help').run()
    }

    void runCurrent() {
        currentExecuter.withArguments(argFor(current.version), 'help').run()
    }

    private static String argFor(GradleVersion version) {
        version.baseVersion < GradleVersion.version("6.6")
            ? "--${LONG_OPTION}=on"
            : "--$LONG_OPTION"
    }

    def "does not reuse cached state from previous version"() {
        when:
        runPrevious()

        then:
        assertStateStored(previousFixture)

        when:
        runCurrent()

        then:
        assertStateStored(currentFixture)
    }

    def "does not reuse cached state from future version"() {
        when:
        runCurrent()

        then:
        assertStateStored(currentFixture)

        when:
        runPrevious()

        then:
        assertStateStored(previousFixture)
    }

    static void assertStateStored(BuildOperationsFixture operations) {
        def store = workGraphStoreOperation(operations)
        assert store != null && store.failure == null
    }

    @Nullable
    private static BuildOperationRecord workGraphStoreOperation(BuildOperationsFixture operations) {
        operations.firstMatchingRegex("Store (configuration cache|instant execution) state.*")
    }
}
