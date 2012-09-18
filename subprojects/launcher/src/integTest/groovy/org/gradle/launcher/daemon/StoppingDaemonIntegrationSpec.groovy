/*
 * Copyright 2009 the original author or authors.
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
import org.gradle.tests.fixtures.ConcurrentTestUtil
import org.gradle.util.TextUtil
/**
 * by Szczepan Faber, created at: 1/20/12
 */
class StoppingDaemonIntegrationSpec extends DaemonIntegrationSpec {
    def "can handle multiple concurrent stop requests"() {
        given:
        def projectDir = distribution.testDir
        projectDir.file('build.gradle') << '''
file('marker.txt') << 'waiting'
Thread.sleep(60000)
'''

        when:
        def build = executer.start()
        ConcurrentTestUtil.poll(20) { assert projectDir.file('marker.txt').file }

        def stopExecutions = []
        5.times { idx ->
            stopExecutions << executer.withArguments("--stop").start()
        }
        stopExecutions.each { it.waitForFinish() }
        build.waitForFailure()
        def out = executer.withArguments("--stop").run().output

        then:
        out.contains(DaemonMessages.NO_DAEMONS_RUNNING)
    }

    def "reports exact number of daemons stopped and keeps console output clean"() {
        given:
        executer.allowExtraLogging = false
        executer.run()

        when:
        def out = executer.withArguments("--stop").run().output

        then:
        out == TextUtil.toPlatformLineSeparators('''Stopping daemon(s).
Gradle daemon stopped.
''')

        when:
        out = executer.withArguments("--stop").run().output

        then:
        out == TextUtil.toPlatformLineSeparators("""$DaemonMessages.NO_DAEMONS_RUNNING
""")
    }
}
