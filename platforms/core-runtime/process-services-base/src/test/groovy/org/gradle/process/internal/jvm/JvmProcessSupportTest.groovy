/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.process.internal.jvm

import org.gradle.internal.os.OperatingSystem
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Exercises the base (Java 8) copy of {@link JvmProcessSupport}.
 * For Java 9+ specific behavior, see {@code JvmProcessSupportJava9Test}.
 */
@Timeout(60)
class JvmProcessSupportTest extends Specification {

    def executor = Executors.newCachedThreadPool()

    def cleanup() {
        executor.shutdownNow()
    }

    private static Process startSleeper() {
        def cmd = OperatingSystem.current().windows
            ? ["cmd", "/c", "ping", "-n", "60", "127.0.0.1"]
            : ["sleep", "60"]
        new ProcessBuilder(cmd).start()
    }

    def "pid returns the native process id via reflection"() {
        def process = startSleeper()

        expect:
        JvmProcessSupport.pid(process) == process.pid()
        JvmProcessSupport.pid(process) > 0

        cleanup:
        process.destroyForcibly().waitFor()
    }

    def "onExit completes with the process after it exits"() {
        def process = startSleeper()
        def future = JvmProcessSupport.onExit(process, executor)

        when:
        process.destroy()

        then:
        future.get(30, TimeUnit.SECONDS) == process
        !process.alive
    }

    def "destroyDescendants is a no-op on Java 8"() {
        def process = startSleeper()

        when:
        JvmProcessSupport.destroyDescendants(process)

        then:
        process.alive

        cleanup:
        process.destroyForcibly().waitFor()
    }
}
