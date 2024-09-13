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
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.launcher.daemon.configuration.DaemonPriority
import org.gradle.launcher.daemon.context.DaemonContext
import org.gradle.launcher.daemon.context.DefaultDaemonContext
import org.gradle.launcher.daemon.registry.DaemonDir
import org.gradle.launcher.daemon.registry.DaemonInfo
import org.gradle.launcher.daemon.registry.DaemonRegistry
import org.gradle.launcher.daemon.registry.EmbeddedDaemonRegistry
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationResult
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

import static org.gradle.internal.nativeintegration.services.NativeServices.NativeServicesMode
import static org.gradle.launcher.daemon.server.api.DaemonState.Idle
import static org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus.DO_NOT_EXPIRE
import static org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus.GRACEFUL_EXPIRE

class DaemonRegistryUnavailableExpirationStrategyTest extends Specification {
    Daemon daemon = Mock(Daemon)
    @Subject DaemonRegistryUnavailableExpirationStrategy expirationStrategy = new DaemonRegistryUnavailableExpirationStrategy(daemon)
    @Rule TestNameTestDirectoryProvider tempDir = new TestNameTestDirectoryProvider(getClass())
    File daemonDir = tempDir.createDir("test_daemon_dir")

    def "daemon should expire when registry file is unreachable"() {
        given:
        DaemonRegistryUnavailableExpirationStrategy expirationStrategy = new DaemonRegistryUnavailableExpirationStrategy(daemon)
        DaemonContext daemonContext = new DefaultDaemonContext("user", null, JavaLanguageVersion.current(), null, tempDir.file("BOGUS"), 51234L, 10000, [] as List<String>, false, NativeServicesMode.ENABLED, DaemonPriority.NORMAL)

        when:
        1 * daemon.getDaemonContext() >> { daemonContext }
        DaemonExpirationResult expirationCheck = expirationStrategy.checkExpiration()

        then:
        expirationCheck.status == GRACEFUL_EXPIRE
        expirationCheck.reason == DaemonRegistryUnavailableExpirationStrategy.REGISTRY_BECAME_UNREADABLE
    }

    def "daemon should not expire given readable registry with it's PID"() {
        given:
        Address address = new Address() {
            String getDisplayName() {
                return "DAEMON_ADDRESS"
            }
        }
        DaemonContext daemonContext = new DefaultDaemonContext("user", null, JavaLanguageVersion.current(), null, daemonDir, 51234L, 10000, [] as List<String>, false, NativeServicesMode.ENABLED, DaemonPriority.NORMAL)
        DaemonDir daemonDir = new DaemonDir(daemonDir)
        DaemonRegistry registry = new EmbeddedDaemonRegistry()
        daemonDir.getRegistry().createNewFile()
        registry.store(new DaemonInfo(address, daemonContext, "password".bytes, Idle))

        when:
        1 * daemon.getDaemonContext() >> { daemonContext }
        1 * daemon.getDaemonRegistry() >> { registry }

        then:
        DaemonExpirationResult expirationCheck = expirationStrategy.checkExpiration()
        expirationCheck.status == DO_NOT_EXPIRE
        expirationCheck.reason == null
    }
}
