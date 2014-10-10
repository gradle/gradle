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

import org.gradle.initialization.BuildLayoutParameters
import org.gradle.launcher.daemon.client.DaemonClientServices
import org.gradle.launcher.daemon.configuration.DaemonParameters
import org.gradle.launcher.daemon.registry.DaemonRegistry
import org.gradle.logging.LoggingServiceRegistry
import org.gradle.util.GradleVersion

class DaemonLogsAnalyzer implements DaemonsFixture {
    private File daemonLogsDir
    private File daemonBaseDir
    private DaemonRegistry registry

    DaemonLogsAnalyzer(File daemonBaseDir) {
        this.daemonBaseDir = daemonBaseDir
        daemonLogsDir = new File(daemonBaseDir, GradleVersion.current().version)
        DaemonParameters daemonParameters = new DaemonParameters(new BuildLayoutParameters())
        daemonParameters.setBaseDir(daemonBaseDir)
        def services = new DaemonClientServices(LoggingServiceRegistry.newEmbeddableLogging(), daemonParameters, new ByteArrayInputStream(new byte[0]))
        registry = services.get(DaemonRegistry)
    }

    void killAll() {
        daemons*.kill()
    }

    List<DaemonFixture> getDaemons() {
        assert daemonLogsDir.isDirectory()
        return daemonLogsDir.listFiles().findAll { it.name.endsWith('.log') }.collect { new TestableDaemon(it, registry) }
    }

    List<DaemonFixture> getVisible() {
        return registry.all.collect { new TestableDaemon(new File(daemonLogsDir, "daemon-${it.context.pid}.out.log"), registry) }
    }

    DaemonFixture getDaemon() {
        def daemons = getDaemons()
        assert daemons.size() == 1
        daemons[0]
    }
}