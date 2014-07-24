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

import org.gradle.launcher.daemon.logging.DaemonMessages
import org.gradle.test.fixtures.ConcurrentTestUtil

import java.util.regex.Pattern

class CancellingDaemonIntegrationSpec extends DaemonIntegrationSpec {
    def "can handle multiple concurrent cancel requests"() {
        given:
        file('build.gradle') << '''
file('marker.txt') << 'waiting'
Thread.sleep(60000)
'''

        when:
        def build = executer.start()
        ConcurrentTestUtil.poll(20) { assert file('marker.txt').file }
        def buildCmdPattern = Pattern.compile('Dispatching Build\\{id=([0-9a-z\\-]+\\.1),')
        def buildCmdMatcher = buildCmdPattern.matcher(build.standardOutput)
        assert buildCmdMatcher.find()
        def buildId = buildCmdMatcher.group(1)

        def stopExecutions = []
        3.times { idx ->
            stopExecutions << executer.withArguments('--cancel=' + buildId).start()
        }
        stopExecutions.each { it.waitForFinish(); println 'finished' }
        build.waitForFailure()
        def out = executer.withArguments('--cancel=' + buildId).run().output

        then:
        out.contains(DaemonMessages.NO_DAEMONS_RUNNING)
    }

    def "cancels correct build"() {
        def marker2 = file("marker2.txt")
        given:
        file('build.gradle') << """
task t1 << {
    file('start1.txt') << 'waiting'
    def marker = file('marker1.txt')
    long timeout = System.currentTimeMillis() + 30000
    while (!marker.file && System.currentTimeMillis() < timeout) { Thread.sleep(200) }
    if (!marker.file) { throw new RuntimeException('Timeout waiting for marker file') }
    Thread.sleep(1000)
    println '_t1_finished_'
}
task t2 << {
    file('start2.txt') << 'waiting'
    def marker = file('marker2.txt')
    long timeout = System.currentTimeMillis() + 10000
    while (!marker.file && System.currentTimeMillis() < timeout) { Thread.sleep(200) }
    if (!marker.file) { throw new RuntimeException('Timeout waiting for marker file') }
    Thread.sleep(1000)
    println '_t2_finished_'
}
"""

        when:
        def build = executer.withArguments('-d', 't1').start()
        ConcurrentTestUtil.poll(20) { assert file('start1.txt').file }
        def build2 = executer.withArguments('-i', 't2').start()
        ConcurrentTestUtil.poll(20) { assert file('start2.txt').file }

        def buildCmdPattern = Pattern.compile('Dispatching Build\\{id=([0-9a-z\\-]+\\.1),')
        def buildCmdMatcher = buildCmdPattern.matcher(build.standardOutput)
        assert buildCmdMatcher.find()
        def buildId = buildCmdMatcher.group(1)

        def stopExecution = executer.withArguments('-d', '--cancel=' + buildId).start()
        file('marker2.txt') << 'go'
        stopExecution.waitForFinish()
        build.waitForFailure()
        build2.waitForFinish()

        then:
        !build.standardOutput.contains('_t1_finished_')
        build.standardOutput.contains('Cancel request not processed: will force stop.')
        !build2.standardOutput.contains('Cancel request not processed: will force stop.')
        // build2 can end before cancel is send
        // build2.standardOutput.contains('Cancel request does not match current build')
        build2.standardOutput.contains('_t2_finished_')
    }
}
