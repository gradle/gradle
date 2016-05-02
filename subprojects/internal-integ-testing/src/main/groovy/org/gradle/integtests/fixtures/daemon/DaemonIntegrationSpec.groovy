/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.fixtures.file.LeaksFileHandles
import spock.lang.IgnoreIf

@IgnoreIf({ GradleContextualExecuter.daemon })
@LeaksFileHandles
abstract class DaemonIntegrationSpec extends AbstractIntegrationSpec {
    def setup() {
        executer.requireDaemon()
        executer.requireIsolatedDaemons()
    }

    protected void cleanup() {
        // Need to kill daemons before test files are cleaned up, as the log files and registry are used to locate the daemons and these live under
        // the test file directory.
        daemons.killAll()
    }

    void stopDaemonsNow() {
        result = executer.withArguments("--stop", "--info").run()
    }

    void buildSucceeds() {
        result = executer.withArguments("--info").run()
    }

    DaemonsFixture getDaemons() {
        new DaemonLogsAnalyzer(executer.daemonBaseDir)
    }

    DaemonsFixture daemons(String gradleVersion){
        new DaemonLogsAnalyzer(executer.daemonBaseDir, gradleVersion)
    }
}
