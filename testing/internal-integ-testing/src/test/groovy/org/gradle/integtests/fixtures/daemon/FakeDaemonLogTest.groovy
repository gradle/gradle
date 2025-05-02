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

package org.gradle.integtests.fixtures.daemon

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.GradleVersion
import org.junit.Rule
import spock.lang.Specification

import java.nio.charset.Charset

class FakeDaemonLogTest extends Specification {
    @Rule
    public TestNameTestDirectoryProvider testNameTestDirectoryProvider = new TestNameTestDirectoryProvider(getClass())

    def "can read fake log for appropriate version"() {
        given:
        def fakeLog = new FakeDaemonLog(testNameTestDirectoryProvider.getTestDirectory(), gradleVersion)
        fakeLog.logException("java.net.SocketException: Socket operation on nonsocket: no further information")
        def daemonLog = new DaemonLogFile(fakeLog.logFile, Charset.defaultCharset())
        expect:
        DaemonContextParser.parseFromFile(daemonLog, GradleVersion.version(gradleVersion))
        where:
        gradleVersion << ["8.7", "8.8", GradleVersion.current().version]
    }
}
