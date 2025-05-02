/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.launcher.daemon.server.exec.LogToClient

class DaemonOutputToggleIntegrationTest extends DaemonIntegrationSpec {

    def "output is received when toggle is off"() {
        when:
        executer.noExtraLogging()
        succeeds "help"

        then:
        outputContains(":help")
    }

    def "output is not received when toggle is on"() {
        when:
        executer.withBuildJvmOpts("-D$LogToClient.DISABLE_OUTPUT=true").noExtraLogging()
        succeeds "help"

        then:
        outputDoesNotContain(":help")
        outputDoesNotContain("BUILD SUCCESSFUL")
    }
}
