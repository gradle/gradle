/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.launcher.daemon.management.internal

import spock.lang.IgnoreIf
import spock.lang.Specification

class ProcessControllerTest extends Specification {

    def controller = new ProcessController()

    def "does not treat #description as a process it may signal"() {
        expect:
        !controller.isAlive(pid)
        !controller.terminate(pid, 100)

        where:
        description                     | pid
        "the caller's own process group" | 0L
        "every process the user may signal" | -1L
        "a negative process id"         | -4321L
    }

    @IgnoreIf({ os.windows })
    def "refuses to signal a running process that is not a Gradle daemon"() {
        given: "an ordinary long-running process, standing in for a recycled process id"
        def process = new ProcessBuilder("sleep", "30").start()
        def pid = pidOf(process)

        expect:
        controller.isAlive(pid)

        when:
        def terminated = controller.terminate(pid, 1000)

        then: "it is left alone, because nothing confirmed it to be a daemon"
        !terminated
        process.isAlive()

        cleanup:
        process.destroyForcibly()
    }

    @IgnoreIf({ os.windows })
    def "reports a process that has already gone as terminated"() {
        given:
        def process = new ProcessBuilder("sleep", "30").start()
        def pid = pidOf(process)
        process.destroyForcibly()
        process.waitFor()

        expect: "there is nothing left to signal, so the caller's goal is already met"
        controller.terminate(pid, 1000)
    }

    private static long pidOf(Process process) {
        // The production code compiles against Java 8 and cannot use ProcessHandle. Tests run on 17.
        return process.pid()
    }
}
