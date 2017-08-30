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

import org.gradle.internal.filewatch.PendingChangesManager
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.TestParticipant
import org.gradle.test.fixtures.file.TestFile
import org.junit.Rule

import java.util.concurrent.TimeUnit

class PlayReloadWaitingIntegrationTest extends AbstractPlayReloadIntegrationTest {
    @Rule
    public ConcurrentTestUtil concurrent = new ConcurrentTestUtil()

    TestFile hooksFile

    def setup() {
        withoutContinuousBuild()

        server.start()
        hooksFile = file('hooks.gradle') << """
            gradle.projectsLoaded {
                ${server.callFromBuild("buildStarted")}
            }
            gradle.buildFinished {
                ${server.callFromBuild("buildFinished")}
            }
        """
        buildFile << """
            tasks.withType(PlayRun) {
                doLast {
                    ${server.callFromBuild("appStarted")}
                }
            }
        """
    }

    def "waits for app request before building changes"() {
        given:
        executer.withArguments("-I", hooksFile.absolutePath)
        waitForStartup()

        when:
        def block = server.expectAndBlock( "buildStarted")
        addNewRoute("hello")

        then:
        def op = concurrent.waitsForAsyncCallback().withWaitTime(2000)
        TestParticipant routeChecker
        op.start {
            op.callbackLater {
                // Starting the HTTP request releases the build
                routeChecker = concurrent.start({
                    checkRoute('hello')
                })
            }
            block.waitForAllPendingCalls()
            block.releaseAll()
            server.expect("appStarted")
            server.expect("buildFinished")
        }
        // Request should be complete soon after build completes
        routeChecker.completesWithin(1, TimeUnit.SECONDS)
    }

    def "wait for changes to be built when a request comes in during initial app startup"() {
        given:
        // prebuild so the build doesn't timeout waiting for rebuild signal
        executer.withTasks("playBinary").run()

        when:
        executer.withArguments("-I", hooksFile.absolutePath)
        server.expect("buildStarted")
        def onRun = server.expectAndBlock("appStarted")

        start("runPlayBinary")

        onRun.waitForAllPendingCalls()
        def routeChecker = concurrent.start({
            checkRoute('hello')
        })

        // Add a new route and expect a rebuild
        addNewRoute('hello')

        // Allow the first build to complete
        onRun.releaseAll()
        server.expect("buildFinished")
        expectBuild()

        then:
        routeChecker.completesWithin(5, TimeUnit.SECONDS)
    }

    def "wait for changes to be built when a change occurs during a build"() {
        given:
        buildFile << """
            def pendingChangesManager = gradle.services.get(${PendingChangesManager.canonicalName})
            pendingChangesManager.addListener {
                Thread.start {
                    ${server.callFromBuild("pendingChange")}
                }
            }
"""
        executer.withArguments("-I", hooksFile.absolutePath)
        waitForStartup()

        when:
        // Add a new route and wait for it to be detected
        server.expect("pendingChange")
        def changeDelivered = server.expectAndBlock("pendingChange")
        addNewRoute("ignored")
        changeDelivered.waitForAllPendingCalls()

        // Open an HTTP connection to the App: will block until all changes built and incorporated
        def routeChecker = concurrent.start({
            checkRoute('important')
        })
        changeDelivered.releaseAll()

        // After the app starts (but before the build finishes) add another route
        server.expect( "buildStarted")
        def afterAppStarted = server.expectAndBlock("appStarted")
        afterAppStarted.waitForAllPendingCalls()
        addNewRoute("important")

        server.expect("pendingChange")
        server.expect("pendingChange")
        server.expect("pendingChange")
        // TODO:DAZ Find a better way to ensure this change is 'delivered'
        sleep 10000

        afterAppStarted.releaseAll()

        server.expect("buildFinished")

        expectBuild()

        then:
        routeChecker.completesWithin(5, TimeUnit.SECONDS)
    }

    private void waitForStartup() {
        2.times {
            expectBuild()
        }
        succeeds("runPlayBinary")
        appIsRunningAndDeployed()
    }

    private void expectBuild() {
        server.expect("buildStarted")
        server.expect("appStarted")
        server.expect("buildFinished")
    }

    void checkRoute(String route) {
        runningApp.initialize(gradle)
        assert runningApp.playUrl(route).text == route + ' world'
    }
}
