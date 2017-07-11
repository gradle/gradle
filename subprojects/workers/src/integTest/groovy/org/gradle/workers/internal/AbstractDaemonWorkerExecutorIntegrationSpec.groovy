/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.workers.internal

import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer
import org.gradle.integtests.fixtures.daemon.DaemonsFixture


abstract class AbstractDaemonWorkerExecutorIntegrationSpec extends AbstractWorkerExecutorIntegrationTest {
    def setup() {
        executer.requireDaemon()
        executer.requireIsolatedDaemons()
        executer.withWorkerDaemonsExpirationDisabled()
    }

    void stopDaemonsNow() {
        result = executer.withArguments("--stop", "--info").run()
    }

    DaemonsFixture getDaemons() {
        new DaemonLogsAnalyzer(executer.daemonBaseDir)
    }

    DaemonsFixture daemons(String gradleVersion) {
        new DaemonLogsAnalyzer(executer.daemonBaseDir, gradleVersion)
    }
}
