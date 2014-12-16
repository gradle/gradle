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

package org.gradle.play.integtest
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.util.AvailablePortFinder
import spock.lang.IgnoreIf

import static org.gradle.integtests.fixtures.UrlValidator.*

abstract class AbstractPlaySampleIntegrationTest extends AbstractIntegrationSpec {
    def portFinder = AvailablePortFinder.createPrivate()
    def initScript
    int httpPort

    abstract Sample getPlaySample();

    void checkContent() {
        assertUrlContent playUrl("assets/stylesheets/main.css"), publicAsset("stylesheets/main.css")
        assertUrlContent playUrl("assets/javascripts/hello.js"), publicAsset("javascripts/hello.js")
        assertBinaryUrlContent playUrl("assets/images/favicon.png"), publicAsset("images/favicon.png")
    }

    def setup() {
        httpPort = portFinder.nextAvailable
        initScript = file("initFile") << """
            gradle.allprojects {
                model {
                    tasks.runPlayBinary {
                        httpPort = $httpPort
                    }
                }
            }
        """
    }

    /**
     * Don't currently run with DaemonExecuter, because
     * InputForwarder is consuming stdin eagerly.
     * */
    @IgnoreIf({ GradleContextualExecuter.isDaemon() })
    def "produces usable application" () {
        when:
        executer.usingInitScript(initScript)
        sample playSample

        // Assemble first so that build time doesn't play into the startup timeout
        then:
        succeeds "assemble"

        when:
        PipedInputStream inputStream = new PipedInputStream();
        PipedOutputStream stdinWriter = new PipedOutputStream(inputStream);
        executer.withStdIn(inputStream)
        executer.usingInitScript(initScript)
        sample playSample
        GradleHandle gradleHandle = executer.withTasks(":runPlayBinary").start()

        then:
        available("http://localhost:$httpPort", "Play app", 60000)
        assert playUrl().text.contains("Your new application is ready.")

        and:
        checkContent()

        when: "stopping gradle"
        stdinWriter.write(4) // ctrl+d
        stdinWriter.flush()
        gradleHandle.waitForFinish()

        then: "play server is stopped too"
        notAvailable("http://localhost:$httpPort")
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