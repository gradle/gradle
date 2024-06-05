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

package org.gradle.launcher.daemon.diagnostics

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class DaemonDiagnosticsTest extends Specification {

    @Rule TestNameTestDirectoryProvider temp = new TestNameTestDirectoryProvider(getClass())

    def "tailing the daemon log is always safe"() {
        given:
        def diagnostics = new DaemonDiagnostics(new File("does not exist"), 123)

        when:
        def description = diagnostics.describe()

        then:
        noExceptionThrown()
        description.contains("Unable to read from the daemon log file")
    }

    def "can describe itself"() {
        given:
        def log = temp.file("foo.log")
        log << "hey joe!"
        def diagnostics = new DaemonDiagnostics(log, 123)

        when:
        String desc = diagnostics.describe()

        then:
        desc.contains "123"
        desc.contains log.name
        desc.contains "hey joe!"
    }
}
