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
    def PlayApplicationRunner runner = Mock(PlayApplicationRunner)
    def PlayApplicationDeploymentHandle deploymentHandle = new PlayApplicationDeploymentHandle("test", runner)

    def "starting deployment handle starts runner" () {
        PlayRunSpec spec = Mock(PlayRunSpec)

        when:
        deploymentHandle.start(spec)

        then:
        1 * runner.start({ it == spec })
    }

    def "stopping deployment handle stops runner" () {
        PlayRunSpec spec = Mock(PlayRunSpec)
        PlayApplicationRunnerToken token = Mock(PlayApplicationRunnerToken)

        when:
        deploymentHandle.start(spec)
        deploymentHandle.stop()

        then:
        1 * runner.start(_) >> token
        1 * token.stop()
    }
}
