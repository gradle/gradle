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

package org.gradle.play.internal.run

import org.gradle.BuildListener
import org.gradle.BuildResult
import org.gradle.api.invocation.Gradle
import spock.lang.Specification


class PlayApplicationDeploymentHandleTest extends Specification {
    def PlayApplicationRunnerToken runnerToken = Mock(PlayApplicationRunnerToken)
    def PlayApplicationDeploymentHandle deploymentHandle = new PlayApplicationDeploymentHandle("test")
    def Gradle gradle = Mock(Gradle)
    def failure = new Throwable()
    def BuildResult goodBuild = new BuildResult(gradle, null)
    def BuildResult badBuild = new BuildResult(gradle, failure)

    def "reloading deployment handle reloads runner" () {
        when:
        deploymentHandle.start(runnerToken)
        deploymentHandle.reloadFromResult(goodBuild)

        then:
        1 * runnerToken.isRunning() >> true
        1 * runnerToken.rebuildSuccess()

        when:
        deploymentHandle.reloadFromResult(badBuild)

        then:
        1 * runnerToken.isRunning() >> true
        1 * runnerToken.rebuildFailure(failure)

    }

    def "stopping deployment handle stops runner" () {
        when:
        deploymentHandle.start(runnerToken)
        deploymentHandle.stop()

        then:
        1 * runnerToken.isRunning() >> true
        1 * runnerToken.stop()
    }

    def "cannot reload a stopped deployment handle" () {
        given:
        runnerToken.isRunning() >> false
        deploymentHandle.start(runnerToken)

        when:
        deploymentHandle.reloadFromResult(goodBuild)

        then:
        0 * runnerToken.rebuildSuccess()

        when:
        deploymentHandle.reloadFromResult(badBuild)

        then:
        0 * runnerToken.rebuildFailure(_)
    }

    def "cannot reload a deployment handle that was never started" () {
        when:
        deploymentHandle.reloadFromResult(goodBuild)
        then:
        0 * runnerToken.rebuildFailure(_)

        when:
        deploymentHandle.reloadFromResult(badBuild)
        then:
        0 * runnerToken.rebuildSuccess()
    }

    def "registers for build finished events" () {
        when:
        deploymentHandle.onNewBuild(gradle)
        then:
        1 * gradle.addBuildListener({ it instanceof BuildListener })
    }

}
