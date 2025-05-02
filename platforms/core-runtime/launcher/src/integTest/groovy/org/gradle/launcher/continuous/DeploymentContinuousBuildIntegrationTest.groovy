/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.launcher.continuous

import org.gradle.integtests.fixtures.AbstractContinuousIntegrationTest
import org.gradle.test.fixtures.TestDeploymentFixture

import java.util.concurrent.TimeUnit

import static org.gradle.util.internal.CollectionUtils.single

class DeploymentContinuousBuildIntegrationTest extends AbstractContinuousIntegrationTest {
    def fixture = new TestDeploymentFixture()

    def setup() {
        fixture.writeToProject(testDirectory)
        buildTimeout = 30
    }

    def "deployment promoted to continuous build reports accurate build time" () {
        when:
        withoutContinuousBuild()
        succeeds("runDeployment")

        then:
        def key = fixture.keyFile.text
        fixture.assertDeploymentIsRunning(key)
        buildTimes.size() == 2
        buildTimes[0] >= buildTimes[1]
    }

    def "deployment in continuous build reports accurate build time" () {
        when:
        succeeds("runDeployment")

        then:
        def key = fixture.keyFile.text
        fixture.assertDeploymentIsRunning(key)

        when:
        def lastBuildTime = single(buildTimes)
        waitBeforeModification fixture.triggerFile
        fixture.triggerFile << "\n#a change"
        buildTriggeredAndSucceeded()

        then:
        fixture.assertDeploymentIsRunning(key)
        lastBuildTime >= single(buildTimes)
    }

    List<Long> getBuildTimes() {
        return (output =~ /BUILD SUCCESSFUL in (\d+)(m?s)/).collect { buildTimeFromString(it[1].toString(), it[2].toString()) }
    }

    private long buildTimeFromString(String time, String unit) {
        switch (unit) {
            case "ms":
                return time.toInteger()
            case "s":
                return TimeUnit.SECONDS.toMillis(time.toInteger())
            default:
                throw new IllegalArgumentException("Unexpected time unit: $unit")
        }
    }
}
