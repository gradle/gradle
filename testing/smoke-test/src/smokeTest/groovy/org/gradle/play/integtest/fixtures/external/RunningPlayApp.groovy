/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.play.integtest.fixtures.external

import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.file.TestFile

import static org.gradle.integtests.fixtures.UrlValidator.assertBinaryUrlContent
import static org.gradle.integtests.fixtures.UrlValidator.assertUrlContent
import static org.gradle.integtests.fixtures.UrlValidator.notAvailable

class RunningPlayApp {
    static final int UNASSIGNED = -1
    int httpPort = UNASSIGNED
    final TestFile testDirectory
    Closure output
    boolean standalone

    RunningPlayApp(TestFile testDirectory) {
        this.testDirectory = testDirectory
    }

    URL playUrl(String path='') {
        requireHttpPort()
        return new URL("http://localhost:$httpPort/${path}")
    }

    protected int parseHttpPort(int occurrence) {
        if (output == null) {
            throw new IllegalStateException("Attempted to parse the http port from the build output, but initialize() was not called first!")
        }

        if (standalone) {
            httpPort = regexParseHttpPortStandalone(output.call(), occurrence)
        } else {
            httpPort = regexParseHttpPortFromGradle(output.call(), occurrence)
        }
        return httpPort
    }


    static int regexParseHttpPortStandalone(output, int occurrence) {
        return parseHttpPort(output, /(?:play|Server) - Listening for HTTP on .*:([0-9]+)/, occurrence)
    }


    static int regexParseHttpPortFromGradle(output, int occurrence) {
        return parseHttpPort(output, /Running Play App \(:.*\) at http:\/\/.*:([0-9]+)\//, occurrence)
    }

    static int parseHttpPort(output, regex, int occurrence) {
        def matcher = output =~ regex
        if (matcher.count >= occurrence + 1) {
            return matcher[occurrence][1] as int
        }
        return UNASSIGNED
    }

    void requireHttpPort(int occurrence) {
        if (httpPort == UNASSIGNED) {
            if (parseHttpPort(occurrence) == UNASSIGNED) {
                throw new IllegalStateException("Could not parse Play http port from gradle output!")
            }
        }
    }

    void requireHttpPort() {
        requireHttpPort(0)
    }

    void initialize(GradleHandle gradle) {
        output = { gradle.standardOutput }
        standalone = false
    }

    void waitForStarted(int occurrence = 0) {
        int timeout = 120
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
