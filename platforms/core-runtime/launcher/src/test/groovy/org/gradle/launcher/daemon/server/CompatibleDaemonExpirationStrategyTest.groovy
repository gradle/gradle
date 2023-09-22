/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.launcher.daemon.server

import org.gradle.launcher.daemon.context.DaemonCompatibilitySpec
import org.gradle.launcher.daemon.context.DaemonContext
import org.gradle.launcher.daemon.registry.DaemonInfo

import static org.gradle.launcher.daemon.server.api.DaemonStateControl.State.Idle
import static org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus.GRACEFUL_EXPIRE

class CompatibleDaemonExpirationStrategyTest extends DaemonExpirationStrategyTest {
    DaemonCompatibilitySpec compatibilitySpec = Stub()
    def compatible = []
    def compatibleWithCurrent = []

    def setup() {
        // Start with a new registry on each test.
        _ * compatibilitySpec.isSatisfiedBy(_) >> { DaemonContext context -> context in compatibleWithCurrent }
    }

    def "expires compatible daemons"() {
        given:
        DaemonInfo d1 = registerDaemon(Idle)
        DaemonInfo d2 = registerDaemon(Idle)

        when:
        compatible = [ d1, d2 ]

        then:
        wouldExpire(d1)
        wouldExpire(d2)
    }

    def "does not expire when there are no compatible daemons"() {
        given:
        DaemonInfo d1 = registerDaemon(Idle)
        DaemonInfo d2 = registerDaemon(Idle)
        DaemonInfo d3 = registerDaemon(Idle)

        when:
        compatible = []

        then:
        !wouldExpire(d1)
        !wouldExpire(d2)
        !wouldExpire(d3)
    }

    def "does not expire when there is only one daemon"() {
        given:
        DaemonInfo d1 = registerDaemon(Idle)

        when:
        compatible = [ d1 ]

        then:
        !wouldExpire(d1)
    }

    boolean wouldExpire(DaemonInfo info, Boolean timeoutReached = true) {
        Daemon daemon = Mock(Daemon) {
            1 * getDaemonRegistry() >> { registry }
            _ * getDaemonContext() >> { info.getContext() }
            _ * getStateCoordinator() >> Stub(DaemonStateCoordinator) {
                getState() >> info.state
                getIdleMillis(_) >> { long now -> return IDLE_COMPATIBLE_TIMEOUT + (timeoutReached ?  1 : -1) }
            }
        }

        if (info in compatible) {
            compatibleWithCurrent = compatible.collect { it.context }
        } else {
            compatibleWithCurrent = []
        }

        return new CompatibleDaemonExpirationStrategy(daemon, compatibilitySpec).checkExpiration().status == GRACEFUL_EXPIRE
    }
}
