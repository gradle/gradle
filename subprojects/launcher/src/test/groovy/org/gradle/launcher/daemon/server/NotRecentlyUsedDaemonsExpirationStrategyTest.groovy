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

import org.gradle.launcher.daemon.registry.DaemonInfo
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus

import static org.gradle.launcher.daemon.server.api.DaemonStateControl.State.Idle

class NotRecentlyUsedDaemonsExpirationStrategyTest extends DaemonExpirationStrategyTest {

    def "does not expire when there is only one daemon and max duplicated idle count is one"() {
        given:
        int maxDuplicatedIdleCount = 1
        DaemonInfo d1 = registerDaemon(Idle)

        expect:
        !wouldExpire(d1, maxDuplicatedIdleCount)
    }

    def "expires given more than 1 daemon and max duplicated idle count is one"() {
        given:
        int maxDuplicatedIdleCount = 1
        DaemonInfo d1 = registerDaemon(Idle)
        DaemonInfo d2 = registerDaemon(Idle)

        expect:
        wouldExpire(d1, maxDuplicatedIdleCount)
        !wouldExpire(d2, maxDuplicatedIdleCount)
    }

    def "expires all daemons except three given max duplicated idle count is three"() {
        given:
        int maxDuplicatedIdleCount = 3
        DaemonInfo d1 = registerDaemon(Idle)
        DaemonInfo d2 = registerDaemon(Idle)
        DaemonInfo d3 = registerDaemon(Idle)
        DaemonInfo d4 = registerDaemon(Idle)
        DaemonInfo d5 = registerDaemon(Idle)

        expect:
        wouldExpire(d1, maxDuplicatedIdleCount)
        wouldExpire(d2, maxDuplicatedIdleCount)
        !wouldExpire(d3, maxDuplicatedIdleCount)
        !wouldExpire(d4, maxDuplicatedIdleCount)
        !wouldExpire(d5, maxDuplicatedIdleCount)
    }

    def "expires no daemons given four daemons max duplicated idle count is four"() {
        given:
        int maxDuplicatedIdleCount = 4
        DaemonInfo d1 = registerDaemon(Idle)
        DaemonInfo d2 = registerDaemon(Idle)
        DaemonInfo d3 = registerDaemon(Idle)
        DaemonInfo d4 = registerDaemon(Idle)

        expect:
        !wouldExpire(d1, maxDuplicatedIdleCount)
        !wouldExpire(d2, maxDuplicatedIdleCount)
        !wouldExpire(d3, maxDuplicatedIdleCount)
        !wouldExpire(d4, maxDuplicatedIdleCount)
    }

    boolean wouldExpire(DaemonInfo info, int maxDuplicatedIdleCount) {
        Daemon daemon = Mock(Daemon) {
            1 * getDaemonRegistry() >> { registry }
            _ * getDaemonContext() >> { info.getContext() }
        }
        return new NotRecentlyUsedDaemonsExpirationStrategy(daemon, maxDuplicatedIdleCount).checkExpiration().status == DaemonExpirationStatus.GRACEFUL_EXPIRE
    }
}
