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

import org.gradle.test.fixtures.server.http.CyclicBarrierHttpServer
import org.junit.Rule

public class EnablingContinuousModeExecutionIntegrationTest extends AbstractContinuousModeIntegrationSpec {
    @Rule CyclicBarrierHttpServer server = new CyclicBarrierHttpServer()
    def trigger = file("trigger.out")

    def setup() {
        buildFile << """
import org.gradle.launcher.continuous.*
import org.gradle.internal.event.*

def triggerFile = file("${trigger.toURI()}")
gradle.buildFinished {
    def inputStream = new ObjectInputStream(new FileInputStream(triggerFile))
    def trigger = inputStream.readObject()
    inputStream.close()

    def listenerManager = gradle.services.get(ListenerManager)
    def triggerListener = listenerManager.getBroadcaster(TriggerListener)
    triggerListener.triggered(trigger)
    new URL("${server.uri}").text // wait for test
}
"""
    }

    def planToRebuild() {
        writeTrigger(new DefaultTriggerDetails(TriggerDetails.Type.REBUILD, "test"))
    }

    def planToStop() {
        writeTrigger(new DefaultTriggerDetails(TriggerDetails.Type.STOP, "being done"))
    }

    private writeTrigger(DefaultTriggerDetails triggerDetails) {
        ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream(trigger))
        os.writeObject(triggerDetails)
        os.close()
    }

    def "can enable continuous mode"() {
        when:
        planToStop()
        and:
        def gradle = executer.withTasks("tasks").start()
        then:
        server.sync()
        gradle.waitForFinish()
    }

    def "warns about incubating feature"() {
        when:
        planToStop()
        and:
        def gradle = executer.withTasks("tasks").start()
        then:
        server.sync()
        gradle.waitForFinish()
        gradle.standardOutput.contains("Continuous mode is an incubating feature.")
    }

    def "prints useful messages when in continuous mode"() {
        when:
        planToRebuild()
        and:
        def gradle = executer.withTasks("tasks").start()
        then:
        server.sync()
        when:
        planToStop()
        then:
        server.sync()
        gradle.waitForFinish()
        gradle.standardOutput.contains("Waiting for a trigger. To exit 'continuous mode', use Ctrl+C.")
        gradle.standardOutput.contains("REBUILD triggered due to test")
        gradle.standardOutput.contains("STOP triggered due to being done")
    }
}
