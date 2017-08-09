/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.play.integtest.continuous

import org.gradle.deployment.internal.DefaultDeployment
import org.gradle.internal.filewatch.PendingChangesListener
import org.gradle.internal.filewatch.PendingChangesManager
import org.gradle.internal.filewatch.SingleFirePendingChangesListener
import spock.lang.Unroll

class PlayReloadWaitingIntegrationTest extends PlayReloadIntegrationTest {

    def setup() {
        server.start()
        addPendingChangesHook()
        buildFile << """
            gradle.taskGraph.afterTask { task ->
                if (task.path != ":compilePlayBinaryScala") {
                    return
                }
                def markerFile = file('wait-for-changes')
                if (markerFile.exists()) {
                    println "WAITING FOR CHANGES"
                    def pendingChanges = new java.util.concurrent.atomic.AtomicBoolean(false)
                    def pendingChangesManager = gradle.services.get(${PendingChangesManager.canonicalName})
                    def pendingChangesListener = new ${SingleFirePendingChangesListener.canonicalName}({
                        synchronized(pendingChanges) {
                            println "Pending changes detected"
                            pendingChanges.set(true)
                            pendingChanges.notifyAll()
                        }
                    } as ${PendingChangesListener.canonicalName})

                    pendingChangesManager.addListener pendingChangesListener
                    
                    // Signal we are listening for changes
                    ${server.callFromBuild("rebuild")}

                    synchronized(pendingChanges) {
                        while(!pendingChanges.get()) {
                            pendingChanges.wait()
                        }
                    }
                    
                    markerFile.delete()
                }
            }
        """
    }

    def "wait for changes to be built when a request comes in during a build"() {
        file('hooks.gradle') << """
            gradle.projectsLoaded {
                ${server.callFromBuild("buildStarted")}
            }
            gradle.buildFinished {
                ${server.callFromBuild("buildFinished")}
            }
        """
        executer.withArguments("-I", file("hooks.gradle").absolutePath)

        when:
        server.expect("buildStarted")
        server.expect("buildFinished")
        succeeds("runPlayBinary")
        then:
        appIsRunningAndDeployed()

        when:
        def block = server.expectAndBlock( "buildStarted")
        addNewRoute("hello")
        block.waitForAllPendingCalls()
        block.releaseAll()

        then:
        server.expect("buildFinished")
        checkRoute 'hello'
    }

    def "wait for pending changes to be built if a request comes in during a build and there are pending changes"() {
        when:
        succeeds("runPlayBinary")
        then:
        appIsRunningAndDeployed()

        when:
        def block = blockBuildWaitingForChanges()

        // Make a change that triggers the build
        addNewRoute("hello")

        // Wait for the build to be blocked waiting for next change
        block.waitForAllPendingCalls()

        // Trigger another change
        addNewRoute("goodbye")
        block.releaseAll()

        then:
        // goodbye route is added by second change, so if it's available, we know we've blocked
        checkRoute 'goodbye'
        checkRoute 'hello'
     }

    def "wait for pending changes to be built if a request comes in during a failing build and there are pending changes"() {
        when:
        succeeds("runPlayBinary")
        then:
        appIsRunningAndDeployed()

        when:
        def block = blockBuildWaitingForChanges()

        // Trigger a change that breaks the build
        addBadCode()

        // Wait for the build to be blocked waiting for next change
        block.waitForAllPendingCalls()

        // Trigger another change during the build that works
        fixBadCode()
        block.releaseAll()

        then:
        checkRoute 'hello'
    }

    @Unroll
    def "wait for changes to be built when a request comes in during initial app startup and there are pending changes and build is gated=#gated"() {
        given:
        executer.withArgument("-D" + DefaultDeployment.GATED_BUILD_SYSPROP + "=" + gated)
        // prebuild so the build doesn't timeout waiting for rebuild signal
        executer.withTasks("playBinary").run()
        when:
        def rebuild = blockBuildWaitingForChanges()

        // Start up the Play app, block waiting for changes before completion
        start("runPlayBinary")
        rebuild.waitForAllPendingCalls()

        // Trigger a change
        addNewRoute('hello')
        rebuild.releaseAll()

        then:
        appIsRunningAndDeployed()
        checkRoute 'hello'

        where:
        gated << [true, false ]
    }

    def blockBuildWaitingForChanges() {
        file('wait-for-changes').touch()
        return server.expectAndBlock("rebuild")
    }

    void checkRoute(String route) {
        assert runningApp.playUrl(route).text == route + ' world'
    }
}
