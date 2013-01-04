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

package org.gradle.launcher.daemon

import ch.qos.logback.classic.Level
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleDistributionExecuter
import org.slf4j.LoggerFactory

/**
 * by Szczepan Faber, created at: 2/1/12
 */
class DaemonIntegrationSpec extends AbstractIntegrationSpec {

    String output

    def setup() {
        executer.executerType = GradleDistributionExecuter.Executer.daemon
        distribution.requireIsolatedDaemons()
        LoggerFactory.getLogger("org.gradle.cache.internal.DefaultFileLockManager").level = Level.INFO
    }

    void stopDaemonsNow() {
        def result = executer.withArguments("--stop", "--info").run()
        output = result.output
    }

    void buildSucceeds(String script = '') {
        file('build.gradle') << script
        def result = executer.withArguments("--info").withNoDefaultJvmArgs().run()
        output = result.output
    }
}
