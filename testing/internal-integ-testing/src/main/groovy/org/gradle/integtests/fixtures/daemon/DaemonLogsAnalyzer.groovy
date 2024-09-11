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
import org.gradle.internal.service.scopes.BasicGlobalScopeServices
import org.gradle.launcher.daemon.client.DaemonClientGlobalServices
import org.gradle.launcher.daemon.registry.DaemonRegistry
import org.gradle.launcher.daemon.registry.DaemonRegistryServices
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import org.gradle.util.GradleVersion

import static org.gradle.integtests.fixtures.RetryConditions.daemonStoppedWithSocketExceptionOnWindows

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
            .provider(new BasicGlobalScopeServices())
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
        allDaemons*.kill()
    }


    List<DaemonFixture> getDaemons() {
        getAllDaemons().findAll { !daemonStoppedWithSocketExceptionOnWindows(it) || it.logContains("Starting build in new daemon") }
    }

    List<DaemonFixture> getAllDaemons() {
        if (!daemonLogsDir.exists() || !daemonLogsDir.isDirectory()) {
            return []
        }
        return daemonLogsDir.listFiles().findAll { it.name.endsWith('.log') && !it.name.startsWith('hs_err') }.collect { daemonForLogFile(it) }
    }

    List<DaemonFixture> getVisible() {
        return registry.all.collect { daemonForLogFile(new File(daemonLogsDir, "daemon-${it.pid}.out.log")) }
    }

    DaemonFixture daemonForLogFile(File logFile) {
        def version = GradleVersion.version(version)
        def daemonLog = DaemonLogFile.forVersion(logFile, version)
        if (version == GradleVersion.current()) {
            return new TestableDaemon(daemonLog, registry, version)
        }
        return new LegacyDaemon(daemonLog, version)
    }

    DaemonFixture getDaemon() {
        def daemons = getDaemons()
        assert daemons.size() == 1
        daemons[0]
    }

    File getDaemonBaseDir() {
        return daemonBaseDir
    }

    String getVersion() {
        return version
    }

    void assertNoCrashedDaemon() {
        List<File> crashLogs = findCrashLogs(daemonLogsDir)
        crashLogs.each { println(it.text) }
        assert crashLogs.empty: "Found crash logs: ${crashLogs}"
    }

    static List<File> findCrashLogs(File dir) {
        dir.listFiles()?.findAll { it.name.endsWith('.log') && it.name.startsWith('hs_err') } ?: []
    }
}
