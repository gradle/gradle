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

import org.gradle.launcher.daemon.context.DaemonContext
import org.gradle.launcher.daemon.registry.DaemonDir
import spock.lang.Ignore
import spock.lang.Specification

class DaemonRegistryUnavailableExpirationStrategyTest extends Specification {
    final Daemon daemon = Mock(Daemon)
    final DaemonContext daemonContext = Mock(DaemonContext)
    final DaemonDir daemonDir = Mock(DaemonDir)

    def "daemon should expire when registry file is unreachable"() {
        given:
        DaemonRegistryUnavailableExpirationStrategy expirationStrategy = new DaemonRegistryUnavailableExpirationStrategy()

        when:
        1 * daemon.getDaemonContext() >> { daemonContext }
        1 * daemonContext.getDaemonRegistryDir() >> { new File("BOGUS") }
        boolean daemonShouldExpire = expirationStrategy.shouldExpire(daemon)

        then:
        daemonShouldExpire
    }

    // FIXME(ew): create TempDir and registry.bin file
    @Ignore
    def "daemon should not expire given readable registry"() {
        given:
        DaemonRegistryUnavailableExpirationStrategy expirationStrategy = new DaemonRegistryUnavailableExpirationStrategy()

        when:
        1 * daemon.getDaemonContext() >> { daemonContext }
        1 * daemonContext.getDaemonRegistryDir() >> { new File("foo") }

        then:
        !expirationStrategy.shouldExpire(daemon)
    }
}
