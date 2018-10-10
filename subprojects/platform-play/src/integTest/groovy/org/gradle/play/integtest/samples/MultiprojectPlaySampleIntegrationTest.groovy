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
        assertUrlContentContains playUrl(), "Here is a multiproject app! (built by Gradle)"
        assertUrlContent playUrl("assets/javascript/timestamp.js"), publicAsset("javascript/timestamp.js")
        assertBinaryUrlContent playUrl("assets/images/gradle.ico"), publicAsset("images/gradle.ico")

        checkAdminModuleContent()
   }

    private void checkAdminModuleContent() {
        assertUrlContentContains playUrl("admin"), "Here is the ADMIN module. (built by Gradle)"
        assertUrlContent playUrl("admin/assets/javascript/admin.js"), moduleAsset("admin", "javascript/admin.js")
    }

    File moduleAsset(String module, String asset) {
        return new File(playSample.dir, "modules/${module}/public/${asset}")
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
        executer.usingInitScript(initScript).withStdinPipe().withForceInteractive(true)
        GradleHandle gradleHandle = executer.withTasks(":admin:runPlayBinary").start()
        runningPlayApp.initialize(gradleHandle)

        then:
        runningPlayApp.waitForStarted()

        and:
        checkAdminModuleContent()

        when:
        gradleHandle.cancelWithEOT().waitForFinish()

        then: "play server is stopped too"
        runningPlayApp.verifyStopped('admin')
    }
}
