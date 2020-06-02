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

package org.gradle.instantexecution

import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.CrossVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetVersions
import org.gradle.integtests.fixtures.instantexecution.InstantExecutionBuildOperationsFixture
import org.gradle.util.GradleVersion

import static org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheOption.LONG_OPTION


@TargetVersions("6.5-rc-1+")
class InstantExecutionCacheCrossVersionTest extends CrossVersionIntegrationSpec {

    def previousExecuter = version(previous)
    def previousFixture = new InstantExecutionBuildOperationsFixture(new BuildOperationsFixture(previousExecuter, temporaryFolder))

    def currentExecuter = version(current)
    def currentFixture = new InstantExecutionBuildOperationsFixture(new BuildOperationsFixture(currentExecuter, temporaryFolder))

    void runPrevious() {
        previousExecuter.withArguments(argFor(previous.version), 'help').run()
    }

    void runCurrent() {
        currentExecuter.withArguments(argFor(current.version), 'help').run()
    }

    private static String argFor(GradleVersion version) {
        version.baseVersion == GradleVersion.version("6.5")
            ? "--${LONG_OPTION}=on"
            : "--$LONG_OPTION"
    }

    def "does not reuse cached state from previous version"() {

        when:
        runPrevious()

        then:
        previousFixture.assertStateStored()

        when:
        runCurrent()

        then:
        currentFixture.assertStateStored()
    }

    def "does not reuse cached state from future version"() {

        when:
        runCurrent()

        then:
        currentFixture.assertStateStored()

        when:
        runPrevious()

        then:
        previousFixture.assertStateStored()
    }
}
