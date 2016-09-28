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
import org.gradle.launcher.daemon.client.ReportDaemonStatusClient
import org.gradle.launcher.daemon.logging.DaemonMessages
import org.gradle.launcher.daemon.registry.DaemonStopEvent
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus
import org.gradle.test.fixtures.server.http.CyclicBarrierHttpServer
import org.junit.Rule

class DaemonReportStatusIntegrationSpec extends DaemonIntegrationSpec {
    @Rule CyclicBarrierHttpServer server = new CyclicBarrierHttpServer()

    def "shows default message if no daemons are running"() {
        when:
        def out = executer.withArguments("--status").run().output

        then:
        out =~ """^$DaemonMessages.NO_DAEMONS_RUNNING

$ReportDaemonStatusClient.STATUS_FOOTER.*""".toString()
    }

    def "reports idle, busy and stopped statuses of daemons"() {
        given:
        buildFile << """
task block {
    doLast {
        new URL("$server.uri").text
    }
}
"""
        daemons.getRegistry().storeStopEvent(new DaemonStopEvent(new Date(), 12346L, DaemonExpirationStatus.GRACEFUL_EXPIRE, "GRACEFUL_EXPIRE_REASON"))
        def build = executer.withTasks("block").start()
        server.waitFor()
        daemons.daemon.assertBusy()
        executer.withBuildJvmOpts('-Xmx128m')
        executer.run()

        when:
        def out = executer.withArguments("--status").run().output

        then:
        daemons.daemons.size() == 2
        out =~ /^   PID STATUS\s+INFO/
        out =~ /\n\s*\d+\s+IDLE\s+([\w\.\+\-]+)/
        out =~ /\n\s*\d+\s+BUSY\s+([\w\.\+\-]+)/
        out =~ /\n\s*12346\s+STOPPED\s+\(GRACEFUL_EXPIRE_REASON\)/

        cleanup:
        server.release()
        build.waitForFinish()
    }

    def "reports stopped status of recently stopped daemons"() {
        given:
        daemons.getRegistry().storeStopEvent(new DaemonStopEvent(new Date(), 12345L, DaemonExpirationStatus.IMMEDIATE_EXPIRE, "IMMEDIATE_EXPIRE_REASON"))
        daemons.getRegistry().storeStopEvent(new DaemonStopEvent(new Date(), 12345L, DaemonExpirationStatus.GRACEFUL_EXPIRE, "GRACEFUL_EXPIRE_REASON"))
        daemons.getRegistry().storeStopEvent(new DaemonStopEvent(new Date(), 12346L, DaemonExpirationStatus.GRACEFUL_EXPIRE, "GRACEFUL_EXPIRE_REASON"))

        when:
        def out = executer.withArguments("--status").run().output

        then:
        out.startsWith(DaemonMessages.NO_DAEMONS_RUNNING)
        out =~ /\n   PID STATUS\s+INFO/
        out =~ /\n\s*12345\s+STOPPED\s+\(IMMEDIATE_EXPIRE_REASON\)/
        out =~ /\n\s*12346\s+STOPPED\s+\(GRACEFUL_EXPIRE_REASON\)/
        out !=~ /\n\s*12345\s+STOPPED\s+\(GRACEFUL_EXPIRE_REASON\)/
    }
}

