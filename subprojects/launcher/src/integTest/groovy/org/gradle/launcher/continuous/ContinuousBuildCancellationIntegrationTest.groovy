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

import org.gradle.internal.SystemProperties
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.util.TextUtil
import spock.util.concurrent.PollingConditions

class ContinuousBuildCancellationIntegrationTest extends Java7RequiringContinuousIntegrationTest {

    def setup() {
        buildFile.text = "apply plugin: 'java'"
        testDirectory.createDir('src/main/java')
    }

    def "should cancel build when System.in contains EOT"() {
        given:
        succeeds("build")

        when:
        stdinPipe.write(4) // EOT / CTRL-D

        // TODO: this is not right, we are sending a line ending to workaround the input buffering by the daemon
        // Possibly, the daemon should be EOT aware and close the stream.
        // Or, when the client is doing a blocking read of the input we shouldn't buffer.
        stdinPipe.write(SystemProperties.instance.lineSeparator.bytes)

        then:
        expectOutput {
            it.contains("Build cancelled")
        }
    }

    def "should cancel continuous build by EOT, also when EOT isn't the first character"() {
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
        stdinPipe.write(4)

        then:
        new PollingConditions(initialDelay: 0.5).within(buildTimeout) {
            assert !gradle.isRunning()
        }

        where:
        [inputBefore, flushBefore] << [['', ' ', 'a', 'some input', 'a' * 8192], [true, false]].combinations()
    }


    def "should cancel build when System.in is closed"() {
        given:
        succeeds("build")

        when:
        closeStdIn()

        then:
        expectOutput { it.contains("Build cancelled") }
    }

    def "should cancel build when System.in contains some other characters, then closes"() {
        when:
        succeeds("build")
        stdinPipe << 'abc'

        then:
        expectOutput { !it.contains("Build cancelled") }

        when:
        stdinPipe.close()

        then:
        expectOutput { it.contains("Build cancelled") }
    }

    @Requires(TestPrecondition.NOT_WINDOWS)
    def "does not cancel on EOT or by closing System.in when not interactive"() {
        when:
        executer.beforeExecute { it.withForceInteractive(false) }
        waitingMessage = "Waiting for changes to input files of tasks...\n"
        killToStop = true
        stdinPipe.close()

        then:
        succeeds "build" // tests message

        when:
        file("src/main/java/Thing.java") << "class Thing {}"

        then:
        succeeds()
    }

    def "does not cancel continuous build when other than EOT is entered"() {
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

    def "can cancel continuous build by EOT after multiple builds"() {
        given:
        def testfile = file('src/main/java/Thing.java')
        testfile.text = 'public class Thing {}'

        when:
        succeeds("build")

        then:
        noExceptionThrown()

        when:
        for (int i = 0; i < 3; i++) {
            testfile << '// changed'
            succeeds()
        }
        and:
        stdinPipe.write(4)

        then:
        new PollingConditions(initialDelay: 0.5).within(buildTimeout) {
            assert !gradle.isRunning()
        }
    }
}
