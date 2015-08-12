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

package org.gradle.play.integtest.fixtures
import org.apache.http.HttpStatus
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.play.integtest.fixtures.DistributionTestExecHandleBuilder.DistributionTestExecHandle
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.file.TestFile

import static org.gradle.integtests.fixtures.UrlValidator.*

class RunningPlayApp {
    private static final int UNASSIGNED = -1
    int httpPort = UNASSIGNED
    final TestFile testDirectory
    Closure output

    RunningPlayApp(TestFile testDirectory) {
        this.testDirectory = testDirectory
    }

    URL playUrl(String path='') {
        requireHttpPort()
        return new URL("http://localhost:$httpPort/${path}")
    }

    def playUrlError(String path='', int timeout=30) {
        requireHttpPort()
        HttpURLConnection connection
        ConcurrentTestUtil.poll(timeout) {
            connection = playUrl(path).openConnection()
            assert connection.responseCode >= HttpStatus.SC_BAD_REQUEST
        }

        return [ 'httpCode': connection.responseCode,
          'message': connection.responseMessage,
          'text': connection.errorStream.text ]
    }

    protected int parseHttpPort(int occurrence) {
        if (output == null) {
            throw new IllegalStateException("Attempted to parse the http port from the build output, but initialize() was not called first!")
        }

        def matcher = output.call() =~ 'play - Listening for HTTP on .*:([0-9]+)'
        if (matcher.count >= occurrence + 1) {
            httpPort = matcher[occurrence][1] as int
            return httpPort
        } else {
            return UNASSIGNED
        }
    }

    void requireHttpPort(int occurence) {
        if (httpPort == UNASSIGNED) {
            if (parseHttpPort(occurence) == UNASSIGNED) {
                throw new IllegalStateException("Could not parse Play http port from gradle output!")
            }
        }
    }

    void requireHttpPort() {
        requireHttpPort(0)
    }

    void initialize(GradleHandle gradle) {
        output = { gradle.standardOutput }
    }

    void initialize(DistributionTestExecHandle distHandle) {
        output = { distHandle.standardOutput }
    }

    void waitForStarted(int occurrence = 0) {
        int timeout = 60
        ConcurrentTestUtil.poll(timeout) {
            assert parseHttpPort(occurrence) != UNASSIGNED : "Could not parse Play http port from spec output after ${timeout} seconds"
        }
    }

    void verifyStarted(String path = '', int occurrence = 0) {
        waitForStarted(occurrence)
        assert playUrl(path).text.contains("Your new application is ready.")
    }

    void verifyStopped(String path = '') {
        notAvailable(playUrl(path).toString())
    }

    void verifyContent() {
        // Check all static assets from the shared content
        assertUrlContent playUrl("assets/stylesheets/main.css"), testDirectory.file("public/stylesheets/main.css")
        assertUrlContent playUrl("assets/javascripts/hello.js"), testDirectory.file("public/javascripts/hello.js")
        assertBinaryUrlContent playUrl("assets/images/favicon.svg"), testDirectory.file("public/images/favicon.svg")
    }
}
