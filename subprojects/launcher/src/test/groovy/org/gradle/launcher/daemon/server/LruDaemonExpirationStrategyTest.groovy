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
import org.gradle.launcher.daemon.registry.EmbeddedDaemonRegistry
import org.gradle.util.MockTimeProvider
import spock.lang.Specification

class LruDaemonExpirationStrategyTest extends Specification {
    private final LruDaemonExpirationStrategy strategy = new LruDaemonExpirationStrategy()
    private EmbeddedDaemonRegistry registry

    def setup() {
        // Start with a new registry on each test.
        registry = new EmbeddedDaemonRegistry()
    }

    def "expires one idle daemon"() {
        when:
        DaemonInfo info = registerDaemon(10, true)

        then:
        isOldest(info)
    }

    def "doesn't expire one busy daemon"() {
        when:
        DaemonInfo info = registerDaemon(10, false)

        then:
        !isOldest(info)
    }

    def "only expires one daemon"() {
        when:
        DaemonInfo d1 = registerDaemon(10, true)
        DaemonInfo d2 = registerDaemon(20, true)

        then:
        isOldest(d1)
        !isOldest(d2)
    }

    def "only expires idle daemons"() {
        when:
        DaemonInfo d1 = registerDaemon(10, false)
        DaemonInfo d2 = registerDaemon(20, true)
        DaemonInfo d3 = registerDaemon(30, true)

        then:
        !isOldest(d1)
        isOldest(d2)
        !isOldest(d3)
    }

    private DaemonInfo registerDaemon(long lastIdleTime, boolean idle) {
        final int id = registry.getAll().size() + 1
        Address daemonAddress = createAddress(id)
        DaemonContext context = Mock(DaemonContext) {
            _ * getPid() >> { id }
        }
        DaemonInfo info = new DaemonInfo(daemonAddress, context, "password", idle, new MockTimeProvider(lastIdleTime))
        registry.store(info)
        return info
    }

    private static Address createAddress(int i) {
        new Address() {
            int getNum() { i }
            String getDisplayName() { getNum() }
        }
    }

    private boolean isOldest(DaemonInfo info) {
        return strategy.isOldest(info.getContext(), registry)
    }
}
