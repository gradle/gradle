/*
 * Copyright 2011 the original author or authors.
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

import org.junit.Rule
import spock.lang.Specification
import org.gradle.integtests.fixtures.*
import spock.lang.*

class DaemonDisappearingProcessSpec extends Specification {

    @Rule public final GradleHandles handles = new GradleHandles()

    def daemons
    def client

    def setup() {
        handles.distribution.with {
            requireOwnUserHomeDir()
            file("build.gradle") << """
                task('sleep') << {
                    println "about to sleep"
                    sleep 10000
                }
            """
        }
    }

    @Timeout(10)
    def "tearing down client while daemon is building tears down daemon"() {
        given:
        daemons = handles.daemonRegistry
        client = handles.createHandle { withArguments("--daemon", "--info").withTasks("sleep") }

        expect:
        daemons.all.empty

        when:
        client.start()

        then:
        waitFor { assert daemons.busy.size() == 1; true }

        when:
        client.abort()

        then:
        waitFor { assert daemons.all.empty; true }
    }

    def waitFor(int timeout = 5000, Closure assertion) {
        int x = 0;
        while(true) {
            try {
                def value = assertion()
                if (!value) {
                    throw new AssertionError("'$value' is not true")
                }
                return value
            } catch (Throwable t) {
                Thread.sleep(100);
                x += 100;
                if (x > timeout) {
                    throw t;
                }
            }
        }
    }

    def cleanup() {
        try {
            client?.abort()
        } catch (IllegalStateException e) {}
    }

}