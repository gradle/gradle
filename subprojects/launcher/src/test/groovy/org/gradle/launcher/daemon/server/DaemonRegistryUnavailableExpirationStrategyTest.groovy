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
import org.gradle.launcher.daemon.context.DefaultDaemonContext
import org.gradle.launcher.daemon.registry.DaemonDir
import org.gradle.launcher.daemon.registry.DaemonInfo
import org.gradle.launcher.daemon.registry.DaemonRegistry
import org.gradle.launcher.daemon.registry.EmbeddedDaemonRegistry
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.launcher.daemon.server.DaemonExpirationStatus.DO_NOT_EXPIRE
import static org.gradle.launcher.daemon.server.DaemonExpirationStatus.GRACEFUL_EXPIRE

class DaemonRegistryUnavailableExpirationStrategyTest extends Specification {
    @Rule TestNameTestDirectoryProvider tempDir = new TestNameTestDirectoryProvider()
    Daemon daemon = Mock(Daemon)
    File daemonDir = tempDir.createDir("test_daemon_dir")

    def "daemon should expire when registry file is unreachable"() {
        given:
        DaemonRegistryUnavailableExpirationStrategy expirationStrategy = new DaemonRegistryUnavailableExpirationStrategy()
        DaemonContext daemonContext = new DefaultDaemonContext("user", null, tempDir.file("BOGUS"), 51234L, 10000, [] as List<String>)

        when:
        1 * daemon.getDaemonContext() >> { daemonContext }
        DaemonExpirationResult expirationCheck = expirationStrategy.checkExpiration(daemon)

        then:
        expirationCheck.status == GRACEFUL_EXPIRE
        expirationCheck.reason == "daemon registry became unreadable"
    }

    def "daemon should not expire given readable registry with it's PID"() {
        given:
        Address address = new Address() {
            String getDisplayName() {
                return "DAEMON_ADDRESS"
            }
        }
        DaemonContext daemonContext = new DefaultDaemonContext("user", null, daemonDir, 51234L, 10000, [] as List<String>)
        DaemonDir daemonDir = new DaemonDir(daemonDir)
        DaemonRegistry registry = new EmbeddedDaemonRegistry()
        daemonDir.getRegistry().createNewFile()
        registry.store(new DaemonInfo(address, daemonContext, "password".bytes, true))
        DaemonRegistryUnavailableExpirationStrategy expirationStrategy = new DaemonRegistryUnavailableExpirationStrategy()

        when:
        1 * daemon.getDaemonContext() >> { daemonContext }
        1 * daemon.getDaemonRegistry() >> { registry }

        then:
        DaemonExpirationResult expirationCheck = expirationStrategy.checkExpiration(daemon)
        expirationCheck.status == DO_NOT_EXPIRE
        expirationCheck.reason == null
    }
}
