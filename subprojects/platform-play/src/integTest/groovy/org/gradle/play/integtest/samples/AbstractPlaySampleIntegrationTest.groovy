/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.integtest.samples
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.util.AvailablePortFinder
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.util.TextUtil

import static org.gradle.integtests.fixtures.UrlValidator.*

@Requires(TestPrecondition.JDK7_OR_LATER)
abstract class AbstractPlaySampleIntegrationTest extends AbstractIntegrationSpec {
    def portFinder = AvailablePortFinder.createPrivate()
    def initScript
    int httpPort

    abstract Sample getPlaySample();

    void checkContent() {
        assertUrlContentContains playUrl(), "Your new application is ready."
        assertUrlContent playUrl("assets/stylesheets/main.css"), publicAsset("stylesheets/main.css")
        assertUrlContent playUrl("assets/javascripts/hello.js"), publicAsset("javascripts/hello.js")
        assertBinaryUrlContent playUrl("assets/images/favicon.png"), publicAsset("images/favicon.png")
    }

    def setup() {
        httpPort = portFinder.nextAvailable
        initScript = file("initFile") << """
            gradle.allprojects {
                tasks.withType(PlayRun) {
                    httpPort = $httpPort
                }
            }
        """
    }

    def "produces usable application" () {
        when:
        executer.usingInitScript(initScript)
        sample playSample

        // Assemble first so that build time doesn't play into the startup timeout
        then:
        succeeds "assemble"

        when:
        sample playSample
        def userInput = new PipedOutputStream();
        executer.withStdIn(new PipedInputStream(userInput))
        executer.usingInitScript(initScript)
        GradleHandle gradleHandle = executer.withTasks(":runPlayBinary").start()

        then:
        available("http://localhost:$httpPort", "Play app", 60)

        and:
        checkContent()

        when:
        stopWithCtrlD(userInput, gradleHandle)

        then: "play server is stopped too"
        notAvailable("http://localhost:$httpPort")
    }

    static stopWithCtrlD(PipedOutputStream userInput, GradleHandle gradleHandle) {
        userInput.write(4) // ctrl+d
        userInput.write(TextUtil.toPlatformLineSeparators("\n").bytes) // For some reason flush() doesn't get the keystroke to the DaemonExecuter
        gradleHandle.waitForFinish()
    }

    URL playUrl(String path='') {
        return new URL("http://localhost:$httpPort/${path}")
    }

    File publicAsset(String asset) {
        return new File(playSample.dir, "public/${asset}")
    }

    File appAsset(String asset) {
        return new File(playSample.dir, "app/assets/${asset}")
    }
}
