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


class PlayApplicationDeploymentHandleTest extends Specification {
    def PlayApplicationRunnerToken runnerToken = Mock(PlayApplicationRunnerToken)
    def PlayApplicationDeploymentHandle deploymentHandle = new PlayApplicationDeploymentHandle("test", runnerToken)

    def "reloading deployment handle reloads runner" () {
        when:
        deploymentHandle.reload()

        then:
        1 * runnerToken.isRunning() >> true
        1 * runnerToken.rebuildSuccess()
    }

    def "stopping deployment handle stops runner" () {
        when:
        deploymentHandle.stop()

        then:
        1 * runnerToken.isRunning() >> true
        1 * runnerToken.stop()
    }

    def "cannot reload a stopped deployment handle" () {
        given:
        1 * runnerToken.isRunning() >> false

        when:
        deploymentHandle.reload()

        then:
        IllegalStateException e = thrown()
        e.message == "Cannot reload a deployment handle that has already been stopped."
    }
}
