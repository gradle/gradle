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

import org.gradle.process.internal.WorkerProcess
import spock.lang.Specification

class PlayApplicationRunnerTokenTest extends Specification {

    def process = Mock(WorkerProcess)
    def client = Mock(PlayWorkerClient)
    def server = Mock(PlayRunWorkerServerProtocol)
    def runnerToken = new PlayApplicationRunnerToken(server, client, process)

    def "stops all participants when stopped"() {
        when:
        runnerToken.stop()

        then:
        1 * server.stop()
        1 * client.waitForStop()
        1 * process.waitForStop()
    }

    def "rebuildSuccess sends successful build result to server"() {
        when:
        runnerToken.rebuildSuccess()

        then:
        1 * server.reload()
    }

    def "rebuildFailure sends failure build result to server"() {
        given:
        def failure = new Throwable()
        when:
        runnerToken.rebuildFailure(failure)

        then:
        1 * server.buildError(failure)
    }
}
