/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.integtests.fixtures

import org.gradle.launcher.daemon.client.DaemonClientServices
import org.gradle.launcher.daemon.registry.DaemonRegistry
import static org.gradle.util.ConcurrentSpecification.poll

class RegistryBackedDaemonController implements DaemonController {
    private final DaemonRegistry daemons

    RegistryBackedDaemonController(File userHomeDir) {
        daemons = new DaemonClientServices(userHomeDir).get(DaemonRegistry)
    }

    RegistryBackedDaemonController(DaemonRegistry daemons) {
        this.daemons = daemons
    }

    def getNumDaemons() { daemons.all.size() }

    def getNumBusyDaemons() { daemons.busy.size() }

    def getNumIdleDaemons() { daemons.idle.size() }

    void isDaemonsRunning(int expectedDaemons) {
        poll { assert numDaemons == expectedDaemons }
        true
    }

    void isDaemonsIdle(int expectedDaemons) {
        poll { assert numIdleDaemons == expectedDaemons }
        true
    }

    void isDaemonsBusy(int expectedDaemons) {
        poll { assert numBusyDaemons == expectedDaemons }
        true
    }

    Collection<File> getDaemonLogs() {
        return daemons.registryFile.parentFile.listFiles().findAll { it.name.matches("daemon-.+\\.out.log") }
    }
}
