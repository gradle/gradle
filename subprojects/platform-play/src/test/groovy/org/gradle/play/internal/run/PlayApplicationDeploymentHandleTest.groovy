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

import org.gradle.deployment.internal.Deployment
import spock.lang.Specification

class PlayApplicationDeploymentHandleTest extends Specification {
    PlayRunSpec spec = Mock()
    PlayApplicationRunner runner = Mock()
    PlayApplicationRunnerToken runnerToken = Mock(PlayApplicationRunnerToken)
    PlayApplicationDeploymentHandle deploymentHandle = new PlayApplicationDeploymentHandle(spec, runner)

    def failure = new Throwable()

    def setup() {
        def deployment = Mock(Deployment)
        runner.start(spec, deployment) >> runnerToken
        deploymentHandle.start(deployment)
    }

    def "running comes from runnerToken"() {
        when:
        def running = deploymentHandle.running
        then:
        1 * runnerToken.running >> true
        running
    }

    def "stopping deployment handle stops runner" () {
        when:
        deploymentHandle.stop()
        then:
        1 * runnerToken.stop()
    }

    def "gets application IP from runner"() {
        when:
        def address = deploymentHandle.playAppAddress
        then:
        1 * runnerToken.running >> false
        0 * runnerToken.playAppAddress
        address == null

        when:
        deploymentHandle.playAppAddress
        then:
        1 * runnerToken.running >> true
        1 * runnerToken.playAppAddress
    }
}
