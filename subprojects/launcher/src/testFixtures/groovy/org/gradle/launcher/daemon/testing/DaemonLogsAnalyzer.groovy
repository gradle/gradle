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

package org.gradle.launcher.daemon.testing

import org.gradle.internal.nativeintegration.services.NativeServices
import org.gradle.internal.service.ServiceRegistryBuilder
import org.gradle.internal.service.scopes.GlobalScopeServices
import org.gradle.launcher.daemon.client.DaemonClientGlobalServices
import org.gradle.launcher.daemon.registry.DaemonRegistry
import org.gradle.launcher.daemon.registry.DaemonRegistryServices
import org.gradle.logging.LoggingServiceRegistry
import org.gradle.util.GradleVersion

class DaemonLogsAnalyzer implements DaemonsFixture {
    private File daemonLogsDir
    private File daemonBaseDir
    private DaemonRegistry registry

    DaemonLogsAnalyzer(File daemonBaseDir) {
        this.daemonBaseDir = daemonBaseDir
        daemonLogsDir = new File(daemonBaseDir, GradleVersion.current().version)
        def services = ServiceRegistryBuilder.builder()
                .parent(LoggingServiceRegistry.newEmbeddableLogging())
                .parent(NativeServices.instance)
                .provider(new GlobalScopeServices(false))
                .provider(new DaemonClientGlobalServices())
                .provider(new DaemonRegistryServices(daemonBaseDir))
                .build()
        registry = services.get(DaemonRegistry)
    }

    DaemonRegistry getRegistry() {
        return registry
    }

    void killAll() {
        daemons*.kill()
    }

    List<DaemonFixture> getDaemons() {
        assert daemonLogsDir.isDirectory()
        return daemonLogsDir.listFiles().findAll { it.name.endsWith('.log') }.collect { new TestableDaemon(it, registry) }
    }

    List<DaemonFixture> getVisible() {
        return registry.all.collect { new TestableDaemon(new File(daemonLogsDir, "daemon-${it.pid}.out.log"), registry) }
    }

    DaemonFixture getDaemon() {
        def daemons = getDaemons()
        assert daemons.size() == 1
        daemons[0]
    }
}