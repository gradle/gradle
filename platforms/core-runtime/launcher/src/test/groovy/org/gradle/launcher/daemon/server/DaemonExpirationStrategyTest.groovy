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

import org.gradle.internal.remote.Address
import org.gradle.launcher.daemon.context.DaemonContext
import org.gradle.launcher.daemon.registry.DaemonInfo
import org.gradle.launcher.daemon.registry.DaemonRegistry
import org.gradle.launcher.daemon.registry.EmbeddedDaemonRegistry
import org.gradle.launcher.daemon.server.api.DaemonStateControl
import org.gradle.internal.time.MockClock
import spock.lang.Specification

import static org.gradle.launcher.daemon.server.api.DaemonStateControl.State.Busy

abstract class DaemonExpirationStrategyTest extends Specification {
    DaemonRegistry registry

    def setup() {
        // Start with a new registry on each test.
        registry = new EmbeddedDaemonRegistry()
    }

    DaemonInfo registerDaemon(DaemonStateControl.State state, long lastIdle = -1) {
        final String uid = UUID.randomUUID().toString()
        final int id = registry.getAll().size() + 1
        final long lastIdleTime = lastIdle == -1L ? id * 1000 : lastIdle;
        Address daemonAddress = createAddress(id)
        DaemonContext context = Mock(DaemonContext) {
            _ * getUid() >> uid
        }
        DaemonInfo info = new DaemonInfo(daemonAddress, context, "password".bytes, Busy, new MockClock(lastIdleTime))
        info.setState(state)
        registry.store(info)
        return info
    }

    private static Address createAddress(int i) {
        new Address() {
            int getNum() { i }
            String getDisplayName() { getNum() }
        }
    }
}
