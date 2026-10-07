/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.launcher.daemon

import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.launcher.daemon.logging.DaemonMessages

class DaemonAuthenticationIntegrationSpec extends DaemonIntegrationSpec {
    private static final String REFUSED = "Peer did not present the expected connection token"

    def "daemon refuses connection from a client that does not have the correct authentication token"() {
        when:
        buildSucceeds()
        def daemon = daemons.daemon
        daemon.assertIdle()

        then:
        daemon.assertRegistryNotWorldReadable()

        when:
        daemon.changeTokenVisibleToClient()
        buildSucceeds()

        then: "daemon rejects the token during the handshake"
        daemon.log.contains(REFUSED)

        and: "client treats the refusal as an unreachable daemon and starts a new one"
        daemons.daemons.size() == 2

        and: "client removed the old daemon from the registry, so it expires"
        daemon.stops()
    }

    def "daemon discards stop request that does not contain correct authentication token"() {
        given:
        buildSucceeds()
        def daemon = daemons.daemon
        daemon.assertIdle()

        when:
        daemon.changeTokenVisibleToClient()
        stopDaemonsNow()

        then:
        output.contains DaemonMessages.UNABLE_TO_STOP_DAEMON
        daemon.log.contains(REFUSED)

        and:
        // daemon is still running
        daemon.assertIdle()
    }
}
