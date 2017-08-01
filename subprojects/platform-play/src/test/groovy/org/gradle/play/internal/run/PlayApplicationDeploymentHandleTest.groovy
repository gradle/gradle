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

import spock.lang.Specification

import java.util.concurrent.Callable

class PlayApplicationDeploymentHandleTest extends Specification {
    PlayApplicationRunnerToken runnerToken = Mock(PlayApplicationRunnerToken)
    PlayApplicationDeploymentHandle deploymentHandle = new PlayApplicationDeploymentHandle("test", new Callable() {
        @Override
        PlayApplicationRunnerToken call() throws Exception {
            return runnerToken
        }
    })

    def failure = new Throwable()

    def "reloading deployment handle reloads runner" () {
        when:
        deploymentHandle.start()
        deploymentHandle.buildResult(null)

        then:
        1 * runnerToken.isRunning() >> true
        1 * runnerToken.rebuildSuccess()

        when:
        deploymentHandle.buildResult(failure)

        then:
        1 * runnerToken.isRunning() >> true
        1 * runnerToken.rebuildFailure(failure)

    }

    def "stopping deployment handle stops runner" () {
        when:
        deploymentHandle.start()
        deploymentHandle.stop()

        then:
        1 * runnerToken.isRunning() >> true
        1 * runnerToken.stop()
    }

    def "cannot reload a stopped deployment handle" () {
        given:
        runnerToken.isRunning() >> false
        deploymentHandle.start()

        when:
        deploymentHandle.buildResult(null)

        then:
        def e = thrown(IllegalStateException)
        e.message == "test needs to be started first."

        when:
        deploymentHandle.buildResult(failure)

        then:
        e = thrown(IllegalStateException)
        e.message == "test needs to be started first."
    }

    def "cannot reload a deployment handle that was never started" () {
        when:
        deploymentHandle.buildResult(null)
        then:
        def e = thrown(IllegalStateException)
        e.message == "test needs to be started first."

        when:
        deploymentHandle.buildResult(failure)
        then:
        e = thrown(IllegalStateException)
        e.message == "test needs to be started first."
    }
}
