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

package org.gradle.launcher.continuous
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.test.fixtures.server.http.CyclicBarrierHttpServer
import org.junit.Rule

class AbstractContinuousModeIntegrationSpec extends AbstractIntegrationSpec {
    @Rule CyclicBarrierHttpServer server = new CyclicBarrierHttpServer()
    def trigger = file(".gradle/trigger.out")
    GradleHandle gradle
    def srcFile = file("src/file")

    def setup() {
        trigger.parentFile.mkdirs()
        srcFile.parentFile.mkdirs()
        executer.withArgument("--watch")

        buildFile << """

import org.gradle.launcher.continuous.*
import org.gradle.internal.event.*

def triggerFile = file("${trigger.toURI()}")
gradle.buildFinished {
    // wait for test to sync with build
    new URL("${server.uri}").text

    def trigger = null
    try {
        triggerFile.withObjectInputStream { instream ->
            trigger = instream.readObject()
        }
    } catch (Exception e) {}

    if (trigger!=null) {
        def listenerManager = gradle.services.get(ListenerManager)
        def triggerListener = listenerManager.getBroadcaster(TriggerListener)
        triggerListener.triggered(trigger)
    }
}
"""
    }

    def validSource() {
        srcFile.text = "WORKS"
    }
    def invalidSource() {
        srcFile.text = "BROKEN"
    }
    def changeSource() {
        srcFile << "NEWLINE"
    }

    def createSource() {
        def newFile = file("src/new")
        newFile.text = "new"
    }
    def deleteSource() { srcFile.delete() }

    void startGradle(String task="tasks") {
        gradle = executer.withTasks(task).start()
    }

    void waitForStop() {
        gradle.waitForFinish()
    }

    def afterBuild(Closure c) {
        server.waitFor()
        c.call()
        server.release()
        server.waitForDisconnect()
    }

    def triggerRebuild() {
        writeTrigger(new DefaultTriggerDetails(TriggerDetails.Type.REBUILD, "test"))
    }

    def triggerStop() {
        writeTrigger(new DefaultTriggerDetails(TriggerDetails.Type.STOP, "being done"))
    }

    def triggerNothing() {
        trigger.bytes = []
    }

    private writeTrigger(DefaultTriggerDetails triggerDetails) {
        ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream(trigger))
        os.writeObject(triggerDetails)
        os.flush()
        os.close()
    }
}
