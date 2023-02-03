/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.integtests.tooling.r25

import org.gradle.integtests.fixtures.executer.GradleVersions
import org.gradle.integtests.tooling.fixture.ContinuousBuildToolingApiSpecification
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.test.fixtures.TestDeploymentFixture
import spock.lang.Timeout

@Timeout(120)
@TargetGradleVersion(GradleVersions.SUPPORTS_DEPLOYMENT_REGISTRY)
class DeploymentHandleContinuousBuildCrossVersionSpec extends ContinuousBuildToolingApiSpecification {
    def fixture = new TestDeploymentFixture()

    def setup() {
        fixture.writeToProject(projectDir)
        buildTimeout = 30
    }

    def "deployment is stopped when continuous build is cancelled" () {
        when:
        runBuild(["runDeployment"]) {
            succeeds()
            def key = fixture.keyFile.text
            fixture.assertDeploymentIsRunning(key)

            waitBeforeModification fixture.triggerFile
            fixture.triggerFile << "\n#a change"
            succeeds()
            fixture.assertDeploymentIsRunning(key)

            waitBeforeModification fixture.triggerFile
            fixture.triggerFile << "\n#another change"
            succeeds()
            fixture.assertDeploymentIsRunning(key)

            cancel()
        }

        then:
        fixture.assertDeploymentIsStopped()
    }
}
