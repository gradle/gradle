/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.integtests.fixtures.daemon

import org.gradle.internal.logging.services.LoggingServiceRegistry
import org.gradle.internal.service.ServiceRegistryBuilder
import org.gradle.internal.service.scopes.GlobalScopeServices
import org.gradle.launcher.daemon.client.DaemonClientGlobalServices
import org.gradle.launcher.daemon.registry.DaemonRegistry
import org.gradle.launcher.daemon.registry.DaemonRegistryServices
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import org.gradle.util.GradleVersion

class DaemonLogsAnalyzer implements DaemonsFixture {
    private final File daemonLogsDir
    private final File daemonBaseDir
    private final DaemonRegistry registry
    private final String version

    DaemonLogsAnalyzer(File daemonBaseDir, String version = GradleVersion.current().version) {
        this.version = version
        this.daemonBaseDir = daemonBaseDir
        daemonLogsDir = new File(daemonBaseDir, version)
        def services = ServiceRegistryBuilder.builder()
            .parent(LoggingServiceRegistry.newEmbeddableLogging())
            .parent(NativeServicesTestFixture.getInstance())
            .provider(new GlobalScopeServices(false))
            .provider(new DaemonClientGlobalServices())
            .provider(new DaemonRegistryServices(daemonBaseDir))
            .build()
        registry = services.get(DaemonRegistry)
    }

    static DaemonsFixture newAnalyzer(File daemonBaseDir, String version = GradleVersion.current().version) {
        return new DaemonLogsAnalyzer(daemonBaseDir, version)
    }

    DaemonRegistry getRegistry() {
        return registry
    }

    void killAll() {
        daemons*.kill()
    }

    List<DaemonFixture> getDaemons() {
        if (!daemonLogsDir.exists() || !daemonLogsDir.isDirectory()) {
            return []
        }
        return daemonLogsDir.listFiles().findAll { it.name.endsWith('.log') }.collect { daemonForLogFile(it) }
    }

    List<DaemonFixture> getVisible() {
        return registry.all.collect { daemonForLogFile(new File(daemonLogsDir, "daemon-${it.pid}.out.log")) }
    }

    DaemonFixture daemonForLogFile(File logFile) {
        if (version == GradleVersion.current().version) {
            return new TestableDaemon(logFile, registry)
        }
        return new LegacyDaemon(logFile, version)
    }

    DaemonFixture getDaemon() {
        def daemons = getDaemons()
        assert daemons.size() == 1
        daemons[0]
    }
}
