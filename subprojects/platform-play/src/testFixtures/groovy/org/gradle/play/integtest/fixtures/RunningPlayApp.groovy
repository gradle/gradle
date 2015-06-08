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

import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.AvailablePortFinder

import static org.gradle.integtests.fixtures.UrlValidator.*

class RunningPlayApp {
    int httpPort
    def portFinder = AvailablePortFinder.createPrivate()
    TestFile testDirectory

    RunningPlayApp(TestFile testDirectory) {
        this.testDirectory = testDirectory
    }

    URL playUrl(String path='') {
        return new URL("http://localhost:$httpPort/${path}")
    }

    int selectPort() {
        httpPort = portFinder.nextAvailable
        return httpPort
    }

    void verifyStarted() {
        def url = playUrl().toString()
        available(url, "Play app", 60)
        assert playUrl().text.contains("Your new application is ready.")
    }

    void verifyStopped() {
        notAvailable(playUrl().toString())
    }

    void verifyContent() {
        // Check all static assets from the shared content
        assertUrlContent playUrl("assets/stylesheets/main.css"), testDirectory.file("public/stylesheets/main.css")
        assertUrlContent playUrl("assets/javascripts/hello.js"), testDirectory.file("public/javascripts/hello.js")
        assertBinaryUrlContent playUrl("assets/images/favicon.svg"), testDirectory.file("public/images/favicon.svg")
    }
}
