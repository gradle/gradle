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
import spock.lang.Requires
import spock.lang.Specification
import spock.lang.Timeout

/**
 * Exercises the Java 9+ copy of {@link JvmProcessSupport}.
 */
@Timeout(60)
class JvmProcessSupportJava9Test extends Specification {

    private static Process startSleeper() {
        def cmd = OperatingSystem.current().windows
            ? ["cmd", "/c", "ping", "-n", "60", "127.0.0.1"]
            : ["sleep", "60"]
        new ProcessBuilder(cmd).start()
    }

    def "pid returns the native process id"() {
        def process = startSleeper()

        expect:
        JvmProcessSupport.pid(process) == process.pid()
        JvmProcessSupport.pid(process) > 0

        cleanup:
        process.destroyForcibly().waitFor()
    }

    @Requires({ !OperatingSystem.current().windows })
    def "destroyDescendants destroys child processes"() {
        // The shell stays alive via `wait` while its `sleep` child runs.
        def process = new ProcessBuilder(["sh", "-c", "sleep 60 & wait"]).start()
        waitFor { process.descendants().count() >= 1 }

        when:
        JvmProcessSupport.destroyDescendants(process)

        then:
        waitFor { process.descendants().count() == 0 }

        cleanup:
        process.destroyForcibly().waitFor()
    }

    private static void waitFor(Closure<Boolean> condition) {
        def deadline = System.currentTimeMillis() + 30_000
        while (!condition.call()) {
            assert System.currentTimeMillis() < deadline
            Thread.sleep(20)
        }
    }
}
