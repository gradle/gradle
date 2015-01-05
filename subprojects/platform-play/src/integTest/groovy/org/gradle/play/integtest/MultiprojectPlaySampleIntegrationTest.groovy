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
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.junit.Rule

import static org.gradle.integtests.fixtures.UrlValidator.*

class MultiprojectPlaySampleIntegrationTest extends AbstractPlaySampleIntegrationTest {
    @Rule
    Sample multiprojectSample = new Sample(temporaryFolder, "play/multiproject")

    Sample getPlaySample() {
        return multiprojectSample
    }

    @Override
    void checkContent() {
        assertUrlContentContains playUrl(), "Here is a multiproject app, built by Gradle!"
    }

    def "can run module subproject independently" () {
        when:
        executer.usingInitScript(initScript)
        sample playSample

        // Assemble first so that build time doesn't play into the startup timeout
        then:
        succeeds ":admin:assemble"

        when:
        sample playSample
        def userInput = new PipedOutputStream();
        executer.withStdIn(new PipedInputStream(userInput))
        executer.usingInitScript(initScript)
        GradleHandle gradleHandle = executer.withTasks(":admin:runPlayBinary").start()

        then:
        available("http://localhost:$httpPort/admin", "Play app", 60000)

        and:
        assertUrlContentContains playUrl("admin"), "Here is the ADMIN module"

        when:
        stopWithCtrlD(userInput, gradleHandle)

        then: "play server is stopped too"
        notAvailable("http://localhost:$httpPort/admin")
    }
}
