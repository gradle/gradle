/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.launcher.continuous

import org.gradle.integtests.fixtures.AbstractContinuousIntegrationTest
import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer
import org.gradle.integtests.fixtures.daemon.DaemonsFixture
import org.gradle.launcher.daemon.logging.DaemonMessages
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.TextUtil

class ContinuousBuildCancellationIntegrationTest extends AbstractContinuousIntegrationTest {

    TestFile setupJavaProject() {
        buildFile.text = "apply plugin: 'java'"
        testDirectory.createDir('src/main/java')
    }

    def "can cancel continuous build by ctrl+d"() {
        given:
        setupJavaProject()

        when:
        succeeds("build")

        then:
        noExceptionThrown()

        when:
        if (inputBefore) {
            stdinPipe << inputBefore
        }
        if (flushBefore) {
            stdinPipe.flush()
        }
        sendEOT()

        then:
        ConcurrentTestUtil.poll(buildTimeout, 0.5) {
            assert !gradle.isRunning()
        }

        where:
        [inputBefore, flushBefore] << [['', ' ', 'a', 'some input', 'a' * 8192], [true, false]].combinations()
    }

    def "does not cancel continuous build when other than ctrl+d is entered"() {
        given:
        setupJavaProject()

        when:
        succeeds("build")

        then:
        noExceptionThrown()

        when:
        stdinPipe << "some input"
        stdinPipe << TextUtil.platformLineSeparator
        stdinPipe.flush()

        then:
        sleep(1000L)
        assert gradle.isRunning()
    }

    def "can cancel continuous build by ctrl+d after multiple builds"() {
        given:
        def testfile = setupJavaProject().file('Thing.java')
        testfile.text = 'public class Thing {}'

        when:
        succeeds("build")

        then:
        noExceptionThrown()

        when:
        for (int i = 0; i < 3; i++) {
            testfile << '// changed'
            buildTriggeredAndSucceeded()
        }
        and:
        sendEOT()

        then:
        ConcurrentTestUtil.poll(buildTimeout, 0.5) {
            assert !gradle.isRunning()
        }
    }

    def "does not log daemon cancel message for continuous build"() {
        setup:
        executer.requireDaemon()
        executer.requireIsolatedDaemons()
        setupJavaProject()

        when:
        succeeds("build")

        and:
        sendEOT()

        then:
        ConcurrentTestUtil.poll(buildTimeout, 0.5) {
            assert !gradle.isRunning()
        }

        and:
        !output.contains(DaemonMessages.CANCELED_BUILD)
        !daemons.daemon.log.contains(DaemonMessages.CANCELED_BUILD)

        cleanup:
        daemons.killAll()
    }

    DaemonsFixture getDaemons() {
        new DaemonLogsAnalyzer(executer.daemonBaseDir)
    }
}
