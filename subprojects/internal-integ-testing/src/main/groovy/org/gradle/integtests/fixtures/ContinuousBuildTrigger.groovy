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

package org.gradle.integtests.fixtures
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult
import org.gradle.launcher.continuous.DefaultTriggerDetails
import org.gradle.launcher.continuous.TriggerDetails
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.CyclicBarrierHttpServer
import org.junit.rules.ExternalResource
import spock.util.concurrent.PollingConditions

class ContinuousBuildTrigger extends ExternalResource {
    private final GradleExecuter executer
    private final TestDirectoryProvider testDirectoryProvider
    private final CyclicBarrierHttpServer server
    private TestFile trigger
    private GradleHandle gradle

    ContinuousBuildTrigger(GradleExecuter executer, TestDirectoryProvider testDirectoryProvider) {
        this.executer = executer
        this.server = new CyclicBarrierHttpServer()
        this.testDirectoryProvider = testDirectoryProvider
    }

    public void before() {
        server.before()
        this.trigger = testDirectoryProvider.testDirectory.file(".gradle/trigger.out")

        trigger.parentFile.mkdirs()
        executer.withArgument("--watch")

        testDirectoryProvider.testDirectory.file("build.gradle") << """
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

    public void after() {
        server.after()
    }

    void startGradle(String task="build") {
        gradle = executer.withTasks(task).start()
    }


    void soFar(Closure c) {
        OutputScrapingExecutionResult result = new OutputScrapingExecutionResult(gradle.standardOutput, gradle.errorOutput)
        gradle.clearStandardOutput()
        gradle.clearErrorOutput()
        c.delegate = result
        c.resolveStrategy = Closure.DELEGATE_FIRST
        c.call(result)
    }

    void waitForStop() {
        gradle.waitForFinish()
    }

    def afterBuild(Closure c) {
        server.waitFor()
        c.call()
        server.release()
        server.waitForDisconnect()
        true
    }

    def waitForWatching(Closure c) {
        afterBuild(c)
        new PollingConditions().within(3) {
            assert gradle.standardOutput.endsWith("Waiting for a trigger. To exit 'continuous mode', use Ctrl+C.\n")
        }
        true
    }

    def waitForWatching() {
        waitForWatching {
            triggerNothing()
        }
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
