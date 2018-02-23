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
import org.gradle.play.integtest.fixtures.RunningPlayApp
import org.gradle.test.fixtures.ConcurrentTestUtil
import spock.lang.IgnoreIf

import static org.gradle.integtests.fixtures.UrlValidator.*

abstract class AbstractPlaySampleIntegrationTest extends AbstractIntegrationSpec {
    File initScript
    RunningPlayApp runningPlayApp = new RunningPlayApp(testDirectory)

    abstract Sample getPlaySample();

    void checkContent() {
        assertUrlContentContains playUrl(), "Your new application is ready."
        assertUrlContent playUrl("assets/stylesheets/main.css"), publicAsset("stylesheets/main.css")
        assertUrlContent playUrl("assets/javascripts/hello.js"), publicAsset("javascripts/hello.js")
        assertBinaryUrlContent playUrl("assets/images/favicon.png"), publicAsset("images/favicon.png")
    }

    def setup() {
        initScript = file("initFile") << """
            gradle.allprojects {
                tasks.withType(PlayRun) {
                    httpPort = 0
                }
            }
        """
    }

    @IgnoreIf({ !AbstractPlaySampleIntegrationTest.portForWithBrowserTestIsFree() })
    def "produces usable application" () {
        when:
        executer.usingInitScript(initScript)
        sample playSample

        // Assemble first so that build time doesn't play into the startup timeout
        then:
        succeeds "assemble"

        when:
        sample playSample
        executer.usingInitScript(initScript).withStdinPipe().withForceInteractive(true)
        GradleHandle gradleHandle = executer.withTasks(":runPlayBinary").start()
        runningPlayApp.initialize(gradleHandle)

        then:
        runningPlayApp.waitForStarted()

        and:
        checkContent()

        when:
        gradleHandle.cancelWithEOT().waitForFinish()

        then: "play server is stopped too"
        runningPlayApp.verifyStopped()
    }

    URL playUrl(String path='') {
        runningPlayApp.playUrl(path)
    }

    File publicAsset(String asset) {
        return new File(playSample.dir, "public/${asset}")
    }

    File appAsset(String asset) {
        return new File(playSample.dir, "app/assets/${asset}")
    }

    static boolean portForWithBrowserTestIsFree() {
        boolean free = true

        ConcurrentTestUtil.poll(30) {
            Socket probe
            try {
                probe = new Socket(InetAddress.getLocalHost(), 19001)
                // something is listening, keep polling
                free = false
            } catch (Exception e) {
                // nothing listening - exit the polling loop
            } finally {
                probe?.close()
            }
        }
        free
    }
}
