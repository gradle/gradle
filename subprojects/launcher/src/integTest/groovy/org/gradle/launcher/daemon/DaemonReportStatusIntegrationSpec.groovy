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

package org.gradle.launcher.daemon

import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.launcher.daemon.logging.DaemonMessages
import org.gradle.test.fixtures.server.http.CyclicBarrierHttpServer
import org.junit.Rule

class DaemonReportStatusIntegrationSpec extends DaemonIntegrationSpec {
    @Rule CyclicBarrierHttpServer server = new CyclicBarrierHttpServer()

    def "shows default message if no daemons are running"() {
        when:
        def out = executer.withArguments("--status").run().output

        then:
        out == """$DaemonMessages.NO_DAEMONS_RUNNING
""".toString()
    }

    def "reports idle and busy status of running daemons"() {
        given:
        buildFile << """
task block << {
    new URL("$server.uri").text
}
"""
        def build = executer.withTasks("block").start()
        server.waitFor()
        daemons.daemon.assertBusy()
        executer.withBuildJvmOpts('-Xmx128m')
        executer.run()

        when:
        def out = executer.withArguments("--status").run().output

        then:
        daemons.daemons.size() == 2
        out =~ /^   PID VERSION STATUS/
        out =~ /\n\s*\d+ ([\w\.\+\-]+)\s+IDLE/
        out =~ /\n\s*\d+ ([\w\.\+\-]+)\s+BUSY/

        cleanup:
        server.release()
        build.waitForFinish()
    }
}
