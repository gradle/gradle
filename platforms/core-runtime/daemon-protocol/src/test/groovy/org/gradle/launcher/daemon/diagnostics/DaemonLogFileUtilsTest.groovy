/*
 * Copyright 2024 the original author or authors.
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


import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class DaemonLogFileUtilsTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider temp = new TestNameTestDirectoryProvider(getClass())

    def "empty input produces specific output"() {
        expect:
        DaemonLogFileUtils.tail(log(""), 10) == "<<empty>>"
    }

    def "returns last line if the limit is one"() {
        given:
        def logFile = log("""\
            line1
            line2
            line3""")

        expect:
        DaemonLogFileUtils.tail(logFile, 1) == "line3"
    }

    def "input-ending eoln is ignored"() {
        given:
        def logFile = log("""\
            line1
            line2
            line3
        """)

        expect:
        DaemonLogFileUtils.tail(logFile, 1) == "line3"
    }

    def "returns at most n lines"() {
        given:
        def logFile = log("""\
            line1
            line2
            line3
        """)

        expect:
        DaemonLogFileUtils.tail(logFile, 2) == "line2\nline3"
    }

    def "returns all output if the log is too short"() {
        given:
        def logFile = log("""\
            line1
            line2
            line3
        """)

        expect:
        DaemonLogFileUtils.tail(logFile, 10) == "line1\nline2\nline3"
    }

    private TestFile log(String logText) {
        def f = temp.file("log.txt")
        f.text = logText.stripIndent(true)
        return f
    }
}
