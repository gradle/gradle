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

import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.TestParticipant
import org.junit.Rule
import spock.lang.Ignore

import java.util.concurrent.TimeUnit

@Ignore('contains javascript repository')
class PlayReloadWaitingIntegrationTest extends AbstractPlayReloadIntegrationTest {
    @Rule
    public ConcurrentTestUtil concurrent = new ConcurrentTestUtil()

    def setup() {
        withoutContinuousBuild()
        server.start()
        addPendingChangesHook()
    }

    def "waits for app request before building changes"() {
        given:
        def hooksFile = file('hooks.gradle') << """
            gradle.projectsLoaded {
                ${server.callFromBuild("buildStarted")}
            }
        """
        executer.withArguments("-I", hooksFile.absolutePath)

        and:
        2.times {
            server.expect("buildStarted")
        }
        appRunning()

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
        }
        // Request should be complete soon after build completes
        routeChecker.completesWithin(1, TimeUnit.SECONDS)
    }

    def "wait for changes to be built when a change occurs during a build"() {
        given:
        appRunning()

        when:
        // Add a new route and wait for it to be detected
        def initialChangeDelivered = changesReported()
        addNewRoute("first")
        initialChangeDelivered.waitForAllPendingCalls()

        // Once initial change is delivered, build will start when a request is received.
        // Open an HTTP connection to the App: expect 'second' route to be incorporated
        def secondRouteChecker = concurrent.start({
            checkRoute('second')
        })
        def changeDeliveredDuringBuild = blockBuildWaitingForChanges()
        initialChangeDelivered.releaseAll()

        // During the build, add a new route, ensuring that the build blocks waiting for it to be delivered
        changeDeliveredDuringBuild.waitForAllPendingCalls()
        addNewRoute("second")
        changeDeliveredDuringBuild.releaseAll()

        then:
        secondRouteChecker.completesWithin(5, TimeUnit.SECONDS)
    }

    def "wait for changes to be built when a fix occurs during a failing build"() {
        given:
        appRunning()

        when:
        // Add a new route and wait for it to be detected
        def initialChangeDelivered = changesReported()
        addBadCode()
        initialChangeDelivered.waitForAllPendingCalls()

        // Once initial change is delivered, build will start when a request is received.
        // Open an HTTP connection to the App: expect 'second' route to be incorporated
        def routeChecker = concurrent.start({
            checkRoute('hello')
        })
        def changeDeliveredDuringBuild = blockBuildWaitingForChanges()
        initialChangeDelivered.releaseAll()

        // During the build, add a new route, ensuring that the build blocks waiting for it to be delivered
        changeDeliveredDuringBuild.waitForAllPendingCalls()
        fixBadCode()
        changeDeliveredDuringBuild.releaseAll()

        then:
        routeChecker.completesWithin(5, TimeUnit.SECONDS)
    }

    private void appRunning() {
        succeeds("runPlayBinary")
        appIsRunningAndDeployed()
    }

    void checkRoute(String route) {
        runningApp.initialize(gradle)
        assert runningApp.playUrl(route).text == route + ' world'
    }
}
