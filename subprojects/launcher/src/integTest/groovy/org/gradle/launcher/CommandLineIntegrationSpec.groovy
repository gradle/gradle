/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.launcher

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.jvm.JDWPUtil
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.junit.Rule
import spock.lang.IgnoreIf
import spock.lang.Unroll

class CommandLineIntegrationSpec extends AbstractIntegrationSpec {
    @Rule
    JDWPUtil jdwpClient = new JDWPUtil(5005)

    @IgnoreIf({ GradleContextualExecuter.parallel })
    @Unroll
    def "reasonable failure message when --max-workers=#value"() {
        given:
        executer.requireDaemon().requireIsolatedDaemons()  // otherwise exception gets thrown in testing infrastructure

        when:
        args("--max-workers=$value")

        then:
        fails "help"

        and:
        failure.assertHasErrorOutput "Argument value '$value' given for --max-workers option is invalid (must be a positive, non-zero, integer)"

        where:
        value << ["-1", "0", "foo", " 1"]
    }

    @Unroll
    def "reasonable failure message when org.gradle.workers.max=#value"() {
        given:
        executer.requireDaemon().requireIsolatedDaemons() // otherwise exception gets thrown in testing infrastructure

        when:
        args("-Dorg.gradle.workers.max=$value")

        then:
        fails "help"

        and:
        failure.assertHasDescription "Value '$value' given for org.gradle.workers.max Gradle property is invalid (must be a positive, non-zero, integer)"

        where:
        value << ["-1", "0", "foo", " 1"]
    }

    @IgnoreIf({ !CommandLineIntegrationSpec.debugPortIsFree() || GradleContextualExecuter.embedded })
    def "can debug with org.gradle.debug=true"() {
        when:
        def gradle = executer.withArgument("-Dorg.gradle.debug=true").withTasks("help").start()

        then:
        ConcurrentTestUtil.poll() {
            // Connect, resume threads, and disconnect from VM
            jdwpClient.connect().dispose()
        }
        gradle.waitForFinish()
    }

    static boolean debugPortIsFree() {
        boolean free = true

        ConcurrentTestUtil.poll(30) {
            Socket probe
            try {
                probe = new Socket(InetAddress.getLocalHost(), 5005)
                // something is listening, keep polling
                free = false
            } catch (Exception e) {
                // nothing listening - exit the polling loop
            } finally {
                probe?.close()
            }
        }

        free
    }
}
